package com.yanfan.arena.platform.player.api;

import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.player.domain.PlayerStatus;

import java.time.Instant;

// API representation of a player
public record PlayerResponse(
        Long playerId,
        String displayName,
        PlayerStatus status,
        long totalXp,
        int level,
        Instant createdAt,
        Instant updatedAt) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(
                player.getPlayerId(),
                player.getDisplayName(),
                player.getStatus(),
                player.getTotalXp(),
                player.getLevel(),
                player.getCreatedAt(),
                player.getUpdatedAt()
        );
    }

}
