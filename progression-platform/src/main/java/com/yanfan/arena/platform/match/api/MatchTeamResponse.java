package com.yanfan.arena.platform.match.api;

import com.yanfan.arena.platform.match.persistence.entity.MatchTeamResult;

// Represent one team's match result using stored match values
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
            long winningTeamId) {

        MatchOutcome outcome = result.getId().getTeamId() == winningTeamId
                ? MatchOutcome.WIN
                : MatchOutcome.LOSS;

        return new MatchTeamResponse(
                result.getId().getTeamId(),
                result.getTeamNameSnapshot(),
                outcome,
                result.getRatingBefore(),
                result.getRatingChange(),
                result.getRatingAfter()
        );
    }

}