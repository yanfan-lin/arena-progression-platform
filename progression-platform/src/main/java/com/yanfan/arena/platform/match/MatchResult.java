package com.yanfan.arena.platform.match;

import com.yanfan.arena.platform.team.ArenaMode;
import jakarta.persistence.*;

import java.time.Instant;

// Immutable snapshot of one accepted arena match.
@Entity
@Table(name = "matches")
public class MatchResult {

    @Id
    @Column(name = "match_id", nullable = false, length = 36)
    private String matchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ArenaMode mode;

    @Column(name = "winning_team_id", nullable = false)
    private long winningTeamId;

    @Column(name = "contract_version", nullable = false)
    private int contractVersion;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    protected MatchResult() {
    }

    public MatchResult(String matchId,
                       ArenaMode mode,
                       long winningTeamId,
                       int contractVersion,
                       Instant completedAt
    ) {
        this.matchId = matchId;
        this.mode = mode;
        this.winningTeamId = winningTeamId;
        this.contractVersion = contractVersion;
        this.completedAt = completedAt;
    }

    public String getMatchId() {
        return matchId;
    }

    public ArenaMode getMode() {
        return mode;
    }

    public long getWinningTeamId() {
        return winningTeamId;
    }

    public int getContractVersion() {
        return contractVersion;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

}
