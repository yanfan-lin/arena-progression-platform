package com.yanfan.arena.platform.match;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// Immutable match stats and XP snapshot of one player
// in an accepted match
@Entity
@Table(name = "match_participant_results")
public class MatchParticipantResult {

    @EmbeddedId
    private MatchParticipantResultId id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "player_name_snapshot", nullable = false, length = 30)
    private String playerNameSnapshot;

    @Column(nullable = false)
    private int kills;

    @Column(nullable = false)
    private int deaths;

    @Column(nullable = false)
    private int assists;

    @Column(name = "xp_earned", nullable = false)
    private int xpEarned;

    protected MatchParticipantResult() {
    }

    public MatchParticipantResult(
            MatchParticipantResultId id,
            Long teamId,
            String playerNameSnapshot,
            int kills,
            int deaths,
            int assists,
            int xpEarned) {
        this.id = id;
        this.teamId = teamId;
        this.playerNameSnapshot = playerNameSnapshot;
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.xpEarned = xpEarned;
    }

    public MatchParticipantResultId getId() {
        return id;
    }

    public Long getTeamId() {
        return teamId;
    }

    public String getPlayerNameSnapshot() {
        return playerNameSnapshot;
    }

    public int getKills() {
        return kills;
    }

    public int getDeaths() {
        return deaths;
    }

    public int getAssists() {
        return assists;
    }

    public int getXpEarned() {
        return xpEarned;
    }

}
