package com.yanfan.arena.platform.match.messaging;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.match.processing.MatchProcessorTestData;
import com.yanfan.arena.platform.team.domain.ArenaMode;
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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// Verify that a real Kafka event reaches the listener,
// is passed to the match processor, and commits exactly one match
@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=true"
})
class MatchEventListenerIT {

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


    @Test
    void validKafkaEventIsProcessedExactlyOnce() throws Exception {

        MatchProcessorTestData.insertPlayer(jdbcTemplate, 101L, "AlphaOne", 500L);
        MatchProcessorTestData.insertPlayer(jdbcTemplate, 102L, "AlphaTwo", 900L);
        MatchProcessorTestData.insertPlayer(jdbcTemplate, 103L, "AlphaThree", 0L);
        MatchProcessorTestData.insertPlayer(jdbcTemplate, 201L, "BetaOne", 500L);
        MatchProcessorTestData.insertPlayer(jdbcTemplate, 202L, "BetaTwo", 500L);
        MatchProcessorTestData.insertPlayer(jdbcTemplate, 203L, "BetaThree", 500L);

        MatchProcessorTestData.insertTeam(jdbcTemplate, 1L, "Alpha", ArenaMode.THREE_VS_THREE, 1000);
        MatchProcessorTestData.insertTeam(jdbcTemplate, 2L, "Beta", ArenaMode.THREE_VS_THREE, 1000);

        MatchProcessorTestData.addMember(jdbcTemplate, 1L, 101L);
        MatchProcessorTestData.addMember(jdbcTemplate, 1L, 102L);
        MatchProcessorTestData.addMember(jdbcTemplate, 1L, 103L);
        MatchProcessorTestData.addMember(jdbcTemplate, 2L, 201L);
        MatchProcessorTestData.addMember(jdbcTemplate, 2L, 202L);
        MatchProcessorTestData.addMember(jdbcTemplate, 2L, 203L);

        // Team 1 wins
        ArenaMatchCompleted event = MatchProcessorTestData.event(
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                1L,
                MatchMode.THREE_VS_THREE,
                MatchProcessorTestData.eventTeam(1L,
                        MatchProcessorTestData.eventPlayer(101L, 5, 2, 3),
                        MatchProcessorTestData.eventPlayer(102L, 2, 1, 1),
                        MatchProcessorTestData.eventPlayer(103L, 0, 0, 0)),
                MatchProcessorTestData.eventTeam(2L,
                        MatchProcessorTestData.eventPlayer(201L, 1, 4, 2),
                        MatchProcessorTestData.eventPlayer(202L, 0, 1, 1),
                        MatchProcessorTestData.eventPlayer(203L, 2, 2, 0)));

        // Publish the event as JSON with the match ID as the Kafka key
        String json = objectMapper.writeValueAsString(event);
        kafkaTemplate.send("arena-match-completed", event.matchId().toString(), json)
                .get(10, TimeUnit.SECONDS);

        // Wait for the listener to consume and process it
        awaitProcessedCount(1);

        assertThat(MatchProcessorTestData.countRows(jdbcTemplate, "matches"))
                .isEqualTo(1);
        assertThat(MatchProcessorTestData.countRows(jdbcTemplate, "match_team_results"))
                .isEqualTo(2);
        assertThat(MatchProcessorTestData.countRows(jdbcTemplate, "match_participant_results"))
                .isEqualTo(6);

        // The winning player gained XP proves transaction succeeded
        Long alphaOneXp = jdbcTemplate.queryForObject(
                "SELECT total_xp FROM players WHERE player_id = ?", Long.class, 101L);

        assertThat(alphaOneXp).isEqualTo(650L);
    }

    // Poll the table until the listener has committed the match
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

        assertThat(MatchProcessorTestData.countRows(jdbcTemplate, "processed_events"))
                .isEqualTo(expected);
    }

}
