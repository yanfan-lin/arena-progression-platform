package com.yanfan.arena.platform.leaderboard.redis;

import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMember;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static com.yanfan.arena.platform.test.IntegrationTestContainers.*;
import static org.assertj.core.api.Assertions.assertThat;


// Verify Redis leaderboard scores after committed team updates and retirement.
@SpringBootTest
@Testcontainers
class TeamLeaderboardRedisIT {

    private static final long TEAM_ID = 42L;

    private static final ArenaMode MODE = ArenaMode.THREE_VS_THREE;

    private static final String MEMBER = TeamLeaderboardMember.fromTeamId(TEAM_ID);

    // MySQL is needed because Spring loads the whole application
    @Container
    static final MySQLContainer MYSQL = mysqlContainer();

    @Container
    static final GenericContainer<?> REDIS = redisContainer();

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registerMySqlProperties(registry, MYSQL);
        registerRedisProperties(registry, REDIS);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUpTeam() {

        jdbcTemplate.update("DELETE FROM teams");

        clearLeaderboards();

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
                            activated_at
                        )
                        VALUES (?, 'Example Team', ?, 'ACTIVE',
                                1500, 10, 6, 4, 0, 0, 0,
                                NOW(6), NOW(6), NOW(6))
                        """,
                TEAM_ID,
                MODE.name()
        );
    }

    @Test
    void updatesReplacesAndRemovesLeaderboardScores() {

        // Store the active team's initial scores in Redis
        publishTeamChange();

        assertThat(score(TeamLeaderboardMetric.RATING))
                .isEqualTo(1500.0);

        assertThat(score(TeamLeaderboardMetric.WINS))
                .isEqualTo(6.0);

        assertThat(score(TeamLeaderboardMetric.WIN_RATE))
                .isEqualTo(6000.0);

        // Commit new team stats and replace its Redis scores
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                            UPDATE teams
                            SET rating = 1516,
                                matches_played = 11,
                                wins = 7,
                                losses = 4,
                                updated_at = NOW(6)
                            WHERE team_id = ?
                            """,
                    TEAM_ID
            );

            eventPublisher.publishEvent(
                    new TeamLeaderboardChangedEvent(List.of(TEAM_ID))
            );
        });

        assertThat(score(TeamLeaderboardMetric.RATING))
                .isEqualTo(1516.0);

        assertThat(score(TeamLeaderboardMetric.WINS))
                .isEqualTo(7.0);

        assertThat(score(TeamLeaderboardMetric.WIN_RATE))
                .isEqualTo(6363.0);

        assertThat(redisTemplate.opsForZSet()
                .size(TeamLeaderboardKey.from(MODE, TeamLeaderboardMetric.RATING)))
                .isEqualTo(1L);

        // Retire the team and remove its scores from every leaderboard
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                            UPDATE teams
                            SET status = 'RETIRED',
                                retired_at = NOW(6),
                                updated_at = NOW(6)
                            WHERE team_id = ?
                            """,
                    TEAM_ID
            );

            eventPublisher.publishEvent(
                    new TeamLeaderboardChangedEvent(List.of(TEAM_ID))
            );
        });

        assertThat(score(TeamLeaderboardMetric.RATING))
                .isNull();

        assertThat(score(TeamLeaderboardMetric.WINS))
                .isNull();

        assertThat(score(TeamLeaderboardMetric.WIN_RATE))
                .isNull();
    }

    // Publish inside a transaction so the listener runs after its commit
    private void publishTeamChange() {
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(
                        new TeamLeaderboardChangedEvent(List.of(TEAM_ID))
                )
        );
    }

    private Double score(TeamLeaderboardMetric metric) {
        return redisTemplate.opsForZSet().score(
                TeamLeaderboardKey.from(MODE, metric),
                MEMBER);
    }

    private void clearLeaderboards() {
        for (TeamLeaderboardMetric metric : TeamLeaderboardMetric.values()) {
            redisTemplate.delete(TeamLeaderboardKey.from(MODE, metric));
        }
    }

}
