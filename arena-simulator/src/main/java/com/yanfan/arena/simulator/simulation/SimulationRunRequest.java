package com.yanfan.arena.simulator.simulation;

import com.yanfan.arena.contract.MatchMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Define the mode, delay, and match limit for a continuous match simulation run.
public record SimulationRunRequest(
        @NotNull
        MatchMode mode,

        // Time delay after one match finishes publishing before the next
        @Min(100)
        long intervalMs,

        @Positive
        int maxMatches) {

}
