package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.11"))
            .withDatabaseName("arena")
            .withUsername("arena")
            .withPassword("arena-test");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
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
        playersAndTeams();

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
        ConsumerRecord<String, byte[]> dltRecord = awaitDltRecord(failingEvent.matchId().toString());

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
        Long committed = committedOffset(topic, partition);
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

    private void playersAndTeams() {

        insertPlayer(jdbcTemplate, 101L, "AlphaOne", 500L);
        insertPlayer(jdbcTemplate, 102L, "AlphaTwo", 900L);
        insertPlayer(jdbcTemplate, 103L, "AlphaThree", 0L);
        insertPlayer(jdbcTemplate, 201L, "BetaOne", 500L);
        insertPlayer(jdbcTemplate, 202L, "BetaTwo", 500L);
        insertPlayer(jdbcTemplate, 203L, "BetaThree", 500L);

        insertTeam(jdbcTemplate, 1L, "Alpha", ArenaMode.THREE_VS_THREE, 1000);
        insertTeam(jdbcTemplate, 2L, "Beta", ArenaMode.THREE_VS_THREE, 1000);

        addMember(jdbcTemplate, 1L, 101L);
        addMember(jdbcTemplate, 1L, 102L);
        addMember(jdbcTemplate, 1L, 103L);
        addMember(jdbcTemplate, 2L, 201L);
        addMember(jdbcTemplate, 2L, 202L);
        addMember(jdbcTemplate, 2L, 203L);
    }

    // Team 1 wins
    private ArenaMatchCompleted threeVsThreeEvent(
            UUID eventId,
            UUID matchId,
            long winnerTeamId)
    {
        return event(
                eventId,
                matchId,
                winnerTeamId,
                MatchMode.THREE_VS_THREE,
                eventTeam(1L,
                        eventPlayer(101L, 5, 2, 3),
                        eventPlayer(102L, 2, 1, 1),
                        eventPlayer(103L, 0, 0, 0)),
                eventTeam(2L,
                        eventPlayer(201L, 1, 4, 2),
                        eventPlayer(202L, 0, 1, 1),
                        eventPlayer(203L, 2, 2, 0)));
    }

    // Wait for the dead-letter records with the expected key,
    // "earliest" because this test's check group has never committed a DLT offset before
    private ConsumerRecord<String, byte[]> awaitDltRecord(String expectedKey) throws Exception {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "retry-exhaustion-dlt-check",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config);
        consumer.subscribe(List.of("arena-match-completed-dlt"));

        ConsumerRecord<String, byte[]> found = null;

        // Keep polling until the record appears or the deadline passes
        long deadline = System.currentTimeMillis() + 20_000;

        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));

            for (ConsumerRecord<String, byte[]> record : records) {
                // The original key and topic header are preserved
                if (expectedKey.equals(record.key())
                        && record.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC) != null) {
                    found = record;

                    break;
                }
            }

            if (found != null) {
                break;
            }
        }

        consumer.close();

        if (found == null) {
            throw new AssertionError("No dead-letter record arrived");
        }

        return found;
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

    // Read the consumer group's committed offset,
    // or return null if none is committed
    private Long committedOffset(String topic, int partition) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class
        );

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config);

        OffsetAndMetadata offsetAndMetadata = consumer.committed(
                        Set.of(new TopicPartition(topic, partition)), Duration.ofSeconds(5))
                .get(new TopicPartition(topic, partition));

        consumer.close();

        return offsetAndMetadata == null ? null : offsetAndMetadata.offset();
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
