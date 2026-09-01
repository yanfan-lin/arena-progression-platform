package com.yanfan.arena.platform.leaderboard.redis;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

// Expose Redis leaderboard status and rebuild times
@Component
public class TeamLeaderboardProjectionInfo implements InfoContributor {

    private final TeamLeaderboardProjectionHealth projectionHealth;

    public TeamLeaderboardProjectionInfo(TeamLeaderboardProjectionHealth projectionHealth) {
        this.projectionHealth = projectionHealth;
    }

    @Override
    public void contribute(Info.Builder builder) {

        // Show "never" before the first failure
        String lastFailureAt =
                projectionHealth.getLastFailureAt()
                        .map(Instant::toString)
                        .orElse("never");

        // Show "never" before the first successful rebuild
        String lastSuccessfulRebuildAt =
                projectionHealth.getLastSuccessfulRebuildAt()
                        .map(Instant::toString)
                        .orElse("never");

        builder.withDetail(
                "teamLeaderboardProjection",
                Map.of(
                        "status", projectionHealth.getStatus().name(),
                        "lastFailureAt", lastFailureAt,
                        "lastSuccessfulRebuildAt", lastSuccessfulRebuildAt
                )
        );
    }

}
