package com.yanfan.arena.simulator.simulation.run;

import com.yanfan.arena.contract.MatchMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Define the mode, delay, and match limit for scheduled match generation.
public record SimulationRunRequest(
        @NotNull
        MatchMode mode,

        // Time delay after one match finishes publishing before the next
        @Min(100)
        long intervalMs,

        @Positive
        int maxMatches) {

}
