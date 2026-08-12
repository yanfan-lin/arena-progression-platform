package com.yanfan.arena.contract;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

// Version 1 of the wire contract for a completed arena match
// Kafka Producers publish it, then platform consumes and validates it
public record ArenaMatchCompleted(
        @NotNull Integer contractVersion,
        @NotNull String eventId,
        @NotNull String matchId,
        @NotNull MatchMode mode,
        @NotNull Instant completedAt,
        @Min(1) long winnerTeamId,
        @NotNull @Size(min = 2, max = 2) List<@Valid Team> teams) {

    public static final int CONTRACT_VERSION = 1;

    // Participating team with roster for this match
    public record Team(
            @Min(1) long teamId,
            @NotNull @Size(min = 1, max = 5) List<@Valid Player> participants) {

    }

    // Player's statistics(Kills, deaths, and assists) in the match
    public record Player(
            @Min(1) long playerId,
            @Min(0) int kills,
            @Min(0) int deaths,
            @Min(0) int assists) {

    }


}
