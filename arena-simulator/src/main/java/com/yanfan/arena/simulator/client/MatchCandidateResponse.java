package com.yanfan.arena.simulator.client;

import com.yanfan.arena.contract.MatchMode;

import java.time.Instant;
import java.util.List;

// Represent a platform team and its roster without
// depending on platform's code
public record MatchCandidateResponse (
        Long teamId,
        MatchMode mode,
        Instant activatedAt,
        List<Long> playerIds) {

}
