package com.yanfan.arena.platform.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

// Database access for match participant result snapshots
public interface MatchParticipantResultRepository extends JpaRepository<MatchParticipantResult, MatchParticipantResultId> {

    // Load only the affected player IDs for one committed match
    @Query("SELECT r.id.playerId FROM MatchParticipantResult r WHERE r.id.matchId = :matchId")
    List<Long> findPlayerIdsByMatchId(String matchId);

}
