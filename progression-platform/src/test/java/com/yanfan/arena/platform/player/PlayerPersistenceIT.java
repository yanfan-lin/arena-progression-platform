package com.yanfan.arena.platform.player;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Verifies the Flyway schema and JPA mapping against a real MySQL instance.
@SpringBootTest
@Testcontainers
class PlayerPersistenceIT {

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
    PlayerRepository playerRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationIsApplied() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void newPlayerPersistsWithDefaults() {
        Player player = new Player();
        player.setDisplayName("ArenaExamplePlayer");

        Player saved = playerRepository.saveAndFlush(player);

        assertThat(saved.getPlayerId()).isPositive();
        assertThat(saved.getStatus()).isEqualTo(PlayerStatus.ACTIVE);
        assertThat(saved.getTotalXp()).isZero();
        assertThat(saved.getLevel()).isEqualTo(1);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void displayNameIsUniqueCaseInsensitively() {
        Player first = new Player();
        first.setDisplayName("DummyPlayer");

        playerRepository.saveAndFlush(first);

        Player second = new Player();
        second.setDisplayName("dummyPlayer");

        assertThatThrownBy(() -> playerRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }


}