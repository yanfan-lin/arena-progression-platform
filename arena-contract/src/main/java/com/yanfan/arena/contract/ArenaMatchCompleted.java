package com.yanfan.arena.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Versioned completed-match event shared by the simulator and platform
public record ArenaMatchCompleted(
        @NotNull Integer contractVersion,
        @NotNull UUID eventId,
        @NotNull UUID matchId,
        @NotNull MatchMode mode,
        @NotNull Instant completedAt,
        @Min(1) long winnerTeamId,
        @NotNull @Size(min = 2, max = 2) List<@NotNull @Valid Team> teams)
{
    public static final int CONTRACT_VERSION = 1;

    // One participating team and its match roster
    public record Team(
            @Min(1) long teamId,
            @NotNull @Size(min = 1, max = 5) List<@NotNull @Valid Player> participants) {

    }

    // One player's kills, deaths, and assists for the match
    public record Player(
            @Min(1) long playerId,
            @NotNull @Min(0) Integer kills,
            @NotNull @Min(0) Integer deaths,
            @NotNull @Min(0) Integer assists) {

    }

}
