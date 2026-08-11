package com.yanfan.arena.platform.team;

import java.time.Instant;
import java.util.List;

// API representation of a team including its current roster
public record TeamResponse(
        Long teamId,
        String name,
        ArenaMode mode,
        TeamStatus status,
        Integer rating,
        int matchesPlayed,
        int wins,
        int losses,
        int totalKills,
        int totalDeaths,
        int totalAssists,
        Instant createdAt,
        Instant updatedAt,
        List<Long> playerIds) {

    public static TeamResponse from(Team team, List<Long> playerIds) {
        return new TeamResponse(
                team.getTeamId(),
                team.getName(),
                team.getMode(),
                team.getStatus(),
                team.getRating(),
                team.getMatchesPlayed(),
                team.getWins(),
                team.getLosses(),
                team.getTotalKills(),
                team.getTotalDeaths(),
                team.getTotalAssists(),
                team.getCreatedAt(),
                team.getUpdatedAt(),
                playerIds);
    }
}