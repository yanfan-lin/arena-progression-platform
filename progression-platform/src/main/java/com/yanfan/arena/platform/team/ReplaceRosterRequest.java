package com.yanfan.arena.platform.team;

import jakarta.validation.constraints.NotNull;

import java.util.List;

// Request body for replacing a draft team's roster.
public class ReplaceRosterRequest {

    @NotNull(message = "playerIds is required")
    private List<@NotNull Long> playerIds;

    public List<Long> getPlayerIds() {
        return playerIds;
    }

    public void setPlayerIds(List<Long> playerIds) {
        this.playerIds = playerIds;
    }


}
