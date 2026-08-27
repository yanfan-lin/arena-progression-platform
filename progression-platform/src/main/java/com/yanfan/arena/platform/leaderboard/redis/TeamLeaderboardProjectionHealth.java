package com.yanfan.arena.platform.leaderboard.redis;

import com.yanfan.arena.platform.leaderboard.TeamLeaderboardProjectionStatus;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

import java.util.concurrent.atomic.AtomicReference;

import static com.yanfan.arena.platform.leaderboard.TeamLeaderboardProjectionStatus.*;

// Track Redis leaderboard health status and rebuild times
@Component
public class TeamLeaderboardProjectionHealth {

    private static final Logger log = LoggerFactory.getLogger(TeamLeaderboardProjectionHealth.class);

    // Keep status changes safe when multiple threads run at the same time
    // A startup rebuild is required before Redis can serve leaderboard reads
    private final AtomicReference<TeamLeaderboardProjectionStatus> status =
            new AtomicReference<>(DEGRADED);

    // Keep timestamp updates visible across threads
    private volatile Instant lastFailureAt;

    private volatile Instant lastSuccessfulRebuildAt;

    public boolean isHealthy() {
        return status.get() == HEALTHY;
    }

    // Mark Redis leaderboard data unhealthy and record the failure
    public void markDegraded() {

        lastFailureAt = Instant.now();

        TeamLeaderboardProjectionStatus oldStatus = status.getAndSet(DEGRADED);

        if (oldStatus != DEGRADED) {
            log.warn(
                    "Redis team leaderboard status changed: {} -> {}",
                    oldStatus,
                    DEGRADED);
        }
    }

    // Prevent more than one rebuild from running at the same time
    public boolean beginRebuild() {
        TeamLeaderboardProjectionStatus prevStatus = status.getAndSet(REBUILDING);

        if (prevStatus == REBUILDING) {
            return false;
        }

        log.info(
                "Redis team leaderboard status changed: {} -> {}",
                prevStatus,
                REBUILDING);

        return true;
    }

    // Mark Redis healthy only if no failure changed the rebuild status,
    // compareAndSet() prevents a concurrent failure from being overwritten
    public boolean completeRebuild() {
        if (!status.compareAndSet(REBUILDING, HEALTHY)) {
            return false;
        }

        lastSuccessfulRebuildAt =  Instant.now();

        log.info(
                "Redis team leaderboard status changed: {} -> {}",
                REBUILDING,
                HEALTHY);

        return true;
    }

    public TeamLeaderboardProjectionStatus getStatus() {
        return status.get();
    }

    public Optional<Instant> getLastFailureAt() {
        return Optional.ofNullable(lastFailureAt);
    }

    public Optional<Instant> getLastSuccessfulRebuildAt() {
        return Optional.ofNullable(lastSuccessfulRebuildAt);
    }

}
