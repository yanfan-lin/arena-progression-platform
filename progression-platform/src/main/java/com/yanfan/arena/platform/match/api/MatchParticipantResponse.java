package com.yanfan.arena.platform.match.api;

import com.yanfan.arena.platform.match.persistence.entity.MatchParticipantResult;

// Represent one player's result using stored match values
public record MatchParticipantResponse(
        Long playerId,
        Long teamId,
        String playerName,
        int kills,
        int deaths,
        int assists,
        int xpEarned)
{

    public static MatchParticipantResponse from(MatchParticipantResult result) {
        return new MatchParticipantResponse(
                result.getId().getPlayerId(),
                result.getTeamId(),
                result.getPlayerNameSnapshot(),
                result.getKills(),
                result.getDeaths(),
                result.getAssists(),
                result.getXpEarned()
        );
    }

}
