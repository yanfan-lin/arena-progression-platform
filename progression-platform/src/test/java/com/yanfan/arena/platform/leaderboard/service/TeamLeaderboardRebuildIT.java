package com.yanfan.arena.platform.leaderboard.service;

import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMember;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.leaderboard.redis.TeamLeaderboardKey;
import com.yanfan.arena.platform.leaderboard.redis.TeamLeaderboardProjectionHealth;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static com.yanfan.arena.platform.test.IntegrationTestContainers.*;
import static org.assertj.core.api.Assertions.assertThat;

// Verify Redis leaderboard rebuilding from MySQL with real containers
@SpringBootTest
@Testcontainers
class TeamLeaderboardRebuildIT {

    private static final long FIRST_TEAM_ID = 25L;

    private static final long SECOND_TEAM_ID = 26L;

    private static final long STALE_TEAM_ID = 99L;

    private static final ArenaMode MODE = ArenaMode.THREE_VS_THREE;

    private static final ArenaMode EMPTY_MODE = ArenaMode.FIVE_VS_FIVE;

    private static final String PROFILE_CACHE_KEY = "player:profile:" + FIRST_TEAM_ID;

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
    TeamLeaderboardRebuildService rebuildService;

    @Autowired
    TeamLeaderboardService leaderboardService;

    @Autowired
    TeamLeaderboardFallbackService fallbackService;

    @Autowired
    TeamLeaderboardProjectionHealth projectionHealth;

    @BeforeEach
    void setUpData() {

        jdbcTemplate.update("DELETE FROM teams");

        // MySQL contains the current team data that Redis must rebuild
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
                        VALUES
                            (?, 'Rebuilt Team', ?, 'ACTIVE',
                             1500, 10, 6, 4, 0, 0, 0,
                             NOW(6), NOW(6), NOW(6)),
                            (?, 'Second Team', ?, 'ACTIVE',
                             1600, 5, 5, 0, 0, 0, 0,
                             NOW(6), NOW(6), NOW(6))
                        """,
                FIRST_TEAM_ID,
                MODE.name(),
                SECOND_TEAM_ID,
                MODE.name()
        );

        String member =
                TeamLeaderboardMember.fromTeamId(FIRST_TEAM_ID);

        String staleMember =
                TeamLeaderboardMember.fromTeamId(STALE_TEAM_ID);

        // Redis starts with incorrect data that the rebuild must replace or remove
        for (TeamLeaderboardMetric metric : TeamLeaderboardMetric.values()) {
            redisTemplate.opsForZSet().add(
                    TeamLeaderboardKey.from(MODE, metric),
                    member,
                    -1);

            redisTemplate.opsForZSet().add(
                    TeamLeaderboardKey.from(EMPTY_MODE, metric),
                    staleMember,
                    9999);
        }

        // Simulate a profile that missed eviction during a Redis failure
        redisTemplate.opsForValue().set(
                PROFILE_CACHE_KEY,
                "stale");
    }

    // Verify a successful rebuild replaces stale Redis data with the latest MySQL data
    @Test
    void rebuildsLeaderboardsAndClearsStaleRedisData() {

        boolean rebuilt = rebuildService.rebuild();

        assertThat(rebuilt)
                .isTrue();

        assertThat(projectionHealth.isHealthy())
                .isTrue();

        assertThat(projectionHealth.getLastSuccessfulRebuildAt())
                .isPresent();

        // Compare Redis results with MySQL for every ranking metric
        for (TeamLeaderboardMetric metric : TeamLeaderboardMetric.values()) {

            String liveKey = TeamLeaderboardKey.from(MODE, metric);

            assertThat(redisTemplate.opsForZSet().size(liveKey))
                    .isEqualTo(2L);

            assertThat(leaderboardService.getTop(MODE, metric, 10))
                    .isEqualTo(fallbackService.getTop(MODE, metric, 10));

            // No active teams means the rebuild must remove the old live key
            assertThat(redisTemplate.hasKey(
                    TeamLeaderboardKey.from(EMPTY_MODE, metric)))
                    .isFalse();
        }

        // Profiles that missed eviction should not survive the rebuild
        assertThat(redisTemplate.hasKey(PROFILE_CACHE_KEY))
                .isFalse();
    }

}
