package com.yanfan.arena.platform.leaderboard.api;

import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.team.domain.ArenaMode;

import java.util.List;

// Represent the team leaderboard for one arena mode.
public record TeamLeaderboardResponse(
        ArenaMode mode,
        TeamLeaderboardMetric metric,
        List<TeamLeaderboardEntryResponse> entries)
{
    public TeamLeaderboardResponse {
        // Ensure the response entries are immutable after creation
        entries = List.copyOf(entries);
    }

}
