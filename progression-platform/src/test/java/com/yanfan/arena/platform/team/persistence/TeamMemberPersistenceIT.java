package com.yanfan.arena.platform.team.persistence;

import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.player.persistence.PlayerRepository;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamMember;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

// Verify the team member migration and repository against a real MySQL instance
@SpringBootTest
@Testcontainers
class TeamMemberPersistenceIT {

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
    TeamMemberRepository teamMemberRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;


    @Test
    void flywayMigrationIsApplied() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3' AND success = 1",
                Integer.class
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void memberSavesWithAddedAt() {
        Long teamId = saveTeam("MemberTeam");
        Long playerId = savePlayer("MemberPlayer");

        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setPlayerId(playerId);

        TeamMember savedMember = teamMemberRepository.saveAndFlush(member);

        assertThat(savedMember.getMemberId()).isPositive();
        assertThat(savedMember.getAddedAt()).isNotNull();
    }

    @Test
    void duplicateTeamPlayerPairIsRejected() {
        Long teamId = saveTeam("DuplicateTeam");
        Long playerId = savePlayer("DuplicatePlayer");

        TeamMember first = new TeamMember();
        first.setTeamId(teamId);
        first.setPlayerId(playerId);
        teamMemberRepository.saveAndFlush(first);

        TeamMember second = new TeamMember();
        second.setTeamId(teamId);
        second.setPlayerId(playerId);

        assertThatThrownBy(() -> teamMemberRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void deleteByTeamIdRemovesMembers() {
        Long teamId = saveTeam("DeleteTeam");
        Long playerId = savePlayer("DeletePlayer");

        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setPlayerId(playerId);
        teamMemberRepository.saveAndFlush(member);

        teamMemberRepository.deleteByTeamId(teamId);

        assertThat(teamMemberRepository.findByTeamId(teamId)).isEmpty();
    }

    private Long saveTeam(String teamName) {
        Team team = new Team();
        team.setName(teamName);
        team.setMode(ArenaMode.THREE_VS_THREE);

        return teamRepository.saveAndFlush(team).getTeamId();
    }

    private Long savePlayer(String playerName) {
        Player player = new Player();
        player.setDisplayName(playerName);

        return playerRepository.saveAndFlush(player).getPlayerId();
    }


}
