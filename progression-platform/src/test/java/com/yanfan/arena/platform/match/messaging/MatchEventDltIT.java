package com.yanfan.arena.platform.match.messaging;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.platform.match.processing.MatchProcessorTestData;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Verify that permanent match failures are routed to the dead-letter topic.
@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true"
})
public class MatchEventDltIT {

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
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ObjectMapper objectMapper;

    // Clear the database before the test
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM match_participant_results");
        jdbcTemplate.update("DELETE FROM matches");
        jdbcTemplate.update("DELETE FROM match_team_results");
        jdbcTemplate.update("DELETE FROM processed_events");
        jdbcTemplate.update("DELETE FROM team_members");
        jdbcTemplate.update("DELETE FROM teams");
        jdbcTemplate.update("DELETE FROM players");
    }

    @Test
    void malformedJsonIsPublishedToDlt() throws Exception {
        String badJson = "{not-valid-json";

        kafkaTemplate.send("arena-match-completed", "bad-key", badJson)
                .get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, byte[]> dltRecord = awaitDltRecord("bad-key");

        // Original key is preserved
        assertThat(dltRecord.key())
                .isEqualTo("bad-key");

        // Original raw bytes is preserved
        assertThat(new String(dltRecord.value(), StandardCharsets.UTF_8))
                .isEqualTo(badJson);

        // Original topic is recorded as a header
        byte[] originalTopic = dltRecord.headers()
                .lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)
                .value();

        assertThat(new String(originalTopic, StandardCharsets.UTF_8))
                .isEqualTo("arena-match-completed");

        // The malformed event was never processed
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processed_events", Integer.class))
                .isZero();
    }

    @Test
    void permanentFailureIsPublishedToDltAndConsumerContinues() throws Exception {
        MatchProcessorTestData.insertThreeVsThreePlayersAndTeams(jdbcTemplate);

        // Publish an event that fails permanently
        ArenaMatchCompleted event = MatchProcessorTestData.threeVsThreeEvent(
                UUID.fromString("5f8b9a0a-6b3d-4e5f-8f1a-111111111111"),
                UUID.fromString("5f8b9a0a-6b3d-4e5f-8f1a-222222222222"),
                // This winner is not one of the two teams, so processing fails permanently
                999L);

        // Publish the failing event to the normal match-completed topic
        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("arena-match-completed", event.matchId().toString(), json)
                .get(10, TimeUnit.SECONDS);

        // Find the DLT record for this specific match ID
        ConsumerRecord<String, byte[]> dltRecord = awaitDltRecord(event.matchId().toString());

        // The original Kafka key is preserved
        assertThat(dltRecord.key())
                .isEqualTo(event.matchId().toString());

        // The DLT contains the same typed event that failed processing
        String publishedJson = new String(dltRecord.value(), StandardCharsets.UTF_8);
        ArenaMatchCompleted publishedEvent = objectMapper.readValue(publishedJson, ArenaMatchCompleted.class);

        assertThat(publishedEvent.eventId())
                .isEqualTo(event.eventId());

        assertThat(publishedEvent.winnerTeamId())
                .isEqualTo(999L);

        // The original topic is recorded as a header
        byte[] originalTopic = dltRecord.headers()
                .lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)
                .value();

        assertThat(new String(originalTopic, StandardCharsets.UTF_8))
                .isEqualTo("arena-match-completed");

        // The original partition and offset are also recorded as headers
        assertThat(dltRecord.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION))
                .isNotNull();
        assertThat(dltRecord.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET))
                .isNotNull();

        // Database stays unchanged because the event failed
        assertThat(MatchProcessorTestData.countRows(jdbcTemplate, "processed_events"))
                .isZero();
        assertThat(MatchProcessorTestData.countRows(jdbcTemplate, "matches"))
                .isZero();

        // Publish a valid event to prove the listener keeps consuming
        ArenaMatchCompleted goodEvent = MatchProcessorTestData.threeVsThreeEvent(
                UUID.fromString("3d2f1c0b-1a2b-3c4d-5e6f-7890abcdef01"),
                UUID.fromString("3d2f1c0b-1a2b-3c4d-5e6f-7890abcdef02"),
                1L);

        kafkaTemplate.send("arena-match-completed",
                        goodEvent.matchId().toString(),
                        objectMapper.writeValueAsString(goodEvent))
                .get(10, TimeUnit.SECONDS);

        // Wait for the valid event to be committed
        MatchProcessorTestData.awaitProcessedCount(jdbcTemplate, 1);

        assertThat(MatchProcessorTestData.countRows(jdbcTemplate, "matches"))
                .isEqualTo(1);
    }

    private ConsumerRecord<String, byte[]> awaitDltRecord(String expectedKey) throws Exception {

        // Throwaway consumer that reads the DLT
        Map<String, Object> consumerConfig = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-group",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerConfig);
        consumer.subscribe(List.of("arena-match-completed-dlt"));

        ConsumerRecord<String, byte[]> found = null;

        // Give Kafka 15 seconds to deliver DLT record
        long deadline = System.currentTimeMillis() + 15_000;

        while (System.currentTimeMillis() < deadline) {
            // Keep polling until the expected DLT record appears.
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));

            for (ConsumerRecord<String, byte[]> record : records) {
                // Match the expected key and confirm Spring added the original-topic header.
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
}
