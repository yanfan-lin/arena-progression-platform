package com.yanfan.arena.platform.match.persistence.repository;

import com.yanfan.arena.platform.team.domain.ArenaMode;

import java.time.Instant;

// Combine stored match and team values with
// participant stats in one team-history query
public interface TeamMatchHistoryProjection {

    String getMatchId();

    ArenaMode getMode();

    long getWinningTeamId();

    Instant getCompletedAt();

    Long getTeamId();

    String getTeamName();

    int getRatingBefore();

    int getRatingChange();

    int getRatingAfter();

    Long getKills();

    Long getDeaths();

    Long getAssists();

}
