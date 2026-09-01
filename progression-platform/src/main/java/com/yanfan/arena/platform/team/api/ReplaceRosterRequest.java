package com.yanfan.arena.platform.team.api;

import jakarta.validation.constraints.NotNull;

import java.util.List;

// Accept player IDs for replacing a draft team's roster.
public record ReplaceRosterRequest(
        @NotNull(message = "playerIds is required")
        List<@NotNull Long> playerIds) {

}
