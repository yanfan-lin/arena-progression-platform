package com.yanfan.arena.platform.match;

import org.springframework.data.jpa.repository.JpaRepository;

// Database access for accepted match events.
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    // Used by the duplicate check:
    // a new event with an existing match ID will not be processed
    boolean existsByMatchId(String matchId);

}
