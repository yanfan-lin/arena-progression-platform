package com.yanfan.arena.platform.player;

import com.yanfan.arena.platform.common.ConflictException;
import com.yanfan.arena.platform.common.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

// Player lifecycle operations
@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    private final Clock clock;


    @Autowired
    public PlayerService(PlayerRepository playerRepository, Clock clock) {
        this.playerRepository = playerRepository;
        this.clock = clock;
    }

    @Transactional
    public PlayerResponse create(CreatePlayerRequest request) {
        String displayName = request.getDisplayName().trim();

        // Database enforces case-insensitive uniqueness
        if (playerRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new ConflictException("PLAYER_NAME_TAKEN",
                    "Display name already exists");
        }

        Player player = new Player();
        player.setDisplayName(displayName);

        return PlayerResponse.from(playerRepository.save(player));
    }

    public PlayerResponse get(Long playerId) {
        return playerRepository.findById(playerId)
                .map(PlayerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("PLAYER_NOT_FOUND",
                        "Player not found"));
    }

    @Transactional
    public PlayerResponse retire(Long playerId) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PLAYER_NOT_FOUND",
                        "Player not found"));

        if (player.getStatus() == PlayerStatus.RETIRED) {
            throw new ConflictException("PLAYER_RETIRED", "Player is already retired");
        }

        // A player who is in an active 3V3 or 5V5 arena team can not retire,
        // and this check is added in the team lifecycle operations
        player.retire(clock.instant());

        return PlayerResponse.from(playerRepository.save(player));
    }


}
