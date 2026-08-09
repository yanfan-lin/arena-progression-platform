package com.yanfan.arena.platform.player;

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