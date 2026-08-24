package com.yanfan.arena.platform.leaderboard;

// Calculate the score used to order the team leaderboard.
public final class TeamLeaderboardScore {

    // Convert winning rate (ex 72.45%) to 7245 and use long to prevent overflow
    private static final long WIN_RATE_SCALE = 10_000L;

    private TeamLeaderboardScore() {

    }

    public static long calculate(
            TeamLeaderboardMetric metric,
            int rating,
            int wins,
            int matchesPlayed)
    {
        return switch (metric) {
            case RATING -> rating;
            case WINS -> wins;
            case WIN_RATE -> calculateWinRate(wins, matchesPlayed);
        };
    }

    private static long calculateWinRate(int wins, int matchesPlayed) {
        if (matchesPlayed == 0) {
            return 0;
        }

        return wins * WIN_RATE_SCALE / matchesPlayed;
    }

}
