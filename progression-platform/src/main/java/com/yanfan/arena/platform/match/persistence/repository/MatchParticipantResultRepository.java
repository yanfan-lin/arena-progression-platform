package com.yanfan.arena.platform.match.persistence.repository;

import com.yanfan.arena.platform.match.persistence.entity.MatchParticipantResult;
import com.yanfan.arena.platform.match.persistence.entity.MatchParticipantResultId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

// Database access for match participant result snapshots
public interface MatchParticipantResultRepository extends JpaRepository<MatchParticipantResult, MatchParticipantResultId> {

    // Load only the affected player IDs for one committed match
    @Query("SELECT r.id.playerId FROM MatchParticipantResult r WHERE r.id.matchId = :matchId")
    List<Long> findPlayerIdsByMatchId(String matchId);

    // Load one match's participant snapshots in ascending team ID and player ID order
    @Query("""
            SELECT result
            FROM MatchParticipantResult result
            WHERE result.id.matchId = :matchId
            ORDER BY result.teamId, result.id.playerId
            """)
    List<MatchParticipantResult> findAllByMatchId(String matchId);

    // Load one player's complete match history and player stats by playerID
    @Query(
            value = """
                    SELECT mr.matchId AS matchId,
                           mr.mode AS mode,
                           mr.winningTeamId AS winningTeamId,
                           mr.completedAt AS completedAt,
                           mpr.id.playerId AS playerId,
                           mpr.playerNameSnapshot AS playerName,
                           mpr.teamId AS teamId,
                           mtr.teamNameSnapshot AS teamName,
                           mtr.ratingBefore AS ratingBefore,
                           mtr.ratingChange AS ratingChange,
                           mtr.ratingAfter AS ratingAfter,
                           mpr.kills AS kills,
                           mpr.deaths AS deaths,
                           mpr.assists AS assists,
                           mpr.xpEarned AS xpEarned
                    FROM MatchResult mr,
                         MatchTeamResult mtr,
                         MatchParticipantResult mpr
                    WHERE mpr.id.playerId = :playerId
                      AND mr.matchId = mpr.id.matchId
                      AND mtr.id.matchId = mpr.id.matchId
                      AND mtr.id.teamId = mpr.teamId
                    ORDER BY mr.completedAt DESC,
                             mr.matchId DESC
                    """,
            // Count all matching matches for calculating page totals
            countQuery = """
                    SELECT COUNT(mpr)
                    FROM MatchParticipantResult mpr
                    WHERE mpr.id.playerId = :playerId
                    """
    )
    Page<PlayerMatchHistoryProjection> findHistoryByPlayerId(Long playerId, Pageable pageable);


}
