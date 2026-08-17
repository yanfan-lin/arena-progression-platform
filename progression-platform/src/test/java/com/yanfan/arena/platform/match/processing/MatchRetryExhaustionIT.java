package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.yanfan.arena.platform.test.IntegrationTestContainers.kafkaContainer;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.mysqlContainer;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.registerKafkaProperties;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.registerMySqlProperties;
import static com.yanfan.arena.platform.test.KafkaTestSupport.awaitDltRecord;
import static com.yanfan.arena.platform.test.KafkaTestSupport.committedOffset;
import static com.yanfan.arena.platform.match.processing.MatchProcessorTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

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
    JdbcTemplate jdbcTemplate;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MatchProcessor matchProcessor;

    @Autowired
    KafkaListenerEndpointRegistry registry;

    @Value("${spring.kafka.consumer.group-id}")
    String groupId;


    @Test
    void exhaustedRetryableFailureGoesToDltStopsListenerAndDoesNotCommitOffset() throws Exception {
        // Load players and teams for the match
        insertThreeVsThreePlayersAndTeams(jdbcTemplate);

        // Every processing attempt fails until cleared
        matchProcessor.failRetryableForTest();

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


        // The original key is preserved
        assertThat(dltRecord.key())
                .isEqualTo(failingEvent.matchId().toString());

        // The original topic is recorded as a header
        byte[] originalTopic = dltRecord.headers()
                .lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)
                .value();

        assertThat(new String(originalTopic, StandardCharsets.UTF_8))
                .isEqualTo("arena-match-completed");

        // The listener should stop
        awaitListenerStopped();

        // Kafka stores the next offset to consume, so a value past the failed
        // record's offset would mean the failed record was acknowledged
        Long committed = committedOffset(KAFKA, groupId, topic, partition);
        if (committed != null) {
            assertThat(committed).isLessThanOrEqualTo(failedOffset);
        }

        // Clear the forced failure so listener can now process successfully
        matchProcessor.clearRetryableFailureForTest();

        ArenaMatchCompleted validEvent = threeVsThreeEvent(
                UUID.fromString("3d2f1c0b-1a2b-3c4d-5e6f-7890abcdef01"),
                UUID.fromString("3d2f1c0b-1a2b-3c4d-5e6f-7890abcdef02"),
                1L);

        String validJson = objectMapper.writeValueAsString(validEvent);

        kafkaTemplate.send("arena-match-completed",
                        validEvent.matchId().toString(),
                        validJson)
                .get(10, TimeUnit.SECONDS);

        // A stopped listener does not consume
        assertNothingProcessed();

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

    // Prove that nothing was processed during a short period
    // If the listener were still running, the valid event would
    // insert a new processed events row
    private void assertNothingProcessed() throws InterruptedException {

        long deadline = System.currentTimeMillis() + 2_000;

        while (System.currentTimeMillis() < deadline) {
            assertThat(countRows(jdbcTemplate, "processed_events"))
                    .isZero();

            Thread.sleep(200);
        }
    }


}
