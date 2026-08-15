package com.yanfan.arena.platform.player.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Request body for creating a player
public class CreatePlayerRequest {

    @NotBlank(message = "displayName is required")
    @Size(max = 30, message = "displayName must be at most 30 characters")
    private String displayName;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        // Normalize before Bean validation runs, so @Size checks the value correctly
        this.displayName = displayName == null ? null : displayName.trim();
    }

}
