package com.yanfan.arena.platform.match.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

// Durable record of an accepted match event.
// Re-delivered events or reused match IDs will be ignored.
@Entity
@Table(name = "processed_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_processed_events_match_id", columnNames = "match_id"))
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "match_id", nullable = false, length = 36)
    private String matchId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, String matchId) {
        this.eventId = eventId;
        this.matchId = matchId;
    }

    @PrePersist
    void onCreate() {
        this.processedAt = Instant.now();
    }

    public String getMatchId() {
        return matchId;
    }

}
