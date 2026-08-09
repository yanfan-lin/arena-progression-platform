package com.yanfan.arena.platform.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Request body for creating a team
public class CreateTeamRequest {

    @NotBlank(message = "name is required")
    @Size(max = 50, message = "name must be at most 50 characters")
    private String name;

    @NotNull(message = "mode is required")
    private ArenaMode mode;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        // Remove extra spaces before validation runs,
        // ex: name like "   Team A" is checked as "Team A"
        this.name = name == null ? null : name.trim();
    }

    public ArenaMode getMode() {
        return mode;
    }

    public void setMode(ArenaMode mode) {
        this.mode = mode;
    }


}
