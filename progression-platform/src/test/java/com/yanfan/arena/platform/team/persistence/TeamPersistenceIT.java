package com.yanfan.arena.platform.team.persistence;

import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamStatus;
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

// Verify the team migration and JPA mapping against a real MySQL instance.
@SpringBootTest
@Testcontainers
class TeamPersistenceIT {

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
    TeamRepository teamRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;


    @Test
    void flywayMigrationIsApplied() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = 1",
                Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void draftTeamPersistsWithDefaults() {
        Team team = new Team();
        team.setName("ArenaForce");
        team.setMode(ArenaMode.THREE_VS_THREE);

        Team saved = teamRepository.saveAndFlush(team);

        assertThat(saved.getTeamId()).isPositive();
        assertThat(saved.getStatus()).isEqualTo(TeamStatus.DRAFT);
        assertThat(saved.getRating()).isNull();
        assertThat(saved.getMatchesPlayed()).isZero();
        assertThat(saved.getWins()).isZero();
        assertThat(saved.getLosses()).isZero();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void teamNamesAreUniqueCaseInsensitivelyWithinMode() {
        Team first = new Team();
        first.setName("ArenaTeam");
        first.setMode(ArenaMode.THREE_VS_THREE);
        teamRepository.saveAndFlush(first);

        Team duplicate = new Team();
        duplicate.setName("arenateam");
        duplicate.setMode(ArenaMode.THREE_VS_THREE);

        assertThatThrownBy(() -> teamRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }


}
