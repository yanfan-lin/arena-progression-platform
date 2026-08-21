package com.yanfan.arena.platform.match.persistence.repository;

import com.yanfan.arena.platform.match.persistence.entity.MatchTeamResult;
import com.yanfan.arena.platform.match.persistence.entity.MatchTeamResultId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

// Database access for match team result snapshots
public interface MatchTeamResultRepository extends JpaRepository<MatchTeamResult, MatchTeamResultId> {

    // Load only the affected team IDs for one committed match
    @Query("SELECT r.id.teamId FROM MatchTeamResult r WHERE r.id.matchId = :matchId")
    List<Long> findTeamIdsByMatchId(String matchId);

    // Load one match's team snapshots in ascending team ID order
    @Query("""
              SELECT result
              FROM MatchTeamResult result
              WHERE result.id.matchId = :matchId
              ORDER BY result.id.teamId
            """)
    List<MatchTeamResult> findAllByMatchId(String matchId);

    // Load one team's paginated match history with rating and K/D/A
    @Query(
            value = """
                    SELECT mr.matchId AS matchId,
                           mr.mode AS mode,
                           mr.winningTeamId AS winningTeamId,
                           mr.completedAt AS completedAt,
                           mtr.id.teamId AS teamId,
                           mtr.teamNameSnapshot AS teamName,
                           mtr.ratingBefore AS ratingBefore,
                           mtr.ratingChange AS ratingChange,
                           mtr.ratingAfter AS ratingAfter,
                           SUM(mpr.kills) AS kills,
                           SUM(mpr.deaths) AS deaths,
                           SUM(mpr.assists) AS assists
                    FROM MatchResult mr,
                         MatchTeamResult mtr,
                         MatchParticipantResult mpr
                    WHERE mtr.id.teamId = :teamId
                      AND mr.matchId = mtr.id.matchId
                      AND mpr.id.matchId = mtr.id.matchId
                      AND mpr.teamId = mtr.id.teamId
                    GROUP BY  mr.matchId,
                              mr.mode,
                              mr.winningTeamId,
                              mr.completedAt,
                              mtr.id.teamId,
                              mtr.teamNameSnapshot,
                              mtr.ratingBefore,
                              mtr.ratingChange,
                              mtr.ratingAfter
                    ORDER BY mr.completedAt DESC, mr.matchId DESC
                    """,
            // Count matching team results for Spring to calculate page totals
            countQuery = """
                    SELECT COUNT(mtr)
                    FROM MatchTeamResult mtr
                    WHERE mtr.id.teamId = :teamId
                    """
    )
    Page<TeamMatchHistoryProjection> findHistoryByTeamId(Long teamId, Pageable pageable);


}
