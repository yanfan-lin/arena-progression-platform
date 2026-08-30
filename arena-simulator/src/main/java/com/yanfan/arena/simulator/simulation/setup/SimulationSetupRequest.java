package com.yanfan.arena.simulator.simulation.setup;

import com.yanfan.arena.contract.MatchMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Request enough teams and players to prepare one arena mode for simulation
public record SimulationSetupRequest(
        @NotNull MatchMode mode,
        @Min(2) @Max(100) int targetTeamCount) {

}
