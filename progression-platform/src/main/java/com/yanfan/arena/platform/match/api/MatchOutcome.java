package com.yanfan.arena.platform.match.api;

public enum MatchOutcome {
    WIN,
    LOSS;

    // Determine one team's outcome from the stored winner ID
    public static MatchOutcome forTeam(long teamId, long winningTeamId) {
        return teamId == winningTeamId ? WIN : LOSS;
    }
}
