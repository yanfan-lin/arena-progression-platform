package com.yanfan.arena.simulator.simulation.run;

// Represent the current state of scheduled match generation.
public enum SimulationRunState {

    RUNNING,

    // Pause generation until at least two eligible arena teams are available
    WAITING_FOR_TEAMS,

    STOPPING,

    COMPLETED,

    STOPPED,

    FAILED

}
