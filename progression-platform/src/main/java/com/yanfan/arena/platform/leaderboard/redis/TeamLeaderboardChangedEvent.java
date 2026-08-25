package com.yanfan.arena.platform.leaderboard.redis;

import java.util.List;

// Identify teams whose Redis leaderboard scores need to be refreshed
public record TeamLeaderboardChangedEvent(List<Long> teamIds) {

    public TeamLeaderboardChangedEvent {
        // Keep the team IDs unchanged until the event is handled
        teamIds = List.copyOf(teamIds);
    }

}
