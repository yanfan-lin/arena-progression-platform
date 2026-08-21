package com.yanfan.arena.platform.player.service;

import com.yanfan.arena.platform.api.PageResponse;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.match.persistence.repository.MatchParticipantResultRepository;
import com.yanfan.arena.platform.player.api.PlayerMatchHistoryResponse;
import com.yanfan.arena.platform.player.persistence.PlayerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Read player match history from stored match results
@Service
public class PlayerMatchHistoryService {
    private final PlayerRepository playerRepository;

    private final MatchParticipantResultRepository matchParticipantResultRepository;

    @Autowired
    public PlayerMatchHistoryService(PlayerRepository playerRepository,
                                     MatchParticipantResultRepository matchParticipantResultRepository)
    {
        this.playerRepository = playerRepository;
        this.matchParticipantResultRepository = matchParticipantResultRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<PlayerMatchHistoryResponse> getHistory(Long playerId, int page, int size) {
        // Separate an unknown player from an existing player with no matches
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException(
                    "PLAYER_NOT_FOUND",
                    "Player not found"
            );
        }

        PageRequest pageRequest = PageRequest.of(page, size);

        // Convert stored query results into API history entries
        Page<PlayerMatchHistoryResponse> history =
                matchParticipantResultRepository
                        .findHistoryByPlayerId(playerId, pageRequest)
                        .map(PlayerMatchHistoryResponse::from);

        return PageResponse.from(history);
    }


}
