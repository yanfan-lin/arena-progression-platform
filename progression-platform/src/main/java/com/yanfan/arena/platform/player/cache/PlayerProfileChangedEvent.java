package com.yanfan.arena.platform.player.cache;

// Remove the cached player profile after a database update
public record PlayerProfileChangedEvent(Long playerId) {
}
