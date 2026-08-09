package com.yanfan.arena.platform.team;

import java.time.Instant;

// API representation of a team
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
        Instant updatedAt
) {
    public static TeamResponse from(Team team) {
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
                team.getUpdatedAt());
    }
}