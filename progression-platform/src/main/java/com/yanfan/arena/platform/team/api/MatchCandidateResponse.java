package com.yanfan.arena.platform.team.api;

import com.yanfan.arena.platform.team.domain.ArenaMode;

import java.time.Instant;
import java.util.List;

// Represent an active team with a locked roster for simulator matches
public record MatchCandidateResponse(
        Long teamId,
        ArenaMode mode,
        Instant activatedAt,
        List<Long> playerIds)
{

}