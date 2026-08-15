package com.yanfan.arena.platform.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.team.ArenaMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static com.yanfan.arena.platform.match.MatchProcessorTestData.*;
import static org.assertj.core.api.Assertions.assertThat;

// Verify that duplicate events are ignored
@SpringBootTest
@Testcontainers
class MatchProcessorDuplicateIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.11"))
            .withDatabaseName("arena")
            .withUsername("arena")
            .withPassword("arena-test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
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
        assertThat(first.outcome()).isEqualTo(MatchProcessingResult.MatchProcessingOutcome.PROCESSED);

        // Redelivering the exact same event changes nothing
        MatchProcessingResult second = matchProcessor.process(event);
        assertThat(second.outcome()).isEqualTo(MatchProcessingResult.MatchProcessingOutcome.DUPLICATE);
        assertThat(second.processed()).isNull();

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
        assertThat(alphaOneXp).isEqualTo(650L);
    }

    @Test
    void reusedMatchIdWithNewEventIdIsDuplicate() {

        ArenaMatchCompleted original = createMatchEvent();
        matchProcessor.process(original);

        // Same match as the created one but with new event ID
        ArenaMatchCompleted newEvent = event(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                1L,
                MatchMode.THREE_VS_THREE,
                eventTeam(1L,
                        eventPlayer(101L, 5, 2, 3),
                        eventPlayer(102L, 2, 1, 1),
                        eventPlayer(103L, 0, 0, 0)
                ),
                eventTeam(2L,
                        eventPlayer(201L, 1, 4, 2),
                        eventPlayer(202L, 0, 1, 1),
                        eventPlayer(203L, 2, 2, 0)
                )
        );

        MatchProcessingResult result = matchProcessor.process(newEvent);
        assertThat(result.outcome())
                .isEqualTo(MatchProcessingResult.MatchProcessingOutcome.DUPLICATE);

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

        //  Same match as the created one but with new match ID
        ArenaMatchCompleted newEvent = event(
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                1L,
                MatchMode.THREE_VS_THREE,
                eventTeam(1L,
                        eventPlayer(101L, 5, 2, 3),
                        eventPlayer(102L, 2, 1, 1),
                        eventPlayer(103L, 0, 0, 0)
                ),
                eventTeam(2L,
                        eventPlayer(201L, 1, 4, 2),
                        eventPlayer(202L, 0, 1, 1),
                        eventPlayer(203L, 2, 2, 0)
                )
        );

        MatchProcessingResult result = matchProcessor.process(newEvent);
        assertThat(result.outcome())
                .isEqualTo(MatchProcessingResult.MatchProcessingOutcome.DUPLICATE);

        // Still exactly one event record
        assertThat(countRows(jdbcTemplate, "processed_events")).isEqualTo(1);
        assertThat(countRows(jdbcTemplate, "match_team_results")).isEqualTo(2);
    }

    // Create players, teams, rosters, and the original match event
    private ArenaMatchCompleted createMatchEvent() {
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

        return event(
                UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79"),
                UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86"),
                1L,
                MatchMode.THREE_VS_THREE,
                eventTeam(1L,
                        eventPlayer(101L, 5, 2, 3),
                        eventPlayer(102L, 2, 1, 1),
                        eventPlayer(103L, 0, 0, 0)
                ),
                eventTeam(2L,
                        eventPlayer(201L, 1, 4, 2),
                        eventPlayer(202L, 0, 1, 1),
                        eventPlayer(203L, 2, 2, 0)
                )
        );

    }

}
