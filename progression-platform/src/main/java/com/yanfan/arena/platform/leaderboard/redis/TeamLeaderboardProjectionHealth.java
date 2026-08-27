package com.yanfan.arena.platform.leaderboard.redis;

import com.yanfan.arena.platform.leaderboard.TeamLeaderboardProjectionStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

import static com.yanfan.arena.platform.leaderboard.TeamLeaderboardProjectionStatus.*;

// Manage the health status of the Redis team leaderboard.
@Component
public class TeamLeaderboardProjectionHealth {

    // Keep status changes safe when multiple threads run at the same time
    // A startup rebuild is required before Redis can serve leaderboard reads
    private final AtomicReference<TeamLeaderboardProjectionStatus> status =
            new AtomicReference<>(DEGRADED);

    public boolean isHealthy() {
        return status.get() == HEALTHY;
    }

    public boolean isRebuilding() { return status.get() == REBUILDING; }

    public void markDegraded() {
        status.set(DEGRADED);
    }

    // Prevent more than one rebuild from running at the same time
    public boolean beginRebuild() {
        return status.getAndSet(REBUILDING) != REBUILDING;
    }

    // Mark Redis healthy only if no failure changed the rebuild status,
    // checking and updating together prevents a concurrent failure from being overwritten
    public boolean completeRebuild() {
        return status.compareAndSet(REBUILDING, HEALTHY);
    }

}
