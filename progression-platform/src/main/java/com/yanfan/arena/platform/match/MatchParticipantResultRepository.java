package com.yanfan.arena.platform.match;

import org.springframework.data.jpa.repository.JpaRepository;

// Database access for match participant result snapshots
public interface MatchParticipantResultRepository extends JpaRepository<MatchParticipantResult, MatchParticipantResultId> {

}
