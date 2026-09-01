package com.yanfan.arena.platform.team.service;

import com.yanfan.arena.platform.api.PageResponse;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.match.persistence.repository.MatchTeamResultRepository;
import com.yanfan.arena.platform.team.api.TeamMatchHistoryResponse;
import com.yanfan.arena.platform.team.persistence.TeamRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Read team match history from stored match results
@Service
public class TeamMatchHistoryService {

    private final TeamRepository teamRepository;

    private final MatchTeamResultRepository matchTeamResultRepository;

    public TeamMatchHistoryService(TeamRepository teamRepository,
                                   MatchTeamResultRepository matchTeamResultRepository)
    {
        this.teamRepository = teamRepository;
        this.matchTeamResultRepository = matchTeamResultRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeamMatchHistoryResponse> getHistory(Long teamId, int page, int size) {

        // Separate an unknown team from an existing team with no matching history
        if (!teamRepository.existsById(teamId)) {
            throw new ResourceNotFoundException(
                    "TEAM_NOT_FOUND",
                    "Team not found"
            );
        }

        PageRequest pageRequest = PageRequest.of(page, size);

        Page<TeamMatchHistoryResponse> history =
                matchTeamResultRepository
                        .findHistoryByTeamId(teamId, pageRequest)
                        .map(TeamMatchHistoryResponse::from);

        return PageResponse.from(history);
    }

}
