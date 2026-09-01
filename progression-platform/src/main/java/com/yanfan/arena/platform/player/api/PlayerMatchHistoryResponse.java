package com.yanfan.arena.platform.player.api;

import com.yanfan.arena.platform.match.api.MatchOutcome;
import com.yanfan.arena.platform.match.persistence.repository.PlayerMatchHistoryProjection;
import com.yanfan.arena.platform.team.domain.ArenaMode;

import java.time.Instant;
import java.util.UUID;

// Represent one player's stored progression and stats from a match
public record PlayerMatchHistoryResponse(
        UUID matchId,
        ArenaMode mode,
        Instant completedAt,
        Long playerId,
        String playerName,
        Long teamId,
        String teamName,
        MatchOutcome outcome,
        int ratingBefore,
        int ratingChange,
        int ratingAfter,
        int kills,
        int deaths,
        int assists,
    int xpEarned)
{
    public static PlayerMatchHistoryResponse from(PlayerMatchHistoryProjection result) {
        MatchOutcome outcome = MatchOutcome.forTeam(
                result.getTeamId(),
                result.getWinningTeamId()
        );

        return new PlayerMatchHistoryResponse(
                UUID.fromString(result.getMatchId()),
                result.getMode(),
                result.getCompletedAt(),
                result.getPlayerId(),
                result.getPlayerName(),
                result.getTeamId(),
                result.getTeamName(),
                outcome,
                result.getRatingBefore(),
                result.getRatingChange(),
                result.getRatingAfter(),
                result.getKills(),
                result.getDeaths(),
                result.getAssists(),
                result.getXpEarned()
        );
    }


}
