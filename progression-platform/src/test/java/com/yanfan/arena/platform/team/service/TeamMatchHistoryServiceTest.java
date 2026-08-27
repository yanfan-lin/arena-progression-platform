package com.yanfan.arena.platform.team.service;

import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.match.persistence.repository.MatchTeamResultRepository;
import com.yanfan.arena.platform.team.persistence.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Verify unknown teams are rejected before match history is queried
@ExtendWith(MockitoExtension.class)
class TeamMatchHistoryServiceTest {

    @Mock
    TeamRepository teamRepository;

    @Mock
    MatchTeamResultRepository matchTeamResultRepository;

    @InjectMocks
    TeamMatchHistoryService teamMatchHistoryService;

    @Test
    void getHistoryRejectsUnknownTeam() {

        Long teamId = 10L;

        when(teamRepository.existsById(teamId))
                .thenReturn(false);

        assertThatThrownBy(() ->
                teamMatchHistoryService.getHistory(teamId, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Team not found");

        // Stop before querying match history when the team does not exist
        verifyNoInteractions(matchTeamResultRepository);
    }

}
