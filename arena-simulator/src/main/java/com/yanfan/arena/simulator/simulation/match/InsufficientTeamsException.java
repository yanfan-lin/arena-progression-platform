package com.yanfan.arena.simulator.simulation.match;

import com.yanfan.arena.contract.MatchMode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Signal insufficient teams for match generation.
@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientTeamsException extends RuntimeException {

    public InsufficientTeamsException(MatchMode mode) {

        super("At least two teams are required for mode: " + mode);
    }

}
