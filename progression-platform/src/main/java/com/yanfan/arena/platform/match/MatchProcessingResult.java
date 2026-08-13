package com.yanfan.arena.platform.match;

import com.yanfan.arena.platform.team.ArenaMode;

import java.time.Instant;
import java.util.List;

// Result of processing one match event.
// PROCESSED means the match was applied and stored,
// DUPLICATE means it was ignored.
public record MatchProcessingResult(
        MatchProcessingOutcome outcome,
        ProcessedMatch processed
) {

    // Determine whether a match event is new or redelivered/reused
    public enum MatchProcessingOutcome {
        PROCESSED,
        DUPLICATE
    }

    public static MatchProcessingResult duplicate() {
        return new MatchProcessingResult(MatchProcessingOutcome.DUPLICATE, null);
    }

    // Immutable record of what the processor stored or changed
    // for Redis to rebuild caches and leaderboards
    public record ProcessedMatch(
            String matchId,
            String eventId,
            ArenaMode mode,
            long winningTeamId,
            int contractVersion,
            Instant completedAt,
            List<TeamResult> teamResults,
            List<PlayerResult> playerResults
    ) {
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
