package com.yanfan.arena.platform.match.persistence.repository;

import com.yanfan.arena.platform.match.persistence.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Database access for accepted match events.
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    // Find the committed record by its match ID
    Optional<ProcessedEvent> findByMatchId(String matchId);

}
