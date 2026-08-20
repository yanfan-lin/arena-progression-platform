package com.yanfan.arena.platform.match.service;

import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.match.api.MatchDetailsResponse;
import com.yanfan.arena.platform.match.api.MatchOutcome;
import com.yanfan.arena.platform.match.api.MatchParticipantResponse;
import com.yanfan.arena.platform.match.api.MatchTeamResponse;
import com.yanfan.arena.platform.match.persistence.entity.*;
import com.yanfan.arena.platform.match.persistence.repository.MatchParticipantResultRepository;
import com.yanfan.arena.platform.match.persistence.repository.MatchResultRepository;
import com.yanfan.arena.platform.match.persistence.repository.MatchTeamResultRepository;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Verify match details are mapped and
// unknown matches stop before result rows are queried
@ExtendWith(MockitoExtension.class)
class MatchQueryServiceTest {

    @Mock
    MatchResultRepository matchResultRepository;

    @Mock
    MatchTeamResultRepository matchTeamResultRepository;

    @Mock
    MatchParticipantResultRepository matchParticipantResultRepository;

    @InjectMocks
    MatchQueryService matchQueryService;

    @Test
    void getReturnsStoredMatchSnapshot() {

        UUID matchId = UUID.fromString(
                "11111111-1111-1111-1111-111111111111");

        String storedMatchId = matchId.toString();

        Instant completedAt = Instant.parse("2026-08-19T12:00:00Z");

        // Build the committed match snapshot stored by the processor
        MatchResult match = new MatchResult(
                storedMatchId,
                ArenaMode.THREE_VS_THREE,
                10L,
                1,
                completedAt
        );

        MatchTeamResult winningTeam = new MatchTeamResult(
                new MatchTeamResultId(storedMatchId, 10L),
                "Stored Winners",
                1000,
                16,
                1016
        );

        MatchTeamResult losingTeam = new MatchTeamResult(
                new MatchTeamResultId(storedMatchId, 20L),
                "Stored Losers",
                1000,
                -16,
                984
        );

        MatchParticipantResult participant =
                new MatchParticipantResult(
                        new MatchParticipantResultId(
                                storedMatchId,
                                1L
                        ),
                        10L,
                        "Stored Player",
                        8,
                        2,
                        5,
                        150
                );

        when(matchResultRepository.findById(storedMatchId))
                .thenReturn(Optional.of(match));

        when(matchTeamResultRepository.findAllByMatchId(storedMatchId))
                .thenReturn(List.of(winningTeam, losingTeam));

        when(matchParticipantResultRepository
                .findAllByMatchId(storedMatchId))
                .thenReturn(List.of(participant));

        MatchDetailsResponse expected = new MatchDetailsResponse(
                matchId,
                ArenaMode.THREE_VS_THREE,
                10L,
                1,
                completedAt,
                List.of(
                        new MatchTeamResponse(
                                10L,
                                "Stored Winners",
                                MatchOutcome.WIN,
                                1000,
                                16,
                                1016),
                        new MatchTeamResponse(
                                20L,
                                "Stored Losers",
                                MatchOutcome.LOSS,
                                1000,
                                -16,
                                984)
                ),
                List.of(
                        new MatchParticipantResponse(
                                1L,
                                10L,
                                "Stored Player",
                                8,
                                2,
                                5,
                                150)
                )
        );

        assertThat(matchQueryService.get(matchId))
                .isEqualTo(expected);
    }

    @Test
    void getRejectsUnknownMatch() {
        UUID matchId = UUID.fromString(
                "99999999-9999-9999-9999-999999999999");

        when(matchResultRepository.findById(matchId.toString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> matchQueryService.get(matchId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Match not found");

        // Stop before reading team and participant results when the match does not exist
        verifyNoInteractions(
                matchTeamResultRepository,
                matchParticipantResultRepository
        );
    }


}
