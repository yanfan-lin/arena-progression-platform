package com.yanfan.arena.platform.match.persistence.repository;

import com.yanfan.arena.platform.match.persistence.entity.MatchTeamResult;
import com.yanfan.arena.platform.match.persistence.entity.MatchTeamResultId;
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


}
