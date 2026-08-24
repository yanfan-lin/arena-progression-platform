package com.yanfan.arena.platform.infrastructure;

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

import static com.yanfan.arena.platform.test.IntegrationTestContainers.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class RedisConnectivityIT {

    // Start throwaway MySQL and Redis containers for this test,
    // nothing here touches the locally running Compose stack
    @Container
    static final MySQLContainer MYSQL = mysqlContainer();

    @Container
    static final GenericContainer<?> REDIS = redisContainer();

    // Point the app's connection settings at the throwaway containers
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registerMySqlProperties(registry, MYSQL);
        registerRedisProperties(registry, REDIS);
    }

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    void redisReadsAndWrites() {
        redisTemplate.opsForValue().set("key", "ok");

        assertThat(redisTemplate.opsForValue().get("key"))
                .isEqualTo("ok");
    }


}
