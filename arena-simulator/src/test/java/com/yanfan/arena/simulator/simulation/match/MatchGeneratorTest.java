package com.yanfan.arena.simulator.simulation.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.simulator.client.MatchCandidateResponse;
import com.yanfan.arena.simulator.client.PlatformClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// Verify generated matches follow the arena elimination rules.
@ExtendWith(MockitoExtension.class)
class MatchGeneratorTest {

    private static final Instant COMPLETED_AT =
            Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private PlatformClient platformClient;

    private MatchGenerator matchGenerator;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(COMPLETED_AT, ZoneOffset.UTC);

        matchGenerator = new MatchGenerator(platformClient, clock);
    }

    @ParameterizedTest
    @EnumSource(MatchMode.class)
    void generatesValidMatchForEachArenaMode(MatchMode mode) {

        int rosterSize =
                mode == MatchMode.THREE_VS_THREE ? 3 : 5;

        // Provide two active teams with complete locked rosters.
        List<MatchCandidateResponse> candidates = List.of(
                new MatchCandidateResponse(
                        1L,
                        playerIds(1L, rosterSize)),
                new MatchCandidateResponse(
                        2L,
                        playerIds(101L, rosterSize))
        );

        when(platformClient.getMatchCandidates(mode))
                .thenReturn(candidates);

        ArenaMatchCompleted event =
                matchGenerator.generateMatch(mode);

        ArenaMatchCompleted.Team winner = event.teams().get(0);

        ArenaMatchCompleted.Team loser =  event.teams().get(1);

        assertThat(event.mode())
                .isEqualTo(mode);

        assertThat(event.completedAt())
                .isEqualTo(COMPLETED_AT);

        assertThat(event.winnerTeamId())
                .isEqualTo(winner.teamId());

        assertThat(winner.teamId())
                .isNotEqualTo(loser.teamId());

        assertThat(winner.participants())
                .hasSize(rosterSize);

        assertThat(loser.participants())
                .hasSize(rosterSize);

        // The winning team eliminates the entire losing roster
        assertThat(totalKills(winner))
                .isEqualTo(rosterSize);

        assertThat(totalDeaths(loser))
                .isEqualTo(rosterSize);

        assertThat(loser.participants())
                .allMatch(player -> player.deaths() == 1);

        // The loser's kills match the winner's deaths,
        // and at least one winning player survives the match.
        assertThat(totalKills(loser))
                .isEqualTo(totalDeaths(winner));

        assertThat(totalDeaths(winner))
                .isLessThan(rosterSize);

        assertValidAssists(winner);

        assertValidAssists(loser);
    }

    // Build a complete roster for the requested arena mode
    private List<Long> playerIds(long firstPlayerId, int rosterSize) {

        List<Long> playerIds = new ArrayList<>();

        for (int index = 0; index < rosterSize; index++) {
            playerIds.add(firstPlayerId + index);
        }

        return playerIds;
    }

    private int totalKills(ArenaMatchCompleted.Team team) {

        return team.participants().stream()
                .mapToInt(ArenaMatchCompleted.Player::kills)
                .sum();
    }

    private int totalDeaths(ArenaMatchCompleted.Team team) {

        return team.participants().stream()
                .mapToInt(ArenaMatchCompleted.Player::deaths)
                .sum();
    }

    // Ensure assists only come from kills made by teammates
    private void assertValidAssists(ArenaMatchCompleted.Team team) {

        int teamKills = totalKills(team);

        for (ArenaMatchCompleted.Player player : team.participants()) {
            assertThat(player.assists())
                    .isBetween(0, teamKills - player.kills());
        }
    }

}
