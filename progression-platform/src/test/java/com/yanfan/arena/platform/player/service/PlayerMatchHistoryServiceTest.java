package com.yanfan.arena.platform.player.service;

import com.yanfan.arena.platform.api.PageResponse;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.match.api.MatchOutcome;
import com.yanfan.arena.platform.match.persistence.repository.MatchParticipantResultRepository;
import com.yanfan.arena.platform.match.persistence.repository.PlayerMatchHistoryProjection;
import com.yanfan.arena.platform.player.api.PlayerMatchHistoryResponse;
import com.yanfan.arena.platform.player.persistence.PlayerRepository;
import com.yanfan.arena.platform.team.domain.ArenaMode;
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

// Verify player history mapping, empty history,
// and unknown-player handling
@ExtendWith(MockitoExtension.class)
class PlayerMatchHistoryServiceTest {

    @Mock
    PlayerRepository playerRepository;

    @Mock
    MatchParticipantResultRepository matchParticipantResultRepository;

    @Mock
    PlayerMatchHistoryProjection result;

    @InjectMocks
    PlayerMatchHistoryService playerMatchHistoryService;

    // Get player history request return response correctly
    @Test
    void getHistoryReturnsStoredMatchPage() {

        Long playerId = 7L;

        String matchId = "11111111-1111-1111-1111-111111111111";

        Instant completedAt = Instant.parse("2026-08-19T12:00:00Z");

        // Represent one stored row returned by the joined history query
        when(result.getMatchId())
                .thenReturn(matchId);
        when(result.getMode())
                .thenReturn(ArenaMode.THREE_VS_THREE);
        when(result.getWinningTeamId())
                .thenReturn(10L);
        when(result.getCompletedAt())
                .thenReturn(completedAt);
        when(result.getPlayerId())
                .thenReturn(playerId);
        when(result.getPlayerName())
                .thenReturn("Stored Player");
        when(result.getTeamId())
                .thenReturn(10L);
        when(result.getTeamName())
                .thenReturn("Stored Winners");
        when(result.getRatingBefore())
                .thenReturn(1000);
        when(result.getRatingChange())
                .thenReturn(16);
        when(result.getRatingAfter())
                .thenReturn(1016);
        when(result.getKills())
                .thenReturn(8);
        when(result.getDeaths())
                .thenReturn(2);
        when(result.getAssists())
                .thenReturn(5);
        when(result.getXpEarned())
                .thenReturn(150);

        PageRequest pageRequest = PageRequest.of(1, 2);

        Page<PlayerMatchHistoryProjection> storedPage =
                new PageImpl<>(
                        List.of(result),
                        pageRequest,
                        5
                );

        when(playerRepository.existsById(playerId))
                .thenReturn(true);

        when(matchParticipantResultRepository
                .findHistoryByPlayerId(playerId, pageRequest))
                .thenReturn(storedPage);

        PageResponse<PlayerMatchHistoryResponse> response =
                playerMatchHistoryService.getHistory(playerId, 1, 2);

        PlayerMatchHistoryResponse expected =
                new PlayerMatchHistoryResponse(
                        UUID.fromString(matchId),
                        ArenaMode.THREE_VS_THREE,
                        completedAt,
                        playerId,
                        "Stored Player",
                        10L,
                        "Stored Winners",
                        MatchOutcome.WIN,
                        1000,
                        16,
                        1016,
                        8,
                        2,
                        5,
                        150
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

    // Handles existing player with no matching history
    @Test
    void getHistoryReturnsEmptyPageForExistingPlayer() {

        Long playerId = 7L;

        PageRequest pageRequest = PageRequest.of(0, 20);

        when(playerRepository.existsById(playerId))
                .thenReturn(true);

        when(matchParticipantResultRepository.findHistoryByPlayerId(playerId, pageRequest))
                .thenReturn(Page.empty(pageRequest));

        PageResponse<PlayerMatchHistoryResponse> response =
                playerMatchHistoryService.getHistory(playerId, 0, 20);

        assertThat(response.content())
                .isEmpty();
        assertThat(response.totalElements())
                .isZero();
        assertThat(response.totalPages())
                .isZero();

    }

    @Test
    void getHistoryRejectsUnknownPlayer() {

        Long playerId = 99L;

        when(playerRepository.existsById(playerId))
                .thenReturn(false);

        assertThatThrownBy(() ->
                playerMatchHistoryService.getHistory(playerId, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Player not found");

        // Stop before querying match history when the player does not exist
        verifyNoInteractions(matchParticipantResultRepository);
    }


}
