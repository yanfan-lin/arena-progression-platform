package com.yanfan.arena.platform.leaderboard.redis;

import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.team.domain.ArenaMode;

import java.util.Locale;

// Build one Redis key for each arena mode and ranking metric
public final class TeamLeaderboardKey {

    private static final String KEY_PREFIX = "arena:leaderboard:team:";

    private TeamLeaderboardKey() {

    }

    // Build the live key for one mode and ranking metric
    // so reads and updates use the same sorted set
    public static String from(ArenaMode mode, TeamLeaderboardMetric metric) {
        return KEY_PREFIX
                + mode.name().toLowerCase(Locale.ROOT)
                + ":"
                + metric.name().toLowerCase(Locale.ROOT);
    }

    // Build a unique key for one rebuild,
    // so that unfinished data will not replace the live leaderboard
    public static String temporaryKey(
            ArenaMode mode,
            TeamLeaderboardMetric metric,
            String rebuildId)
    {
        return KEY_PREFIX
                + "rebuild:"
                + rebuildId
                + ":"
                + mode.name().toLowerCase(Locale.ROOT)
                + ":"
                + metric.name().toLowerCase(Locale.ROOT);
    }

}
