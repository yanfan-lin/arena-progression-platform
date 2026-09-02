package com.yanfan.arena.platform.team.domain;

import jakarta.persistence.*;

import java.time.Instant;

// Represent a player's membership in a team.
@Entity
@Table(name = "team_members", uniqueConstraints =
@UniqueConstraint(name = "uk_team_members_team_player", columnNames = {"team_id", "player_id"}))
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;


    @PrePersist
    void onCreate() {
        this.addedAt = Instant.now();
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

}
