package com.yanfan.arena.platform.player.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Remove changed player profiles from Redis after MySQL commits
@Component
public class PlayerProfileCacheListener {

    private final PlayerProfileCache playerProfileCache;

    @Autowired
    public PlayerProfileCacheListener(PlayerProfileCache playerProfileCache) {
        this.playerProfileCache = playerProfileCache;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void removeChangedProfile(PlayerProfileChangedEvent event) {
        playerProfileCache.evict(event.playerId());
    }

}
