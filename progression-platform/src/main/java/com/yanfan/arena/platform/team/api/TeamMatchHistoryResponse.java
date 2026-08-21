package com.yanfan.arena.platform.team.api;

import com.yanfan.arena.platform.match.api.MatchOutcome;
import com.yanfan.arena.platform.match.persistence.repository.TeamMatchHistoryProjection;
import com.yanfan.arena.platform.team.domain.ArenaMode;

import java.time.Instant;
import java.util.UUID;

// Represent one team's rating and stats from match history
public record TeamMatchHistoryResponse(
        UUID matchId,
        ArenaMode mode,
        Instant completedAt,
        Long teamId,
        String teamName,
        MatchOutcome outcome,
        int ratingBefore,
        int ratingChange,
        int ratingAfter,
        long kills,
        long deaths,
        long assists)
{
    public static TeamMatchHistoryResponse from(TeamMatchHistoryProjection result) {
        // Determine the team's match outcome
        MatchOutcome outcome =
                result.getTeamId() == result.getWinningTeamId()
                        ? MatchOutcome.WIN
                        : MatchOutcome.LOSS;

        return new TeamMatchHistoryResponse(
                UUID.fromString(result.getMatchId()),
                result.getMode(),
                result.getCompletedAt(),
                result.getTeamId(),
                result.getTeamName(),
                outcome,
                result.getRatingBefore(),
                result.getRatingChange(),
                result.getRatingAfter(),
                result.getKills(),
                result.getDeaths(),
                result.getAssists()
        );
    }

}
