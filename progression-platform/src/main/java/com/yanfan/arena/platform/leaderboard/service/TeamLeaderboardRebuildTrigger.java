package com.yanfan.arena.platform.leaderboard.service;

import com.yanfan.arena.platform.leaderboard.redis.TeamLeaderboardProjectionHealth;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Start a Redis leaderboard rebuild at startup and retry failed attempts
@Component
@ConditionalOnProperty(
        name = "arena.leaderboard.rebuild-enabled",
        havingValue = "true")
public class TeamLeaderboardRebuildTrigger {

    private final TeamLeaderboardRebuildService rebuildService;

    private final TeamLeaderboardProjectionHealth projectionHealth;

    public TeamLeaderboardRebuildTrigger(
            TeamLeaderboardRebuildService rebuildService,
            TeamLeaderboardProjectionHealth projectionHealth)
    {
        this.rebuildService = rebuildService;
        this.projectionHealth = projectionHealth;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        rebuildIfUnhealthy();
    }

    @Scheduled(fixedDelayString = "${arena.leaderboard.rebuild-retry-delay}")
    public void retryFailedRebuild() {
        rebuildIfUnhealthy();
    }

    private void rebuildIfUnhealthy() {
        if (!projectionHealth.isHealthy()) {
            rebuildService.rebuild();
        }
    }
}
