package com.yanfan.arena.platform.match.persistence.repository;

import com.yanfan.arena.platform.team.domain.ArenaMode;

import java.time.Instant;

// Combine stored match, team, and participant result values
// in one player-history query
public interface PlayerMatchHistoryProjection {

    String getMatchId();

    ArenaMode getMode();

    long getWinningTeamId();

    Instant getCompletedAt();

    Long getPlayerId();

    String getPlayerName();

    Long getTeamId();

    String getTeamName();

    int getRatingBefore();

    int getRatingChange();

    int getRatingAfter();

    int getKills();

    int getDeaths();

    int getAssists();

    int getXpEarned();

}