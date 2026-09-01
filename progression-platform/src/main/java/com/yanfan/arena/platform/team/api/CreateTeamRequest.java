package com.yanfan.arena.platform.team.api;

import com.yanfan.arena.platform.team.domain.ArenaMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Accept data for creating a team.
public record CreateTeamRequest(
        @NotBlank(message = "name is required")
        @Size(max = 50, message = "name must be at most 50 characters")
        String name,

        @NotNull(message = "mode is required")
        ArenaMode mode)
{
    public CreateTeamRequest {
        // Trim the name before validation checks its length
        name = name == null ? null : name.trim();
    }

}
