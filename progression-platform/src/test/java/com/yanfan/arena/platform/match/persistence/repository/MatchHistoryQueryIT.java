package com.yanfan.arena.platform.match.persistence.repository;

import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Timestamp;
import java.time.Instant;

import static com.yanfan.arena.platform.match.processing.MatchProcessorTestData.*;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.mysqlContainer;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.registerMySqlProperties;
import static org.assertj.core.api.Assertions.assertThat;

// Verify the match history queries against MySQL
@SpringBootTest
@Testcontainers
class MatchHistoryQueryIT {

    @Container
    static final MySQLContainer MYSQL = mysqlContainer();

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registerMySqlProperties(registry, MYSQL);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MatchTeamResultRepository matchTeamResultRepository;

    @Autowired
    MatchParticipantResultRepository matchParticipantResultRepository;

    @BeforeEach
    void cleanTables() {
        clearMatchTestData(jdbcTemplate);
    }

    @Test
    void returnsOrderedPlayerAndTeamHistoryWithPageTotals() {

        insertPlayer(
                jdbcTemplate,
                101L,
                "Current Alpha One",
                500L
        );

        insertPlayer(
                jdbcTemplate,
                102L,
                "Current Alpha Two",
                500L
        );

        insertPlayer(
                jdbcTemplate,
                201L,
                "Current Beta One",
                500L
        );

        insertPlayer(
                jdbcTemplate,
                202L,
                "Current Beta Two",
                500L
        );

        insertTeam(
                jdbcTemplate,
                1L,
                "Current Alpha",
                ArenaMode.THREE_VS_THREE,
                1001
        );

        insertTeam(
                jdbcTemplate,
                2L,
                "Current Beta",
                ArenaMode.THREE_VS_THREE,
                999
        );

        String lowerMatchId =
                "11111111-1111-1111-1111-111111111111";

        String higherMatchId =
                "22222222-2222-2222-2222-222222222222";

        // Use the same timestamp so match ID decides the order
        Instant completedAt =
                Instant.parse("2026-08-19T12:00:00Z");

        insertTeamResult(
                lowerMatchId,
                1L,
                "Stored Alpha Old",
                1000,
                16,
                1016
        );

        insertTeamResult(
                lowerMatchId,
                2L,
                "Stored Beta Old",
                1000,
                -16,
                984
        );

        insertTeamResult(
                higherMatchId,
                1L,
                "Stored Alpha New",
                1016,
                -15,
                1001
        );

        insertTeamResult(
                higherMatchId,
                2L,
                "Stored Beta New",
                984,
                15,
                999
        );

        // Insert team results first because the match winner must reference an existing result row
        insertMatch(
                lowerMatchId,
                1L,
                completedAt
        );

        insertMatch(
                higherMatchId,
                2L,
                completedAt
        );

        insertParticipant(
                lowerMatchId,
                101L,
                1L,
                "Stored Alpha One Old",
                3,
                1,
                2,
                150
        );

        insertParticipant(
                lowerMatchId,
                102L,
                1L,
                "Stored Alpha Two Old",
                4,
                2,
                1,
                150
        );

        insertParticipant(
                lowerMatchId,
                201L,
                2L,
                "Stored Beta One Old",
                1,
                4,
                1,
                100
        );

        insertParticipant(
                lowerMatchId,
                202L,
                2L,
                "Stored Beta Two Old",
                2,
                3,
                1,
                100
        );

        insertParticipant(
                higherMatchId,
                101L,
                1L,
                "Stored Alpha One New",
                2,
                3,
                1,
                100
        );

        insertParticipant(
                higherMatchId,
                102L,
                1L,
                "Stored Alpha Two New",
                5,
                2,
                4,
                100
        );

        insertParticipant(
                higherMatchId,
                201L,
                2L,
                "Stored Beta One New",
                3,
                4,
                2,
                150
        );

        insertParticipant(
                higherMatchId,
                202L,
                2L,
                "Stored Beta Two New",
                2,
                3,
                1,
                150
        );

        PageRequest firstResultOnly =
                PageRequest.of(0, 1);

        Page<PlayerMatchHistoryProjection> playerHistory =
                matchParticipantResultRepository.findHistoryByPlayerId(
                        101L,
                        firstResultOnly
                );

        assertThat(playerHistory.getTotalElements())
                .isEqualTo(2);
        assertThat(playerHistory.getTotalPages())
                .isEqualTo(2);

        PlayerMatchHistoryProjection playerResult =
                playerHistory.getContent().getFirst();

        assertThat(playerResult.getMatchId())
                .isEqualTo(higherMatchId);
        assertThat(playerResult.getPlayerName())
                .isEqualTo("Stored Alpha One New");
        assertThat(playerResult.getTeamName())
                .isEqualTo("Stored Alpha New");
        assertThat(playerResult.getRatingBefore())
                .isEqualTo(1016);
        assertThat(playerResult.getXpEarned())
                .isEqualTo(100);

        Page<TeamMatchHistoryProjection> teamHistory =
                matchTeamResultRepository.findHistoryByTeamId(
                        1L,
                        firstResultOnly
                );

        assertThat(teamHistory.getTotalElements())
                .isEqualTo(2);
        assertThat(teamHistory.getTotalPages())
                .isEqualTo(2);

        TeamMatchHistoryProjection teamResult =
                teamHistory.getContent().getFirst();

        assertThat(teamResult.getMatchId())
                .isEqualTo(higherMatchId);
        assertThat(teamResult.getTeamName())
                .isEqualTo("Stored Alpha New");
        assertThat(teamResult.getRatingChange())
                .isEqualTo(-15);
        assertThat(teamResult.getKills())
                .isEqualTo(7);
        assertThat(teamResult.getDeaths())
                .isEqualTo(5);
        assertThat(teamResult.getAssists())
                .isEqualTo(5);
    }

    private void insertTeamResult(
            String matchId,
            long teamId,
            String teamName,
            int ratingBefore,
            int ratingChange,
            int ratingAfter)
    {
        jdbcTemplate.update(
                """
                        INSERT INTO match_team_results (
                            match_id,
                            team_id,
                            team_name_snapshot,
                            rating_before,
                            rating_change,
                            rating_after
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                matchId,
                teamId,
                teamName,
                ratingBefore,
                ratingChange,
                ratingAfter
        );
    }

    private void insertMatch(
            String matchId,
            long winningTeamId,
            Instant completedAt)
    {
        jdbcTemplate.update(
                """
                        INSERT INTO matches (
                            match_id,
                            mode,
                            winning_team_id,
                            contract_version,
                            completed_at
                        )
                        VALUES (?, 'THREE_VS_THREE', ?, 1, ?)
                        """,
                matchId,
                winningTeamId,
                Timestamp.from(completedAt)
        );
    }

    private void insertParticipant(
            String matchId,
            long playerId,
            long teamId,
            String playerName,
            int kills,
            int deaths,
            int assists,
            int xpEarned)
    {
        jdbcTemplate.update(
                """
                        INSERT INTO match_participant_results (
                            match_id,
                            player_id,
                            team_id,
                            player_name_snapshot,
                            kills,
                            deaths,
                            assists,
                            xp_earned
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                matchId,
                playerId,
                teamId,
                playerName,
                kills,
                deaths,
                assists,
                xpEarned
        );
    }

}
