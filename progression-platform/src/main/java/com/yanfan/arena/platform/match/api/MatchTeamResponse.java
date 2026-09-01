package com.yanfan.arena.platform.match.api;

import com.yanfan.arena.platform.match.persistence.entity.MatchTeamResult;

// Represent one team's match result using stored match values.
public record MatchTeamResponse(
        Long teamId,
        String teamName,
        MatchOutcome outcome,
        int ratingBefore,
        int ratingChange,
        int ratingAfter)
{

    public static MatchTeamResponse from(
            MatchTeamResult result,
            long winningTeamId)
    {

        Long teamId = result.getId().getTeamId();

        MatchOutcome outcome = MatchOutcome.forTeam(teamId, winningTeamId);

        return new MatchTeamResponse(
                teamId,
                result.getTeamNameSnapshot(),
                outcome,
                result.getRatingBefore(),
                result.getRatingChange(),
                result.getRatingAfter()
        );
    }

}
