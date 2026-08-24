package com.yanfan.arena.platform.leaderboard;

// Format team IDs so Redis can order tied teams by the larger team ID
public final class TeamLeaderboardMember {

    // The team IDs can contain up to 19 digits
    private static final String TEAM_ID_FORMAT = "%019d";

    private TeamLeaderboardMember() {
    }

    public static String fromTeamId(long teamId) {
        return TEAM_ID_FORMAT.formatted(teamId);
    }

    public static long toTeamId(String member) {
        return Long.parseLong(member);
    }

}
