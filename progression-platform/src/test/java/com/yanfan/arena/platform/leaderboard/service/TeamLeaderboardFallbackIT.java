package com.yanfan.arena.platform.leaderboard.service;

import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.leaderboard.api.TeamLeaderboardEntryResponse;
import com.yanfan.arena.platform.leaderboard.api.TeamLeaderboardResponse;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.TeamStatus;
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

import static com.yanfan.arena.platform.test.IntegrationTestContainers.mysqlContainer;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.registerMySqlProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Verify MySQL leaderboard ranking and exact ranks
@SpringBootTest
@Testcontainers
class TeamLeaderboardFallbackIT {

    @Container
    static final MySQLContainer MYSQL = mysqlContainer();

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registerMySqlProperties(registry, MYSQL);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    TeamLeaderboardFallbackService leaderboardService;

    @BeforeEach
    void setUpTeams() {

        jdbcTemplate.update("DELETE FROM teams");

        insertTeam(
                1L,
                "Alpha",
                ArenaMode.THREE_VS_THREE,
                TeamStatus.ACTIVE,
                1500,
                6,
                4
        );

        insertTeam(
                2L,
                "Beta",
                ArenaMode.THREE_VS_THREE,
                TeamStatus.ACTIVE,
                1400,
                8,
                2
        );

        // Teams 2 and 3 tie on wins and win rate
        insertTeam(
                3L,
                "Gamma",
                ArenaMode.THREE_VS_THREE,
                TeamStatus.ACTIVE,
                1500,
                8,
                2
        );

        insertTeam(
                4L,
                "Delta",
                ArenaMode.THREE_VS_THREE,
                TeamStatus.ACTIVE,
                1300,
                1,
                0
        );

        insertTeam(
                7L,
                "No Matches",
                ArenaMode.THREE_VS_THREE,
                TeamStatus.ACTIVE,
                1200,
                0,
                0
        );

        // Retired teams and other arena modes should not enter this leaderboard
        insertTeam(
                5L,
                "Retired",
                ArenaMode.THREE_VS_THREE,
                TeamStatus.RETIRED,
                2000,
                10,
                0
        );

        insertTeam(
                6L,
                "Other Mode",
                ArenaMode.FIVE_VS_FIVE,
                TeamStatus.ACTIVE,
                2500,
                10,
                0
        );
    }

    @Test
    void ordersEveryMetricAndFiltersTeams() {

        TeamLeaderboardResponse rating = leaderboardService.getTop(
                ArenaMode.THREE_VS_THREE,
                TeamLeaderboardMetric.RATING,
                10
        );

        TeamLeaderboardResponse wins = leaderboardService.getTop(
                ArenaMode.THREE_VS_THREE,
                TeamLeaderboardMetric.WINS,
                10
        );

        TeamLeaderboardResponse winRate = leaderboardService.getTop(
                ArenaMode.THREE_VS_THREE,
                TeamLeaderboardMetric.WIN_RATE,
                10
        );

        assertThat(rating.entries())
                .extracting(TeamLeaderboardEntryResponse::teamId)
                .containsExactly(3L, 1L, 2L, 4L, 7L);

        assertThat(wins.entries())
                .extracting(TeamLeaderboardEntryResponse::teamId)
                .containsExactly(3L, 2L, 1L, 4L, 7L);

        assertThat(winRate.entries())
                .extracting(TeamLeaderboardEntryResponse::teamId)
                .containsExactly(4L, 3L, 2L, 1L, 7L);

        assertThat(rating.entries())
                .extracting(TeamLeaderboardEntryResponse::rank)
                .containsExactly(1L, 2L, 3L, 4L, 5L);

        assertThat(winRate.entries().getFirst().winRate())
                .isEqualTo(100.0);
    }

    @Test
    void calculatesExactRankUsingTieRule() {

        TeamLeaderboardEntryResponse result =
                leaderboardService.getRank(
                        2L,
                        TeamLeaderboardMetric.WIN_RATE
                );

        // Delta team ranks first, then the tied team with the larger ID
        assertThat(result.rank())
                .isEqualTo(3L);
    }

    @Test
    void rejectsRankForRetiredTeam() {

        assertThatThrownBy(() ->
                leaderboardService.getRank(5L, TeamLeaderboardMetric.RATING))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Team is not on an active leaderboard");
    }

    private void insertTeam(
            long teamId,
            String name,
            ArenaMode mode,
            TeamStatus status,
            int rating,
            int wins,
            int losses)
    {
        Instant now = Instant.now();

        Timestamp activatedAt = Timestamp.from(now);

        Timestamp retiredAt =
                status == TeamStatus.RETIRED
                        ? Timestamp.from(now)
                        : null;

        jdbcTemplate.update(
                """
                        INSERT INTO teams (
                            team_id,
                            name,
                            mode,
                            status,
                            rating,
                            matches_played,
                            wins,
                            losses,
                            total_kills,
                            total_deaths,
                            total_assists,
                            created_at,
                            updated_at,
                            activated_at,
                            retired_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, NOW(6), NOW(6), ?, ?)
                        """,
                teamId,
                name,
                mode.name(),
                status.name(),
                rating,
                wins + losses,
                wins,
                losses,
                activatedAt,
                retiredAt
        );
    }

}
