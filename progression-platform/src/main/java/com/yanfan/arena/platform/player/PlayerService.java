package com.yanfan.arena.platform.player;

import com.yanfan.arena.platform.common.ConflictException;
import com.yanfan.arena.platform.common.ResourceNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// Player lifecycle operations
@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    @Autowired
    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
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


}
