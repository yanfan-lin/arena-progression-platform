package com.yanfan.arena.simulator.simulation;

// Represent the current state of a continuous match simulation run
public enum SimulationRunState {

    RUNNING,

    // Pause generation until at least two eligible arena teams are available
    WAITING_FOR_TEAMS,

    STOPPING,

    COMPLETED,

    STOPPED,

    FAILED

}
