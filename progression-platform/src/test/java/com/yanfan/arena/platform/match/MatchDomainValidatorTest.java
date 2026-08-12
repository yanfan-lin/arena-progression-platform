package com.yanfan.arena.platform.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.team.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// Validate the domain of match events against stored team/roster state:
// teams exist and are active, modes match, the winner participates,
// rosters match exactly, and no player appears twice within a team
// or on both teams.
@ExtendWith(MockitoExtension.class)
class MatchDomainValidatorTest {

    private static final UUID EVENT_ID = UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79");

    private static final UUID MATCH_ID = UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86");

    private static final Instant NOW = Instant.parse("2026-08-12T00:05:00Z");

    private static final Instant ACTIVATED_AT = Instant.parse("2026-08-11T00:00:00Z");

    @Mock
    TeamRepository teamRepository;

    @Mock
    TeamMemberRepository teamMemberRepository;

    MatchDomainValidator validator;


    @BeforeEach
    void setUp() {
        validator = new MatchDomainValidator(teamRepository, teamMemberRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void validEventPasses() {
        stubValidState();

        assertThatCode(() -> validator.validate(validEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    void sameTeamOnBothSidesIsRejected() {
        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(team(1L, 101L, 102L, 103L),
                        team(1L, 104L, 105L, 106L)));

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("same");
    }

    @Test
    void unknownTeamIsRejected() {
        when(teamRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(activeTeam(1L)));

        assertThatThrownBy(() -> validator.validate(validEvent()))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("do not exist");
    }

    @Test
    void inactiveTeamIsRejected() {
        Team inactiveTeam = activeTeam(2L);
        inactiveTeam.setStatus(TeamStatus.RETIRED);

        when(teamRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(activeTeam(1L), inactiveTeam));

        assertThatThrownBy(() -> validator.validate(validEvent()))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void modeMismatchIsRejected() {
        Team wrongMode = activeTeam(2L);
        wrongMode.setMode(ArenaMode.FIVE_VS_FIVE);

        when(teamRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(activeTeam(1L), wrongMode));

        assertThatThrownBy(() -> validator.validate(validEvent()))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("match mode");
    }

    @Test
    void winnerNotInMatchIsRejected() {
        when(teamRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(activeTeam(1L), activeTeam(2L)));

        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                99,
                List.of(team(1L, 101L, 102L, 103L),
                        team(2L, 201L, 202L, 203L)));

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("Winner is not");
    }

    @Test
    void rosterMismatchIsRejected() {

        stubValidState();

        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(team(1L, 101L, 102L),
                        team(2L, 201L, 202L, 203L)));

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("rosters");
    }

    @Test
    void playerOnBothTeamsIsRejected() {
        when(teamRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(activeTeam(1L), activeTeam(2L)));

        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(team(1L, 101L, 102L, 103L),
                        team(2L, 101L, 202L, 203L)));

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("appears on both");

    }

    @Test
    void duplicatePlayerInsideTeamIsRejected() {
        when(teamRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(activeTeam(1L), activeTeam(2L)));

        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(team(1L, 101L, 101L, 102L),
                        team(2L, 201L, 202L, 203L)));

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("more than once");
    }

    @Test
    void completedAtBeforeTeamActivationIsRejected() {
        Team lateActivation = activeTeam(2L);
        lateActivation.setActivatedAt(Instant.parse("2026-08-12T00:10:00Z"));

        when(teamRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(activeTeam(1L), lateActivation));

        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(team(1L, 101L, 102L, 103L),
                        team(2L, 201L, 202L, 203L)));

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("activated");
    }

    @Test
    void completedAtTooFarInTheFutureIsRejected() {
        when(teamRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(activeTeam(1L), activeTeam(2L)));

        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                NOW.plusSeconds(6 * 60),
                1,
                List.of(team(1L, 101L, 102L, 103L),
                        team(2L, 201L, 202L, 203L)));

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("future");
    }

    @Test
    void completedAtWithinFutureTolerancePasses() {

        stubValidState();

        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                NOW.plusSeconds(4 * 60),
                1,
                List.of(team(1L, 101L, 102L, 103L),
                        team(2L, 201L, 202L, 203L)));

        assertThatCode(() -> validator.validate(event))
                .doesNotThrowAnyException();
    }

    @Test
    void completedAtExactlyAtFutureTolerancePasses() {

        stubValidState();

        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                NOW.plusSeconds(5 * 60),
                1,
                List.of(team(1L, 101L, 102L, 103L),
                        team(2L, 201L, 202L, 203L)));

        assertThatCode(() -> validator.validate(event))
                .doesNotThrowAnyException();
    }

    // Set up the normal database state: two active 3v3 teams with locked rosters
    private void stubValidState() {
        when(teamRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(activeTeam(1L), activeTeam(2L)));

        when(teamMemberRepository.findByTeamId(1L))
                .thenReturn(members(1L, 101L, 102L, 103L));

        when(teamMemberRepository.findByTeamId(2L))
                .thenReturn(members(2L, 201L, 202L, 203L));
    }

    // Set up an event whose teams and rosters match the stubbed database state
    private ArenaMatchCompleted validEvent() {
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(
                        team(1L, 101L, 102L, 103L),
                        team(2L, 201L, 202L, 203L)));
    }

    // Build the team with given player's IDs
    private ArenaMatchCompleted.Team team(long teamId, Long... playerIds) {
        List<ArenaMatchCompleted.Player> players = java.util.Arrays.stream(playerIds)
                .map(id -> new ArenaMatchCompleted.Player(id, 1, 1, 1))
                .toList();

        return new ArenaMatchCompleted.Team(teamId, players);
    }

    private Team activeTeam(long teamId) {
        Team team = new Team();

        team.setTeamId(teamId);
        team.setName("Team" + teamId);
        team.setMode(ArenaMode.THREE_VS_THREE);
        team.setStatus(TeamStatus.ACTIVE);
        team.setActivatedAt(ACTIVATED_AT);

        return team;
    }

    // Build the stored roster rows for one team
    private List<TeamMember> members(long teamId, long... playerIds) {
        return java.util.Arrays.stream(playerIds)
                .mapToObj(playerId -> {
                    TeamMember member = new TeamMember();
                    member.setTeamId(teamId);
                    member.setPlayerId(playerId);
                    return member;
                })
                .toList();
    }


}
