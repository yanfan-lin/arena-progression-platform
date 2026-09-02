package com.yanfan.arena.platform.team.api;

import java.util.List;

// Represent an active team with a locked roster for simulator matches.
public record MatchCandidateResponse(
        Long teamId,
        List<Long> playerIds) {

}