package com.yanfan.arena.platform.leaderboard.service;

import com.yanfan.arena.platform.error.BadRequestException;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.leaderboard.api.TeamLeaderboardEntryResponse;
import com.yanfan.arena.platform.leaderboard.api.TeamLeaderboardResponse;
import com.yanfan.arena.platform.leaderboard.redis.TeamLeaderboardRedisStore;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import com.yanfan.arena.platform.team.persistence.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Verify Redis leaderboard reads and MySQL fallback.
@ExtendWith(MockitoExtension.class)
class TeamLeaderboardServiceTest {

    @Mock
    TeamLeaderboardRedisStore redisStore;

    @Mock
    TeamLeaderboardFallbackService fallbackService;

    @Mock
    TeamRepository teamRepository;

    @InjectMocks
    TeamLeaderboardService teamLeaderboardService;

    // Keep Redis ranking order after loading team details from MySQL
    @Test
    void getTopPreservesRedisOrder() {

        Team first = team(1L, "First");
        Team second = team(2L, "Second");

        when(redisStore.findTopTeamIds(
                ArenaMode.THREE_VS_THREE,
                TeamLeaderboardMetric.RATING,
                10))
                .thenReturn(Optional.of(List.of(2L, 1L)));

        // Return teams in different order
        when(teamRepository.findAllById(List.of(2L, 1L)))
                .thenReturn(List.of(first, second));

        TeamLeaderboardResponse response =
                teamLeaderboardService.getTop(
                        ArenaMode.THREE_VS_THREE,
                        TeamLeaderboardMetric.RATING,
                        10);

        assertThat(response.entries())
                .extracting(TeamLeaderboardEntryResponse::teamId)
                .containsExactly(2L, 1L);

        assertThat(response.entries())
                .extracting(TeamLeaderboardEntryResponse::rank)
                .containsExactly(1L, 2L);

        verifyNoInteractions(fallbackService);
    }

    // Fallback to MySQL when Redis has no usable data
    @Test
    void getTopUsesMySqlWhenRedisHasNoData() {

        Team fallbackTeam = team(1L, "Fallback");

        TeamLeaderboardResponse fallbackResponse =
                new TeamLeaderboardResponse(
                        ArenaMode.THREE_VS_THREE,
                        TeamLeaderboardMetric.WINS,
                        List.of(TeamLeaderboardEntryResponse.from(1L, fallbackTeam))
                );

        when(redisStore.findTopTeamIds(
                ArenaMode.THREE_VS_THREE,
                TeamLeaderboardMetric.WINS,
                10))
                .thenReturn(Optional.empty());

        when(fallbackService.getTop(
                ArenaMode.THREE_VS_THREE,
                TeamLeaderboardMetric.WINS,
                10))
                .thenReturn(fallbackResponse);

        TeamLeaderboardResponse response =
                teamLeaderboardService.getTop(
                        ArenaMode.THREE_VS_THREE,
                        TeamLeaderboardMetric.WINS,
                        10
                );

        assertThat(response)
                .isSameAs(fallbackResponse);

        verifyNoInteractions(teamRepository);
    }

    // Reject unsupported limit sizes before reading Redis or MySQL
    @Test
    void getTopRejectsUnsupportedLimit() {

        assertThatThrownBy(() ->
                teamLeaderboardService.getTop(
                        ArenaMode.THREE_VS_THREE,
                        TeamLeaderboardMetric.WINS,
                        20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Limit must be 10, 30, 50, or 100");

        verifyNoInteractions(redisStore, fallbackService, teamRepository);
    }

    // Combine the Redis rank with the team's current MySQL details
    @Test
    void getRankUsesRedisRank() {

        Team team = team(1L, "Example");

        when(teamRepository.findById(1L))
                .thenReturn(Optional.of(team));

        when(redisStore.findRank(
                ArenaMode.THREE_VS_THREE,
                TeamLeaderboardMetric.WIN_RATE,
                1L
        ))
                .thenReturn(Optional.of(3L));

        TeamLeaderboardEntryResponse response =
                teamLeaderboardService.getRank(
                        1L,
                        TeamLeaderboardMetric.WIN_RATE
                );

        assertThat(response.rank())
                .isEqualTo(3L);

        assertThat(response.teamId())
                .isEqualTo(1L);

        assertThat(response.winRate())
                .isEqualTo(60.0);

        verifyNoInteractions(fallbackService);
    }

    // Use MySQL when Redis has no stored rank for the team
    @Test
    void getRankUsesMySqlWhenRedisHasNoRank() {

        Team fallbackTeam = team(1L, "fallback");

        TeamLeaderboardEntryResponse fallbackResponse =
                TeamLeaderboardEntryResponse.from(3L, fallbackTeam);

        when(teamRepository.findById(1L))
                .thenReturn(Optional.of(fallbackTeam));

        when(redisStore.findRank(
                ArenaMode.THREE_VS_THREE,
                TeamLeaderboardMetric.RATING,
                1L))
                .thenReturn(Optional.empty());

        when(fallbackService.getRank(1L, TeamLeaderboardMetric.RATING))
                .thenReturn(fallbackResponse);

        TeamLeaderboardEntryResponse response =
                teamLeaderboardService.getRank(
                        1L,
                        TeamLeaderboardMetric.RATING);

        assertThat(response)
                .isSameAs(fallbackResponse);
    }

    private Team team(Long teamId, String name) {
        Team team = new Team();

        team.setTeamId(teamId);
        team.setName(name);
        team.setMode(ArenaMode.THREE_VS_THREE);
        team.setStatus(TeamStatus.ACTIVE);
        team.setRating(1500);
        team.setMatchesPlayed(10);
        team.setWins(6);
        team.setLosses(4);

        return team;
    }

}
