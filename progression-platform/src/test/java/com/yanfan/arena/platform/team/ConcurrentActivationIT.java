package com.yanfan.arena.platform.team;

import com.yanfan.arena.platform.common.ConflictException;
import com.yanfan.arena.platform.player.Player;
import com.yanfan.arena.platform.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

// Prove the concurrent activation guarantee:
// Two teams activating the same player at the same time and ONLY one succeeds
@SpringBootTest
@Testcontainers
class ConcurrentActivationIT {
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
    TeamService teamService;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    TeamMemberRepository teamMemberRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;


    @Test
    void concurrentActivationsOfSamePlayerAllowOnlyOneWinner() throws Exception {
        Long sharedId = savePlayer("SharedPlayer");

        Long teamAPlayer1 = savePlayer("TeamAPlayer1");
        Long teamAPlayer2 = savePlayer("TeamAPlayer2");
        Long teamBPlayer1 = savePlayer("TeamBPlayer1");
        Long teamBPlayer2 = savePlayer("TeamBPlayer2");

        Long teamAId = createDraftTeam("ConcurrentTeamA", sharedId, teamAPlayer1, teamAPlayer2);
        Long teamBId = createDraftTeam("ConcurrentTeamB", sharedId, teamBPlayer1, teamBPlayer2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Throwable> teamAResult = executor.submit(() -> activate(teamAId, ready, start));
        Future<Throwable> teamBResult = executor.submit(() -> activate(teamBId, ready, start));

        Throwable teamAError = null;
        Throwable teamBError = null;
        try {
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            teamAError = teamAResult.get(30, TimeUnit.SECONDS);
            teamBError = teamBResult.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // Exactly one activation succeeds, the other must be a conflict.
        assertThat(teamAError == null ^ teamBError == null).isTrue();

        Throwable loser = teamAError != null ? teamAError : teamBError;

        assertThat(loser).isInstanceOf(ConflictException.class);

        Long activeTeams = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM teams WHERE status = 'ACTIVE' AND team_id IN (?, ?)",
                Long.class, teamAId, teamBId);
        assertThat(activeTeams).isEqualTo(1);

    }

    private Throwable activate(Long teamId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            teamService.activate(teamId);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private Long savePlayer(String displayName) {
        Player player = new Player();
        player.setDisplayName(displayName);
        return playerRepository.saveAndFlush(player).getPlayerId();
    }

    private Long createDraftTeam(String name, Long... playerIds) {
        Team team = new Team();
        team.setName(name);
        team.setMode(ArenaMode.THREE_VS_THREE);
        Long teamId = teamRepository.saveAndFlush(team).getTeamId();

        for (Long playerId : playerIds) {
            TeamMember member = new TeamMember();
            member.setTeamId(teamId);
            member.setPlayerId(playerId);
            teamMemberRepository.saveAndFlush(member);
        }
        return teamId;
    }


}
