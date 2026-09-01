package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.yanfan.arena.platform.match.processing.MatchProcessorTestData.threeVsThreeEvent;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.*;
import static com.yanfan.arena.platform.test.KafkaTestSupport.awaitDltRecord;
import static com.yanfan.arena.platform.test.KafkaTestSupport.committedOffset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Verify that an exhausted retryable failure goes to the DLT,
// then the listener stops, and leaves the source offset uncommitted.
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true"
})
@Testcontainers
class MatchRetryExhaustionIT {

    @Container
    static final MySQLContainer MYSQL = mysqlContainer();

    @Container
    static final KafkaContainer KAFKA = kafkaContainer();

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registerMySqlProperties(registry, MYSQL);
        registerKafkaProperties(registry, KAFKA);
    }

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    MatchProcessor matchProcessor;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Value("${spring.kafka.consumer.group-id}")
    String groupId;


    @Test
    void exhaustedRetryableFailureGoesToDltStopsListenerAndDoesNotCommitOffset() throws Exception {

        // Make every listener attempt fail so Kafka exhausts its retries
        when(matchProcessor.process(any(ArenaMatchCompleted.class)))
                .thenThrow(new IllegalStateException("Forced retryable failure"));

        ArenaMatchCompleted failingEvent = threeVsThreeEvent(
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                1L);

        String json = objectMapper.writeValueAsString(failingEvent);

        SendResult<String, String> sendResult = kafkaTemplate.send(
                        "arena-match-completed",
                        failingEvent.matchId().toString(),
                        json)
                .get(10, TimeUnit.SECONDS);

        // Record where the failed record is located
        String topic = sendResult.getRecordMetadata().topic();
        int partition = sendResult.getRecordMetadata().partition();
        long failedOffset = sendResult.getRecordMetadata().offset();

        // After all 4 attempts fail, the record reaches the DLT
        ConsumerRecord<String, byte[]> dltRecord = awaitDltRecord(
                KAFKA,
                "retry-exhaustion-dlt-check",
                failingEvent.matchId().toString(),
                Duration.ofSeconds(20));

        // The headers identify why the record failed
        byte[] category = dltRecord.headers().lastHeader("failure-category").value();

        assertThat(new String(category, StandardCharsets.UTF_8))
                .isEqualTo("RETRYABLE");

        byte[] attempt = dltRecord.headers().lastHeader("attempt").value();

        assertThat(new String(attempt, StandardCharsets.UTF_8))
                .isEqualTo("4");

        // The listener should stop
        awaitListenerStopped();

        // Kafka stores the next offset to consume, so a value past the failed
        // record's offset would mean the failed record was acknowledged
        Long committed = committedOffset(KAFKA, groupId, topic, partition);
        if (committed != null) {
            assertThat(committed).isLessThanOrEqualTo(failedOffset);
        }
    }

    // Poll until every Kafka listener container reports it has stopped
    private void awaitListenerStopped() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;

        while (System.currentTimeMillis() < deadline) {
            boolean allStopped = registry.getListenerContainers()
                    .stream()
                    .noneMatch(MessageListenerContainer::isRunning);

            if (allStopped) {
                return;
            }

            Thread.sleep(200);
        }

        throw new AssertionError("Listener did not stop after exhaustion");
    }

}
