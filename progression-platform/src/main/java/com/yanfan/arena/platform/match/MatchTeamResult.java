package com.yanfan.arena.platform.match;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

// Immutable snapshot of rating changes of one team
// in an accepted match
@Entity
@Table(name = "match_team_results")
public class MatchTeamResult {

    @EmbeddedId
    private MatchTeamResultId id;

    @Column(name = "team_name_snapshot", nullable = false, length = 50)
    private String teamNameSnapshot;

    @Column(name = "rating_before", nullable = false)
    private int ratingBefore;

    @Column(name = "rating_change", nullable = false)
    private int ratingChange;

    @Column(name = "rating_after", nullable = false)
    private int ratingAfter;

    protected MatchTeamResult() {
    }

    public MatchTeamResult(MatchTeamResultId id,
                           String teamNameSnapshot,
                           int ratingBefore,
                           int ratingChange,
                           int ratingAfter) {
        this.id = id;
        this.teamNameSnapshot = teamNameSnapshot;
        this.ratingBefore = ratingBefore;
        this.ratingChange = ratingChange;
        this.ratingAfter = ratingAfter;
    }

    public MatchTeamResultId getId() {
        return id;
    }

    public String getTeamNameSnapshot() {
        return teamNameSnapshot;
    }

    public int getRatingBefore() {
        return ratingBefore;
    }

    public int getRatingChange() {
        return ratingChange;
    }

    public int getRatingAfter() {
        return ratingAfter;
    }

}
