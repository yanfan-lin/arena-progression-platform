package com.yanfan.arena.platform.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.team.ArenaMode;
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

// Verify that one valid 3v3 match updates every table exactly once
@SpringBootTest
@Testcontainers
class MatchProcessorIT {

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

    @Test
    void processesValidMatchAndUpdatesEverything() {

        insertPlayer(jdbcTemplate, 101L, "AlphaOne", 500L);
        insertPlayer(jdbcTemplate, 102L, "AlphaTwo", 900L);
        insertPlayer(jdbcTemplate, 103L, "AlphaThree", 0L);
        insertPlayer(jdbcTemplate, 201L, "BetaOne", 500L);
        insertPlayer(jdbcTemplate, 202L, "BetaTwo", 500L);
        insertPlayer(jdbcTemplate, 203L, "BetaThree", 500L);

        insertTeam(jdbcTemplate, 1L, "Alpha", ArenaMode.THREE_VS_THREE, 1000);
        insertTeam(jdbcTemplate, 2L, "Beta", ArenaMode.THREE_VS_THREE, 1000);

        // Lock the rosters so the for the domain validator
        addMember(jdbcTemplate, 1L, 101L);
        addMember(jdbcTemplate, 1L, 102L);
        addMember(jdbcTemplate, 1L, 103L);
        addMember(jdbcTemplate, 2L, 201L);
        addMember(jdbcTemplate, 2L, 202L);
        addMember(jdbcTemplate, 2L, 203L);

        // Team 1 wins
        ArenaMatchCompleted event = event(
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
                        eventPlayer(202L, 0, 3, 1),
                        eventPlayer(203L, 2, 2, 0)
                )
        );

        MatchProcessingResult result = matchProcessor.process(event);

        // The event was processed, and not treated as a duplicate or redeliver events
        assertThat(result.outcome())
                .isEqualTo(MatchProcessingResult.MatchProcessingOutcome.PROCESSED);
        assertThat(result.processed())
                .isNotNull();

        // Exactly one row for every snapshot table
        assertThat(countRows(jdbcTemplate, "processed_events"))
                .isEqualTo(1);
        assertThat(countRows(jdbcTemplate, "matches"))
                .isEqualTo(1);
        assertThat(countRows(jdbcTemplate, "match_team_results"))
                .isEqualTo(2);
        assertThat(countRows(jdbcTemplate, "match_participant_results"))
                .isEqualTo(6);

        // Winning players gained 150 XP, losing players gained 100 XP
        Long alphaOneXp = jdbcTemplate.queryForObject(
                "SELECT total_xp FROM players WHERE player_id = ?", Long.class, 101L);
        assertThat(alphaOneXp)
                .isEqualTo(650L);

        Long betaOneXp = jdbcTemplate.queryForObject(
                "SELECT total_xp FROM players WHERE player_id = ?", Long.class, 201L);
        assertThat(betaOneXp)
                .isEqualTo(600L);

        // The player who crossed 1000 XP leveled up to 2
        Integer alphaTwoLevel = jdbcTemplate.queryForObject(
                "SELECT level FROM players WHERE player_id = ?", Integer.class, 102L);
        assertThat(alphaTwoLevel)
                .isEqualTo(2);

        // Equal ratings move 16 points: winner 1016, loser 984
        Integer alphaRating = jdbcTemplate.queryForObject(
                "SELECT rating FROM teams WHERE team_id = ?", Integer.class, 1L);
        assertThat(alphaRating)
                .isEqualTo(1016);

        Integer betaRating = jdbcTemplate.queryForObject(
                "SELECT rating FROM teams WHERE team_id = ?", Integer.class, 2L);
        assertThat(betaRating)
                .isEqualTo(984);

        // Team stats updated: Alpha 1 win and 7 kills, Beta 1 loss and 9 deaths
        Integer alphaWins = jdbcTemplate.queryForObject(
                "SELECT wins FROM teams WHERE team_id = ?", Integer.class, 1L);
        assertThat(alphaWins)
                .isEqualTo(1);

        Integer alphaKills = jdbcTemplate.queryForObject(
                "SELECT total_kills FROM teams WHERE team_id = ?", Integer.class, 1L);
        assertThat(alphaKills)
                .isEqualTo(7);

        Integer betaLosses = jdbcTemplate.queryForObject(
                "SELECT losses FROM teams WHERE team_id = ?", Integer.class, 2L);
        assertThat(betaLosses)
                .isEqualTo(1);

        Integer betaDeaths = jdbcTemplate.queryForObject(
                "SELECT total_deaths FROM teams WHERE team_id = ?", Integer.class, 2L);
        assertThat(betaDeaths)
                .isEqualTo(9);

        // Return summary for later Redis use
        assertThat(result.processed().teamResults()).hasSize(2);
        assertThat(result.processed().playerResults()).hasSize(6);
    }

}
