package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static com.yanfan.arena.platform.test.IntegrationTestContainers.mysqlContainer;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.registerMySqlProperties;
import static com.yanfan.arena.platform.match.processing.MatchProcessorTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

// Verify that duplicate events are ignored
@SpringBootTest
@Testcontainers
class MatchProcessorDuplicateIT {

    @Container
    static final MySQLContainer MYSQL = mysqlContainer();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registerMySqlProperties(registry, MYSQL);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MatchProcessor matchProcessor;

    // Clear all tables before each test
    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM match_participant_results");
        jdbcTemplate.update("DELETE FROM matches");
        jdbcTemplate.update("DELETE FROM match_team_results");
        jdbcTemplate.update("DELETE FROM processed_events");
        jdbcTemplate.update("DELETE FROM team_members");
        jdbcTemplate.update("DELETE FROM teams");
        jdbcTemplate.update("DELETE FROM players");
    }

    @Test
    void exactSameEventIsDuplicate() {

        ArenaMatchCompleted event = createMatchEvent();

        // First delivery gets processed normally
        MatchProcessingResult first = matchProcessor.process(event);
        assertThat(first.outcome())
                .isEqualTo(MatchProcessingResult.MatchProcessingOutcome.PROCESSED);

        // Redelivering the exact same event changes nothing
        MatchProcessingResult second = matchProcessor.process(event);
        assertThat(second.outcome())
                .isEqualTo(MatchProcessingResult.MatchProcessingOutcome.DUPLICATE);

        // The duplicate still carries the committed reconciliation data
        assertThat(second.processed()).isNull();

        assertThat(second.reconciliation()).isNotNull();

        assertThat(second.reconciliation().committedEventId())
                .isEqualTo("4e74866d-5a18-4695-bf5e-ff8b79226b79");

        assertThat(second.reconciliation().committedMatchId())
                .isEqualTo("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86");

        assertThat(second.reconciliation().teamIds())
                .containsExactlyInAnyOrder(1L, 2L);

        assertThat(second.reconciliation().playerIds())
                .hasSize(6);

        // No extra rows were written
        assertThat(countRows(jdbcTemplate, "processed_events"))
                .isEqualTo(1);
        assertThat(countRows(jdbcTemplate, "matches"))
                .isEqualTo(1);
        assertThat(countRows(jdbcTemplate, "match_team_results"))
                .isEqualTo(2);
        assertThat(countRows(jdbcTemplate, "match_participant_results"))
                .isEqualTo(6);

        // Player XP is still 650, not 800
        Long alphaOneXp = jdbcTemplate.queryForObject(
                "SELECT total_xp FROM players WHERE player_id = ?", Long.class, 101L);
        assertThat(alphaOneXp)
                .isEqualTo(650L);
    }

    @Test
    void reusedMatchIdWithNewEventIdIsDuplicate() {

        ArenaMatchCompleted original = createMatchEvent();
        matchProcessor.process(original);

        // Same match as the created one but with new event ID
        ArenaMatchCompleted newEvent = threeVsThreeEvent(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                1L);

        MatchProcessingResult result = matchProcessor.process(newEvent);
        assertThat(result.outcome())
                .isEqualTo(MatchProcessingResult.MatchProcessingOutcome.DUPLICATE);

        // The committed event ID wins over the incoming new one
        assertThat(result.reconciliation()).isNotNull();
        assertThat(result.reconciliation().committedEventId())
                .isEqualTo("4e74866d-5a18-4695-bf5e-ff8b79226b79");

        // Still exactly one match stored
        assertThat(countRows(jdbcTemplate, "matches"))
                .isEqualTo(1);
        assertThat(countRows(jdbcTemplate, "match_participant_results"))
                .isEqualTo(6);
    }

    @Test
    void reusedEventIdWithNewMatchIdIsDuplicate() {
        ArenaMatchCompleted original = createMatchEvent();
        matchProcessor.process(original);

        // Use the original event ID with a new match ID
        ArenaMatchCompleted newEvent = threeVsThreeEvent(
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                1L);

        MatchProcessingResult result = matchProcessor.process(newEvent);
        assertThat(result.outcome())
                .isEqualTo(MatchProcessingResult.MatchProcessingOutcome.DUPLICATE);

        // The committed match ID wins over the incoming new one
        assertThat(result.reconciliation()).isNotNull();
        assertThat(result.reconciliation().committedMatchId())
                .isEqualTo("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86");

        // Still exactly one event record
        assertThat(countRows(jdbcTemplate, "processed_events")).isEqualTo(1);
        assertThat(countRows(jdbcTemplate, "match_team_results")).isEqualTo(2);
    }

    // Create players, teams, rosters, and the original match event
    private ArenaMatchCompleted createMatchEvent() {
        insertThreeVsThreePlayersAndTeams(jdbcTemplate);

        return threeVsThreeEvent(
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                1L);
    }

}
