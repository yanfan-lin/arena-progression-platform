package com.yanfan.arena.platform.match;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

// Composite primary key of one player's row in a match
@Embeddable
public class MatchParticipantResultId implements Serializable {

    @Column(name = "match_id", nullable = false, length = 36)
    private String matchId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    protected MatchParticipantResultId() {
    }

    public MatchParticipantResultId(String matchId, Long playerId) {
        this.matchId = matchId;
        this.playerId = playerId;
    }

    public String getMatchId() {
        return matchId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MatchParticipantResultId that)) {
            return false;
        }
        return Objects.equals(matchId, that.matchId) && Objects.equals(playerId, that.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, playerId);
    }

}
