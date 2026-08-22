package com.yanfan.arena.platform.team.service;

import com.yanfan.arena.platform.api.PageResponse;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.match.api.MatchOutcome;
import com.yanfan.arena.platform.match.persistence.repository.MatchTeamResultRepository;
import com.yanfan.arena.platform.match.persistence.repository.TeamMatchHistoryProjection;
import com.yanfan.arena.platform.team.api.TeamMatchHistoryResponse;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.persistence.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Verify stored team history, team without matching history,
// and unknown-team handling
@ExtendWith(MockitoExtension.class)
class TeamMatchHistoryServiceTest {

    @Mock
    TeamRepository teamRepository;

    @Mock
    MatchTeamResultRepository matchTeamResultRepository;

    @Mock
    TeamMatchHistoryProjection result;

    @InjectMocks
    TeamMatchHistoryService teamMatchHistoryService;

    // Return stored team values with page totals
    @Test
    void getHistoryReturnsStoredMatchPage() {

        Long teamId = 1L;

        String matchId =
                "11111111-1111-1111-1111-111111111111";

        Instant completedAt =
                Instant.parse("2026-08-19T12:00:00Z");

        when(result.getMatchId())
                .thenReturn(matchId);
        when(result.getMode())
                .thenReturn(ArenaMode.THREE_VS_THREE);
        when(result.getWinningTeamId())
                .thenReturn(teamId);
        when(result.getCompletedAt())
                .thenReturn(completedAt);
        when(result.getTeamId())
                .thenReturn(teamId);
        when(result.getTeamName())
                .thenReturn("Stored Winners");
        when(result.getRatingBefore())
                .thenReturn(1000);
        when(result.getRatingChange())
                .thenReturn(16);
        when(result.getRatingAfter())
                .thenReturn(1016);
        when(result.getKills())
                .thenReturn(18L);
        when(result.getDeaths())
                .thenReturn(7L);
        when(result.getAssists())
                .thenReturn(12L);

        PageRequest pageRequest = PageRequest.of(1, 2);

        Page<TeamMatchHistoryProjection> storedPage =
                new PageImpl<>(
                        List.of(result),
                        pageRequest,
                        5
                );

        when(teamRepository.existsById(teamId))
                .thenReturn(true);

        when(matchTeamResultRepository
                .findHistoryByTeamId(teamId, pageRequest))
                .thenReturn(storedPage);

        PageResponse<TeamMatchHistoryResponse> response =
                teamMatchHistoryService.getHistory(teamId, 1, 2);

        TeamMatchHistoryResponse expected =
                new TeamMatchHistoryResponse(
                        UUID.fromString(matchId),
                        ArenaMode.THREE_VS_THREE,
                        completedAt,
                        teamId,
                        "Stored Winners",
                        MatchOutcome.WIN,
                        1000,
                        16,
                        1016,
                        18,
                        7,
                        12
                );

        assertThat(response.content())
                .containsExactly(expected);
        assertThat(response.page())
                .isEqualTo(1);
        assertThat(response.size())
                .isEqualTo(2);
        assertThat(response.totalElements())
                .isEqualTo(5);
        assertThat(response.totalPages())
                .isEqualTo(3);
    }

    @Test
    void getHistoryReturnsEmptyPageForExistingTeam() {

        Long teamId = 10L;

        PageRequest pageRequest = PageRequest.of(0, 20);

        when(teamRepository.existsById(teamId))
                .thenReturn(true);

        when(matchTeamResultRepository
                .findHistoryByTeamId(teamId, pageRequest))
                .thenReturn(Page.empty(pageRequest));

        PageResponse<TeamMatchHistoryResponse> response =
                teamMatchHistoryService.getHistory(teamId, 0, 20);

        assertThat(response.content())
                .isEmpty();
        assertThat(response.totalElements())
                .isZero();
        assertThat(response.totalPages())
                .isZero();
    }

    @Test
    void getHistoryRejectsUnknownTeam() {

        Long teamId = 10L;

        when(teamRepository.existsById(teamId))
                .thenReturn(false);

        assertThatThrownBy(() ->
                teamMatchHistoryService.getHistory(teamId, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Team not found");

        // Stop before querying match history when the team does not exist
        verifyNoInteractions(matchTeamResultRepository);
    }

}
