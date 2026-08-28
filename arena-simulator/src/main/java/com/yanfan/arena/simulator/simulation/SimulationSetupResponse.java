package com.yanfan.arena.simulator.simulation;

import com.yanfan.arena.contract.MatchMode;

// Summarize the teams and players found and created during simulation setup
public record SimulationSetupResponse(
        MatchMode mode,
        int targetTeamCount,
        int existingTeamCount,
        int createdTeamCount,
        int createdPlayerCount) {

}
