package com.yanfan.arena.simulator.client;

import java.util.List;

// Represent a platform team and its roster without depending on platform code.
public record MatchCandidateResponse(
        Long teamId,
        List<Long> playerIds) {

}
