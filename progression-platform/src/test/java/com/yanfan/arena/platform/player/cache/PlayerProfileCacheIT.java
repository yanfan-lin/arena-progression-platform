package com.yanfan.arena.platform.player.cache;

import com.yanfan.arena.platform.player.api.PlayerResponse;
import com.yanfan.arena.platform.player.domain.PlayerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Instant;

import static com.yanfan.arena.platform.test.IntegrationTestContainers.*;
import static org.assertj.core.api.Assertions.assertThat;

// Verify player profile caching against a real Redis container
@Testcontainers
@SpringBootTest
class PlayerProfileCacheIT {

    private static final long PLAYER_ID = 42L;

    private static final String CACHE_KEY = "player:profile:" + PLAYER_ID;

    // Spring loads the whole application, so MySQL is needed
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
    PlayerProfileCache playerProfileCache;

    @Autowired
    StringRedisTemplate redisTemplate;

    @AfterEach
    void clearCache() {
        redisTemplate.delete(CACHE_KEY);
    }

    @Test
    void storesReadsAndEvictsProfileWithTenMinuteTtl() {

        PlayerResponse response = playerResponse();

        playerProfileCache.put(response);

        assertThat(playerProfileCache.find(PLAYER_ID))
                .contains(response);

        // Redis reports the remaining TTL in seconds
        assertThat(redisTemplate.getExpire(CACHE_KEY))
                .isBetween(1L, 600L);

        playerProfileCache.evict(PLAYER_ID);

        assertThat(playerProfileCache.find(PLAYER_ID))
                .isEmpty();
    }

    private PlayerResponse playerResponse() {
        return new PlayerResponse(
                PLAYER_ID,
                "ExamplePlayer",
                PlayerStatus.ACTIVE,
                2_500L,
                3,
                Instant.parse("2026-08-24T00:00:00Z"),
                Instant.parse("2026-08-24T00:05:00Z")
        );
    }

}
