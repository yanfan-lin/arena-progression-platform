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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
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
    TeamMemberRepository teamMemberRepository;

    @Autowired
    PlayerRepository playerRepository;

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

    // Verify the team member migration and repository against a real MySQL instance
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
