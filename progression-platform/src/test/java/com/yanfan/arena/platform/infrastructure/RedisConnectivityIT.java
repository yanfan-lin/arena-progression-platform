package com.yanfan.arena.platform.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static com.yanfan.arena.platform.test.IntegrationTestContainers.mysqlContainer;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.registerMySqlProperties;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class RedisConnectivityIT {

    // Start throwaway MySQL and Redis containers for this test,
    // nothing here touches the locally running Compose stack
    @Container
    static final MySQLContainer MYSQL = mysqlContainer();

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4.10"))
            .withExposedPorts(6379);

    // Point the app's connection settings at the throwaway containers
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registerMySqlProperties(registry, MYSQL);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
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
