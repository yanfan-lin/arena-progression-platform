package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static com.yanfan.arena.platform.match.processing.MatchProcessorTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Verify that a forced mid-transaction failure rolls back every MySQL change
@SpringBootTest
@Testcontainers
class MatchProcessorRollbackIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.11"))
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
    void failedMatchInsertRollsBackEveryChange() {

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

        // Trigger the test-only failure before processing the event
        matchProcessor.failBeforeCommitForTest();

        // A completely valid match event
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
                        eventPlayer(202L, 0, 1, 1),
                        eventPlayer(203L, 2, 2, 0)
                )
        );

        // The processor inserts all snapshots, then throws
        assertThatThrownBy(() -> matchProcessor.process(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Forced failure after match inserts");

        // No changes after the rollback, and all snapshot tables are empty
        assertThat(countRows(jdbcTemplate, "processed_events")).isZero();
        assertThat(countRows(jdbcTemplate, "matches")).isZero();
        assertThat(countRows(jdbcTemplate, "match_team_results")).isZero();
        assertThat(countRows(jdbcTemplate, "match_participant_results")).isZero();

        // Players were not given XP
        Long alphaOneXp = jdbcTemplate.queryForObject(
                "SELECT total_xp FROM players WHERE player_id = ?", Long.class, 101L);
        assertThat(alphaOneXp).isEqualTo(500L);

        // Teams kept their original ratings
        Integer alphaRating = jdbcTemplate.queryForObject(
                "SELECT rating FROM teams WHERE team_id = ?", Integer.class, 1L);
        assertThat(alphaRating).isEqualTo(1000);

        // Teams kept their stats
        Integer alphaMatchesPlayed = jdbcTemplate.queryForObject(
                "SELECT matches_played FROM teams WHERE team_id = ?", Integer.class, 1L);
        assertThat(alphaMatchesPlayed).isEqualTo(0);

        Integer alphaWins = jdbcTemplate.queryForObject(
                "SELECT wins FROM teams WHERE team_id = ?", Integer.class, 1L);
        assertThat(alphaWins).isEqualTo(0);

        Integer alphaKills = jdbcTemplate.queryForObject(
                "SELECT total_kills FROM teams WHERE team_id = ?", Integer.class, 1L);
        assertThat(alphaKills).isEqualTo(0);

        // Player level is unchanged
        Integer alphaOneLevel = jdbcTemplate.queryForObject(
                "SELECT level FROM players WHERE player_id = ?", Integer.class, 101L);
        assertThat(alphaOneLevel).isEqualTo(1);

    }

}
