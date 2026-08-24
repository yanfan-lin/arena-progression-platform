package com.yanfan.arena.platform.leaderboard.api;

import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardScore;
import com.yanfan.arena.platform.team.domain.Team;

// Represent one ranked team and its current win-loss record
public record TeamLeaderboardEntryResponse(
        long rank,
        Long teamId,
        String teamName,
        int rating,
        int wins,
        int losses,
        double winRate)
{
    public static TeamLeaderboardEntryResponse from(long rank, Team team) {

        long winRateScore = TeamLeaderboardScore.calculate(
                TeamLeaderboardMetric.WIN_RATE,
                team.getRating(),
                team.getWins(),
                team.getMatchesPlayed()
        );

        return new TeamLeaderboardEntryResponse(
                rank,
                team.getTeamId(),
                team.getName(),
                team.getRating(),
                team.getWins(),
                team.getLosses(),
                winRateScore / 100.0
        );
    }

}
