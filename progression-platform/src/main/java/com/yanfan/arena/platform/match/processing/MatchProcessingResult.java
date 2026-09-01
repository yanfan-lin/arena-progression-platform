package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.platform.team.domain.ArenaMode;

import java.time.Instant;
import java.util.List;

// Result of processing one match event.
// PROCESSED means the match was applied and stored,
// DUPLICATE means it was ignored.
public record MatchProcessingResult(
        MatchProcessingOutcome outcome,
        ProcessedMatch processed,
        ReconciliationData reconciliation
) {

    // Determine whether a match event is new or redelivered/reused
    public enum MatchProcessingOutcome {
        PROCESSED,
        DUPLICATE
    }

    public static MatchProcessingResult duplicate(ReconciliationData reconciliation) {
        return new MatchProcessingResult(MatchProcessingOutcome.DUPLICATE, null, reconciliation);
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
            List<PlayerResult> playerResults
    ) {
    }

    // Committed identities for a duplicate event
    // Redis later rereads current MySQL values for these teams/players
    public record ReconciliationData(
            String committedEventId,
            String committedMatchId,
            List<Long> teamIds,
            List<Long> playerIds) {
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
