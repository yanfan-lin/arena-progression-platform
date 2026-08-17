package com.yanfan.arena.platform.player.persistence;

import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.player.domain.PlayerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Verify the Flyway schema and JPA mapping against a real MySQL instance.
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

    @Test
    void consistentLevelAndXpAreAccepted() {
        jdbcTemplate.update(
                "INSERT INTO players (display_name, status, total_xp, level, created_at, updated_at) "
                        + "VALUES (?, 'ACTIVE', ?, ?, NOW(6), NOW(6))",
                "ConsistentLevelPlayer", 2500L, 3);

        Integer level = jdbcTemplate.queryForObject(
                "SELECT level FROM players WHERE display_name = ?",
                Integer.class, "ConsistentLevelPlayer");

        assertThat(level).isEqualTo(3);
    }

    // Verify that MySQL rejects players whose level
    // does not match the constraint: 1 + FLOOR(total_xp / 1000).
    @Test
    void inconsistentLevelAndXpAreRejected() {
        assertThatThrownBy(() ->
                jdbcTemplate.update(
                        "INSERT INTO players (display_name, status, total_xp, level, created_at, updated_at) "
                                + "VALUES (?, 'ACTIVE', ?, ?, NOW(6), NOW(6))",
                        "InconsistentLevelPlayer", 2500L, 2))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("chk_players_level_matches_xp");
    }

}
