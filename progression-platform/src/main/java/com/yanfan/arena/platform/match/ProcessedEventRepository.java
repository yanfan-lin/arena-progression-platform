package com.yanfan.arena.platform.match;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Database access for accepted match events.
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    // Used by the duplicate check:
    // a new event with an existing match ID will not be processed
    boolean existsByMatchId(String matchId);

    // Find the committed record by its match ID
    Optional<ProcessedEvent> findByMatchId(String matchId);

}
