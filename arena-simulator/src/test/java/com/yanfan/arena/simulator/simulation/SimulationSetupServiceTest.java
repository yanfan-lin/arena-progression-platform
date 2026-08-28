package com.yanfan.arena.simulator.simulation;

import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.simulator.client.MatchCandidateResponse;
import com.yanfan.arena.simulator.client.PlatformClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Verify setup creates only missing teams with complete mode-specific rosters
@ExtendWith(MockitoExtension.class)
class SimulationSetupServiceTest {

    @Mock
    PlatformClient client;

    @InjectMocks
    SimulationSetupService simulationSetupService;

    @ParameterizedTest
    @CsvSource({
            "THREE_VS_THREE, 3",
            "FIVE_VS_FIVE, 5"
    })
    void createsMissingTeamWithCompleteRoster(MatchMode mode, int rosterSize) {

        when(client.getMatchCandidates(mode))
                .thenReturn(List.of(candidate(1L, mode)));

        when(client.createPlayer(anyString()))
                .thenReturn(101L, 102L, 103L, 104L, 105L);

        when(client.createTeam(anyString(), eq(mode)))
                .thenReturn(10L);

        SimulationSetupResponse response =
                simulationSetupService.setup(
                        new SimulationSetupRequest(mode, 2)
                );

        assertThat(response)
                .isEqualTo(new SimulationSetupResponse(
                        mode,
                        2,
                        1,
                        1,
                        rosterSize)
                );

        verify(client, times(rosterSize))
                .createPlayer(anyString());

        // The team should receive the complete roster required by the mode
        verify(client).replaceRoster(
                eq(10L),
                argThat(playerIds -> playerIds.size() == rosterSize)
        );

        verify(client).activateTeam(10L);
    }

    @Test
    void skipsCreationWhenTargetAlreadyExists() {

        MatchMode mode = MatchMode.THREE_VS_THREE;

        when(client.getMatchCandidates(mode))
                .thenReturn(List.of(
                        candidate(1L, mode),
                        candidate(2L, mode))
                );

        SimulationSetupResponse response =
                simulationSetupService.setup(
                        new SimulationSetupRequest(mode, 2)
                );

        assertThat(response)
                .isEqualTo(new SimulationSetupResponse(
                        mode,
                        2,
                        2,
                        0,
                        0)
                );

        // The candidate read should be the only platform call
        verify(client).getMatchCandidates(mode);

        verifyNoMoreInteractions(client);
    }

    // Build an existing team and its roster for the requested arena mode
    private MatchCandidateResponse candidate(long teamId, MatchMode mode) {

        // Give each test team a separate range of player IDs
        long firstPlayerId = teamId * 10;

        List<Long> playerIds =
                mode == MatchMode.THREE_VS_THREE
                        ? List.of(
                        firstPlayerId + 1,
                        firstPlayerId + 2,
                        firstPlayerId + 3)
                        : List.of(
                        firstPlayerId + 1,
                        firstPlayerId + 2,
                        firstPlayerId + 3,
                        firstPlayerId + 4,
                        firstPlayerId + 5);

        return new MatchCandidateResponse(
                teamId,
                mode,
                Instant.EPOCH,
                playerIds
        );
    }

}
