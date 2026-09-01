package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.platform.team.domain.ArenaMode;

import java.time.Instant;
import java.util.List;

// Report whether a match event was processed or ignored as a duplicate.
public record MatchProcessingResult(MatchProcessingOutcome outcome) {

    // Determine whether a match event is new or redelivered/reused
    public enum MatchProcessingOutcome {
        PROCESSED,
        DUPLICATE
    }

    // Hold calculated match changes for MySQL storage and Redis updates
    public record ProcessedMatch(
            String matchId,
            String eventId,
            ArenaMode mode,
            long winningTeamId,
            int contractVersion,
            Instant completedAt,
            List<TeamResult> teamResults,
            List<PlayerResult> playerResults) {

    }

    // Snapshot of the stats of one participating team after the match
    public record TeamResult(
            long teamId,
            String teamNameSnapshot,
            int ratingBefore,
            int ratingChange,
            int ratingAfter,
            int matchesPlayedAfter,
            int winsAfter,
            int lossesAfter,
            int totalKillsAfter,
            int totalDeathsAfter,
            int totalAssistsAfter) {

    }

    // Snapshot of the state of one participating player after the match
    public record PlayerResult(
            long playerId,
            long teamId,
            String playerNameSnapshot,
            int kills,
            int deaths,
            int assists,
            long xpEarned,
            long totalXpAfter,
            int levelAfter) {

    }

}
