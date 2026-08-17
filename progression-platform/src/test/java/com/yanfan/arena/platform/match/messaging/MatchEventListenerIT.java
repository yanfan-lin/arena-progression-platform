package com.yanfan.arena.platform.match.messaging;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.platform.match.processing.MatchProcessorTestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
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


    @Test
    void validKafkaEventCommitsOneMatch() throws Exception {

        MatchProcessorTestData.insertThreeVsThreePlayersAndTeams(jdbcTemplate);

        // Team 1 wins
        ArenaMatchCompleted event = MatchProcessorTestData.threeVsThreeEvent(
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                1L);

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
