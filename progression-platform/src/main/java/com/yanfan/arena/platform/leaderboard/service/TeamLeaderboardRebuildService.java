package com.yanfan.arena.platform.leaderboard.service;

import com.yanfan.arena.platform.leaderboard.redis.TeamLeaderboardProjectionHealth;
import com.yanfan.arena.platform.leaderboard.redis.TeamLeaderboardRedisStore;
import com.yanfan.arena.platform.player.cache.PlayerProfileCache;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import com.yanfan.arena.platform.team.persistence.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;

// Rebuild Redis team leaderboards from active MySQL teams
@Service
public class TeamLeaderboardRebuildService {

    private static final Logger log = LoggerFactory.getLogger(TeamLeaderboardRebuildService.class);

    private final TeamRepository teamRepository;

    private final TeamLeaderboardRedisStore leaderboardStore;

    private final TeamLeaderboardProjectionHealth projectionHealth;

    private final Lock projectionLock;

    private final PlayerProfileCache playerProfileCache;

    public TeamLeaderboardRebuildService(
            TeamRepository teamRepository,
            TeamLeaderboardRedisStore leaderboardStore,
            TeamLeaderboardProjectionHealth projectionHealth,
            Lock projectionLock,
            PlayerProfileCache playerProfileCache)
    {
        this.teamRepository = teamRepository;
        this.leaderboardStore = leaderboardStore;
        this.projectionHealth = projectionHealth;
        this.projectionLock = projectionLock;
        this.playerProfileCache = playerProfileCache;
    }

    // Rebuild all Redis team leaderboards from the latest MySQL data
    public boolean rebuild() {
        // Prevent overlapping rebuilds from replacing each other's temporary data
        if (!projectionHealth.beginRebuild()) {
            return false;
        }

        // Keep this attempt separate from temporary data left by earlier failures
        String rebuildId = UUID.randomUUID().toString();

        // The committed team updates must wait until this rebuild finishes
        projectionLock.lock();

        try {
            // MySQL provides the current state used to rebuild Redis
            List<Team> activeTeams =
                    teamRepository.findAllByStatus(TeamStatus.ACTIVE);

            if (!leaderboardStore.writeTemporaryLeaderboards(activeTeams, rebuildId)) {
                return false;
            }

            if (!leaderboardStore.replaceLiveLeaderboards(rebuildId)) {
                return false;
            }

            // Remove player profiles that may still contain data from before the Redis failure
            if (!playerProfileCache.clearAll()) {
                // Keep recovery retries active until stale player profiles can be removed
                projectionHealth.markDegraded();

                return false;
            }

            return projectionHealth.completeRebuild();
        }
        catch (RuntimeException e) {
            // Keep leaderboard reads on MySQL after unexpected rebuild failure
            projectionHealth.markDegraded();

            log.warn(
                    "Failed to rebuild Redis team leaderboards: rebuildId={} cause={}",
                    rebuildId,
                    e.getClass().getSimpleName());

            return false;
        }
        finally {
            // Remove any temporary keys left by an incomplete rebuild
            leaderboardStore.deleteTemporaryLeaderboards(rebuildId);

            // Allow committed team updates to continue after this attempt
            projectionLock.unlock();
        }
    }

}
