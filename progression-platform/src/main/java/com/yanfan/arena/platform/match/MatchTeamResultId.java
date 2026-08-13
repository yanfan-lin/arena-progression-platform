package com.yanfan.arena.platform.match;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

// Composite primary key of one team's row in a match
@Embeddable
public class MatchTeamResultId implements Serializable {

    @Column(name = "match_id", nullable = false, length = 36)
    private String matchId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    public MatchTeamResultId() {
    }

    public MatchTeamResultId(String matchId, Long teamId) {
        this.matchId = matchId;
        this.teamId = teamId;
    }

    public String getMatchId() {
        return matchId;
    }

    public Long getTeamId() {
        return teamId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof MatchTeamResultId that)) {
            return false;
        }

        // Check whether two keys represent the same row
        return Objects.equals(matchId, that.matchId) && Objects.equals(teamId, that.teamId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, teamId);
    }

}
