package com.yanfan.arena.platform.match;

import org.springframework.data.jpa.repository.JpaRepository;

// Database access for match team result snapshots
public interface MatchTeamResultRepository extends JpaRepository<MatchTeamResult, MatchTeamResultId> {

}
