package com.yanfan.arena.platform.match.service;

import com.yanfan.arena.platform.api.PageResponse;
import com.yanfan.arena.platform.match.api.MatchOutcome;
import com.yanfan.arena.platform.player.api.PlayerMatchHistoryResponse;
import com.yanfan.arena.platform.player.service.PlayerMatchHistoryService;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.api.TeamMatchHistoryResponse;
import com.yanfan.arena.platform.team.service.TeamMatchHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static com.yanfan.arena.platform.match.processing.MatchProcessorTestData.*;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.mysqlContainer;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.registerMySqlProperties;
import static org.assertj.core.api.Assertions.assertThat;

// Verify match history services against MySQL
@SpringBootTest
@Testcontainers
class MatchHistoryIT {

    @Container
    static final MySQLContainer MYSQL = mysqlContainer();

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registerMySqlProperties(registry, MYSQL);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlayerMatchHistoryService playerMatchHistoryService;

    @Autowired
    TeamMatchHistoryService teamMatchHistoryService;

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

        PageResponse<PlayerMatchHistoryResponse> playerHistory =
                playerMatchHistoryService.getHistory(101L, 0, 1);

        assertThat(playerHistory.totalElements())
                .isEqualTo(2);
        assertThat(playerHistory.totalPages())
                .isEqualTo(2);

        PlayerMatchHistoryResponse playerResult =
                playerHistory.content().getFirst();

        assertThat(playerResult.matchId())
                .isEqualTo(UUID.fromString(higherMatchId));
        assertThat(playerResult.playerName())
                .isEqualTo("Stored Alpha One New");
        assertThat(playerResult.teamName())
                .isEqualTo("Stored Alpha New");
        assertThat(playerResult.outcome())
                .isEqualTo(MatchOutcome.LOSS);
        assertThat(playerResult.ratingBefore())
                .isEqualTo(1016);
        assertThat(playerResult.xpEarned())
                .isEqualTo(100);

        PageResponse<TeamMatchHistoryResponse> teamHistory =
                teamMatchHistoryService.getHistory(1L, 0, 1);

        assertThat(teamHistory.totalElements())
                .isEqualTo(2);
        assertThat(teamHistory.totalPages())
                .isEqualTo(2);

        TeamMatchHistoryResponse teamResult =
                teamHistory.content().getFirst();

        assertThat(teamResult.matchId())
                .isEqualTo(UUID.fromString(higherMatchId));
        assertThat(teamResult.teamName())
                .isEqualTo("Stored Alpha New");
        assertThat(teamResult.outcome())
                .isEqualTo(MatchOutcome.LOSS);
        assertThat(teamResult.ratingChange())
                .isEqualTo(-15);
        assertThat(teamResult.kills())
                .isEqualTo(7);
        assertThat(teamResult.deaths())
                .isEqualTo(5);
        assertThat(teamResult.assists())
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
