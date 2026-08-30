package com.yanfan.arena.simulator.simulation.run;

import com.yanfan.arena.contract.MatchMode;

import java.time.Instant;
import java.util.UUID;

// Return the progress of the current or most recent match simulation run
public record SimulationRunResponse(

        UUID runId,

        MatchMode mode,

        SimulationRunState state,

        long intervalMs,

        int maxMatches,

        // Count only matches acknowledged by Kafka
        int publishedMatches,

        UUID lastEventId,

        UUID lastMatchId,

        Instant startedAt,

        Instant endedAt,

        String lastError) {

}
