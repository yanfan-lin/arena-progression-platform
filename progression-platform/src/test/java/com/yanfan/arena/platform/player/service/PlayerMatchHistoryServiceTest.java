package com.yanfan.arena.platform.player.service;

import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.match.persistence.repository.MatchParticipantResultRepository;
import com.yanfan.arena.platform.player.persistence.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Verify unknown players are rejected before match history is queried
@ExtendWith(MockitoExtension.class)
class PlayerMatchHistoryServiceTest {

    @Mock
    PlayerRepository playerRepository;

    @Mock
    MatchParticipantResultRepository matchParticipantResultRepository;

    @InjectMocks
    PlayerMatchHistoryService playerMatchHistoryService;

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
