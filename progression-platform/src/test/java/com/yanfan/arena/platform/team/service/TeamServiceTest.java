package com.yanfan.arena.platform.team.service;

import com.yanfan.arena.platform.error.ConflictException;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.leaderboard.redis.TeamLeaderboardChangedEvent;
import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.player.domain.PlayerStatus;
import com.yanfan.arena.platform.player.persistence.PlayerRepository;
import com.yanfan.arena.platform.team.api.CreateTeamRequest;
import com.yanfan.arena.platform.team.api.ReplaceRosterRequest;
import com.yanfan.arena.platform.team.api.TeamResponse;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamMember;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import com.yanfan.arena.platform.team.persistence.TeamMemberRepository;
import com.yanfan.arena.platform.team.persistence.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Verify team lifecycle, roster, and activation rules.
@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    TeamRepository teamRepository;

    @Mock
    TeamMemberRepository teamMemberRepository;

    @Mock
    PlayerRepository playerRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    Clock clock;

    @InjectMocks
    TeamService teamService;


    @Test
    void createTranslatesDatabaseDuplicateToConflict() {

        when(teamRepository.existsByModeAndNameIgnoreCase(ArenaMode.THREE_VS_THREE, "ExampleTeam"))
                .thenReturn(false);

        when(teamRepository.saveAndFlush(any(Team.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        CreateTeamRequest request = new CreateTeamRequest(
                "ExampleTeam",
                ArenaMode.THREE_VS_THREE);

        assertThatThrownBy(() -> teamService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getThrowsNotFoundForUnknownTeam() {

        when(teamRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.get(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void replaceRosterReplacesMembers() {

        Team team = new Team();
        team.setName("ExampleTeam");
        team.setMode(ArenaMode.THREE_VS_THREE);

        Player player1 = new Player();
        player1.setDisplayName("PlayerOne");
        Player player2 = new Player();
        player2.setDisplayName("PlayerTwo");

        when(teamRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(team));

        when(playerRepository.findAllById(List.of(10L, 11L)))
                .thenReturn(List.of(player1, player2));

        ReplaceRosterRequest request =
                new ReplaceRosterRequest(List.of(10L, 11L));

        TeamResponse response = teamService.replaceRoster(1L, request);

        verify(teamMemberRepository)
                .deleteByTeamId(1L);
        verify(teamMemberRepository, org.mockito.Mockito.times(2))
                .save(any(TeamMember.class));

        assertThat(response.name())
                .isEqualTo("ExampleTeam");
    }

    @Test
    void replaceRosterRejectsNonDraftTeam() {

        Team team = new Team();
        team.setStatus(TeamStatus.ACTIVE);

        when(teamRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(team));

        ReplaceRosterRequest request =
                new ReplaceRosterRequest(List.of(10L));

        assertThatThrownBy(() -> teamService.replaceRoster(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only draft teams");
    }

    @Test
    void replaceRosterRejectsMissingPlayer() {

        Team team = new Team();

        when(teamRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(team));

        Player player1 = new Player();
        player1.setDisplayName("PlayerOne");

        when(playerRepository.findAllById(List.of(10L, 11L)))
                .thenReturn(List.of(player1));

        ReplaceRosterRequest request =
                new ReplaceRosterRequest(List.of(10L, 11L));

        assertThatThrownBy(() -> teamService.replaceRoster(1L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("do not exist");
    }

    @Test
    void replaceRosterRejectsRetiredPlayer() {

        Team team = new Team();

        when(teamRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(team));

        Player retired = new Player();
        retired.setDisplayName("RetiredPlayer");
        retired.setStatus(PlayerStatus.RETIRED);

        when(playerRepository.findAllById(List.of(10L)))
                .thenReturn(List.of(retired));

        ReplaceRosterRequest request =
                new ReplaceRosterRequest(List.of(10L));

        assertThatThrownBy(() -> teamService.replaceRoster(1L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Retired players");

    }

    @Test
    void activateUpdatesTeamAndPublishesLeaderboardEvent() {

        Team team = new Team();

        team.setTeamId(1L);
        team.setName("ExampleTeam");
        team.setMode(ArenaMode.THREE_VS_THREE);

        when(teamRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(team));

        when(teamMemberRepository.findByTeamId(1L))
                .thenReturn(List.of(
                        member(1L, 10L),
                        member(1L, 11L),
                        member(1L, 12L)));

        when(playerRepository.findAllByIdForUpdate(List.of(10L, 11L, 12L)))
                .thenReturn(List.of(new Player(), new Player(), new Player()));

        when(teamMemberRepository.countActiveMemberships(anyCollection(), eq(ArenaMode.THREE_VS_THREE)))
                .thenReturn(0L);

        when(clock.instant())
                .thenReturn(Instant.parse("2026-08-11T00:00:00Z"));

        when(teamRepository.saveAndFlush(any(Team.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TeamResponse response = teamService.activate(1L);

        assertThat(response.status())
                .isEqualTo(TeamStatus.ACTIVE);

        assertThat(response.rating())
                .isEqualTo(1000);

        assertThat(team.getActivatedAt())
                .isEqualTo(Instant.parse("2026-08-11T00:00:00Z"));

        verify(eventPublisher).publishEvent(
                new TeamLeaderboardChangedEvent(List.of(1L)));
    }

    @Test
    void activateRejectsIncompleteRoster() {

        Team team = new Team();
        team.setMode(ArenaMode.FIVE_VS_FIVE);

        when(teamRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(team));

        when(teamMemberRepository.findByTeamId(1L))
                .thenReturn(List.of(member(1L, 10L), member(1L, 11L)));

        assertThatThrownBy(() -> teamService.activate(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("exactly 5");

    }

    @Test
    void activateRejectsPlayerAlreadyInActiveTeam() {

        Team team = new Team();
        team.setMode(ArenaMode.THREE_VS_THREE);

        when(teamRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(team));

        when(teamMemberRepository.findByTeamId(1L))
                .thenReturn(List.of(
                        member(1L, 10L),
                        member(1L, 11L),
                        member(1L, 12L)));

        when(playerRepository.findAllByIdForUpdate(List.of(10L, 11L, 12L)))
                .thenReturn(List.of(new Player(), new Player(), new Player()));

        when(teamMemberRepository.countActiveMemberships(anyCollection(), eq(ArenaMode.THREE_VS_THREE)))
                .thenReturn(1L);

        assertThatThrownBy(() -> teamService.activate(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already on an active team");

    }

    @Test
    void activateRejectsNonDraftTeam() {

        Team team = new Team();
        team.setStatus(TeamStatus.ACTIVE);

        when(teamRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(team));

        assertThatThrownBy(() -> teamService.activate(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only draft teams");
    }

    @Test
    void retireUpdatesTeamAndPublishesLeaderboardEvent() {

        Team team = new Team();

        team.setTeamId(1L);
        team.setName("ExampleTeam");
        team.setMode(ArenaMode.THREE_VS_THREE);

        when(teamRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(team));

        when(teamMemberRepository.findByTeamId(1L))
                .thenReturn(List.of(
                        member(1L, 10L),
                        member(1L, 11L),
                        member(1L, 12L)));

        when(clock.instant())
                .thenReturn(Instant.parse("2026-08-11T00:00:00Z"));

        when(teamRepository.saveAndFlush(any(Team.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TeamResponse response = teamService.retire(1L);

        assertThat(response.status())
                .isEqualTo(TeamStatus.RETIRED);

        assertThat(team.getRetiredAt())
                .isEqualTo(Instant.parse("2026-08-11T00:00:00Z"));

        assertThat(response.playerIds())
                .containsExactly(10L, 11L, 12L);

        verify(eventPublisher).publishEvent(
                new TeamLeaderboardChangedEvent(List.of(1L)));
    }

    @Test
    void retireRejectsAlreadyRetiredTeam() {

        Team team = new Team();
        team.setStatus(TeamStatus.RETIRED);

        when(teamRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(team));

        assertThatThrownBy(() -> teamService.retire(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already retired");
    }

    private TeamMember member(Long teamId, Long playerId) {

        TeamMember member = new TeamMember();

        member.setTeamId(teamId);
        member.setPlayerId(playerId);

        return member;
    }

}
