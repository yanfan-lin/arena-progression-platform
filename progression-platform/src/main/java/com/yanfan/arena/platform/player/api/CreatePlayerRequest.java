package com.yanfan.arena.platform.player.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Accept data for creating a player.
public record CreatePlayerRequest(
        @NotBlank(message = "displayName is required")
        @Size(max = 30, message = "displayName must be at most 30 characters")
        String displayName)
{
    public CreatePlayerRequest {
        // Trim the name before validation checks its length
        displayName = displayName == null ? null : displayName.trim();
    }

}
