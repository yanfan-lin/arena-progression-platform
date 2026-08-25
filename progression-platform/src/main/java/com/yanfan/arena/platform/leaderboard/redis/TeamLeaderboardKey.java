package com.yanfan.arena.platform.leaderboard.redis;

import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.team.domain.ArenaMode;

import java.util.Locale;

// Build one Redis key for each arena mode and ranking metric
public final class TeamLeaderboardKey {

    private static final String KEY_PREFIX = "arena:leaderboard:team:";

    private TeamLeaderboardKey() {

    }

    public static String from(ArenaMode mode, TeamLeaderboardMetric metric) {
        return KEY_PREFIX
                + mode.name().toLowerCase(Locale.ROOT)
                + ":"
                + metric.name().toLowerCase(Locale.ROOT);
    }

}
