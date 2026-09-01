package com.yanfan.arena.platform.match.service;

import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.match.api.MatchDetailsResponse;
import com.yanfan.arena.platform.match.api.MatchParticipantResponse;
import com.yanfan.arena.platform.match.api.MatchTeamResponse;
import com.yanfan.arena.platform.match.persistence.entity.MatchResult;
import com.yanfan.arena.platform.match.persistence.repository.MatchParticipantResultRepository;
import com.yanfan.arena.platform.match.persistence.repository.MatchResultRepository;
import com.yanfan.arena.platform.match.persistence.repository.MatchTeamResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// Read stored match snapshots and map them to API responses.
@Service
public class MatchQueryService {

    private final MatchResultRepository matchResultRepository;

    private final MatchTeamResultRepository matchTeamResultRepository;

    private final MatchParticipantResultRepository matchParticipantResultRepository;

    @Autowired
    public MatchQueryService(
            MatchResultRepository matchResultRepository,
            MatchTeamResultRepository matchTeamResultRepository,
            MatchParticipantResultRepository matchParticipantResultRepository)
    {
        this.matchResultRepository = matchResultRepository;
        this.matchTeamResultRepository = matchTeamResultRepository;
        this.matchParticipantResultRepository = matchParticipantResultRepository;
    }

    // Return one match with its stored team and participant snapshots
    @Transactional(readOnly = true)
    public MatchDetailsResponse get(UUID matchId) {

        String storedMatchId = matchId.toString();

        MatchResult match = matchResultRepository.findById(storedMatchId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATCH_NOT_FOUND",
                        "Match not found"
                ));

        // Convert team snapshots into API responses
        List<MatchTeamResponse> teams =
                matchTeamResultRepository.findAllByMatchId(storedMatchId)
                        .stream()
                        .map(result -> MatchTeamResponse.from(
                                result,
                                match.getWinningTeamId()))
                        .toList();

        // Convert participant snapshots into API responses
        List<MatchParticipantResponse> participants =
                matchParticipantResultRepository.findAllByMatchId(storedMatchId)
                        .stream()
                        .map(MatchParticipantResponse::from)
                        .toList();

        return new MatchDetailsResponse(
                matchId,
                match.getMode(),
                match.getWinningTeamId(),
                match.getContractVersion(),
                match.getCompletedAt(),
                teams,
                participants);
    }

}
