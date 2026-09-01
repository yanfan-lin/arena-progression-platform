package com.yanfan.arena.platform.team.persistence;

import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.player.persistence.PlayerRepository;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamMember;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.yanfan.arena.platform.test.IntegrationTestContainers.mysqlContainer;
import static com.yanfan.arena.platform.test.IntegrationTestContainers.registerMySqlProperties;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Verify the team migration and JPA mapping against a real MySQL instance.
@SpringBootTest
@Testcontainers
class TeamPersistenceIT {

    @Container
    static final MySQLContainer MYSQL = mysqlContainer();

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registerMySqlProperties(registry, MYSQL);
    }

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    TeamMemberRepository teamMemberRepository;

    @Autowired
    PlayerRepository playerRepository;

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
