package com.yanfan.arena.platform.match.api;

import com.yanfan.arena.platform.team.domain.ArenaMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Represent one accepted match using stored results
public record MatchDetailsResponse(
        UUID matchId,
        ArenaMode mode,
        long winningTeamId,
        int contractVersion,
        Instant completedAt,
        List<MatchTeamResponse> teams,
        List<MatchParticipantResponse> participants)
{
    // Copy the lists before Java assigns the record fields,
    // keeping the response immutable
    public MatchDetailsResponse {
        teams = List.copyOf(teams);
        participants = List.copyOf(participants);
    }

}
