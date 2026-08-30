package com.yanfan.arena.simulator.simulation;

import com.yanfan.arena.contract.MatchMode;

// Signal that match generation must wait for more eligible teams.
public class InsufficientTeamsException extends RuntimeException {

    public InsufficientTeamsException(MatchMode mode) {

        super("At least two teams are required for mode: " + mode);
    }

}
