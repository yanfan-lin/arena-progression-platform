package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.yanfan.arena.platform.match.processing.MatchProcessorTestData.*;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.*;
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

    @Test
    void retryableFailureRecoversOnRetry() throws Exception {

        insertThreeVsThreePlayersAndTeams(jdbcTemplate);

        // Make only the next processing attempt fail,
        // then succeed on the retry
        matchProcessor.failBeforeCommitForTest();

        // Team 1 wins
        ArenaMatchCompleted event = threeVsThreeEvent(
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                1L);

        String json = objectMapper.writeValueAsString(event);

        kafkaTemplate.send("arena-match-completed", event.matchId().toString(), json)
                .get(10, TimeUnit.SECONDS);

        // Wait for the retry to succeed and commit one match
        awaitProcessedCount(jdbcTemplate, 1);

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

        assertThat(alphaOneXp)
                .isEqualTo(650L);
    }

}
