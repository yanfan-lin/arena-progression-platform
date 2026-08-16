package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.yanfan.arena.platform.match.processing.MatchProcessorTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

// Verify that a retryable failure is retried,
// and then commits exactly one match
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true"
})
@Testcontainers
class MatchRetryRecoveryIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.11"))
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

    @Test
    void retryableFailureRecoversOnRetry() throws Exception {

        playersAndTeams();

        // Make only the next processing attempt fail,
        // then succeed on the retry
        matchProcessor.failBeforeCommitForTest();

        // Team 1 wins
        ArenaMatchCompleted event = event(
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                1L,
                MatchMode.THREE_VS_THREE,
                eventTeam(1L,
                        eventPlayer(101L, 5, 2, 3),
                        eventPlayer(102L, 2, 1, 1),
                        eventPlayer(103L, 0, 0, 0)),
                eventTeam(2L,
                        eventPlayer(201L, 1, 4, 2),
                        eventPlayer(202L, 0, 1, 1),
                        eventPlayer(203L, 2, 2, 0)));

        String json = objectMapper.writeValueAsString(event);

        kafkaTemplate.send("arena-match-completed", event.matchId().toString(), json)
                .get(10, TimeUnit.SECONDS);

        // Wait for the retry to succeed and commit one match
        awaitProcessedCount(1);

        assertThat(countRows(jdbcTemplate, "matches"))
                .isEqualTo(1);
        assertThat(countRows(jdbcTemplate, "match_team_results"))
                .isEqualTo(2);
        assertThat(countRows(jdbcTemplate, "match_participant_results"))
                .isEqualTo(6);

        // Player alphaOne's XP updated
        Long alphaOneXp = jdbcTemplate.queryForObject(
                "SELECT total_xp FROM players WHERE player_id = ?",
                Long.class,
                101L);

        assertThat(alphaOneXp).isEqualTo(650L);

        // Nothing was sent to the DLT since retry succeeded
        assertNoDltRecord(event.matchId().toString());
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

    // Keep polling until the listener has committed the expected number of events
    private void awaitProcessedCount(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;

        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM processed_events", Integer.class);

            if (count != null && count == expected) {
                return;
            }

            Thread.sleep(200);
        }

        assertThat(countRows(jdbcTemplate, "processed_events")).isEqualTo(expected);
    }

    // Poll the DLT and fail if the expected key appears
    private void assertNoDltRecord(String expectedKey) {
        // Consumer configs
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "retry-no-dlt-check",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"
        );

        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config);
        consumer.subscribe(List.of("arena-match-completed-dlt"));

        long deadline = System.currentTimeMillis() + 3_000;

        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));

            for (ConsumerRecord<String, byte[]> record : records) {
                assertThat(record.key())
                        .isNotEqualTo(expectedKey);
            }
        }

        consumer.close();
    }


}
