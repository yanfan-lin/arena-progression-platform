package com.yanfan.arena.platform.player;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

// Verify that MySQL rejects players whose level
// does not match the constraint: 1 + FLOOR(total_xp / 1000).
@SpringBootTest
@Testcontainers
class PlayerLevelXpConsistencyIT {

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
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationIsApplied() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '5' AND success = 1",
                Integer.class);

        assertThat(count).isEqualTo(1);
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
