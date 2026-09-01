package com.yanfan.arena.platform.player.domain;

import jakarta.persistence.*;

import java.time.Instant;

// Represent a player account and its progression state
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long playerId;

    @Column(name = "display_name", nullable = false, length = 30)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PlayerStatus status = PlayerStatus.ACTIVE;

    // Cumulative XP; never decreases. Level is derived from it
    @Column(name = "total_xp", nullable = false)
    private long totalXp = 0;

    @Column(nullable = false)
    private int level = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Mark the player as permanently retired
    public void retire(Instant retiredAt) {
        this.status = PlayerStatus.RETIRED;
        this.retiredAt = retiredAt;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public PlayerStatus getStatus() {
        return status;
    }

    public void setStatus(PlayerStatus status) {
        this.status = status;
    }

    public long getTotalXp() {
        return totalXp;
    }

    public void setTotalXp(long totalXp) {
        this.totalXp = totalXp;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

}
