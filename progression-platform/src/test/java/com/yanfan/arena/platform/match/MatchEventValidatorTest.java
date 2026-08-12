package com.yanfan.arena.platform.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Validate the structure of completed match events:
// contract version, event presence, bean-validation constraints,
// and exact roster size per arena mode.
class MatchEventValidatorTest {

    private MatchEventValidator validator;

    private static final UUID EVENT_ID = UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79");

    private static final UUID MATCH_ID = UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86");


    @BeforeEach
    void setUp() {
        validator = new MatchEventValidator(
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Test
    void validEventPasses() {
        assertThatCode(() -> validator.validate(validEvent()))
                .doesNotThrowAnyException();
    }

    @Test
    void nullEventIsRejected() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void unsupportedVersionIsRejected() {
        ArenaMatchCompleted event = new ArenaMatchCompleted(
                99,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                twoTeams());

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("Unsupported contract version");
    }

    @Test
    void structurallyInvalidEventIsRejected() {
        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(team(1, 101L, 102L, 103L)));

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void threeVsThreeWithTwoParticipantsIsRejected() {
        assertThatThrownBy(() -> validator.validate(
                new ArenaMatchCompleted(
                        ArenaMatchCompleted.CONTRACT_VERSION,
                        EVENT_ID,
                        MATCH_ID,
                        MatchMode.THREE_VS_THREE,
                        Instant.parse("2026-08-12T00:00:00Z"),
                        1,
                        List.of(
                                team(1L, 101L, 102L),
                                team(2L, 201L, 202L)))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("exactly 3");
    }

    @Test
    void threeVsThreeWithFourParticipantsIsRejected() {
        assertThatThrownBy(() -> validator.validate(
                new ArenaMatchCompleted(
                        ArenaMatchCompleted.CONTRACT_VERSION,
                        EVENT_ID,
                        MATCH_ID,
                        MatchMode.THREE_VS_THREE,
                        Instant.parse("2026-08-12T00:00:00Z"),
                        1,
                        List.of(team(1L, 101L, 102L, 103L, 104L),
                                team(2L, 201L, 202L, 203L, 204L)))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("exactly 3");
    }

    @Test
    void fiveVsFiveWithFourParticipantsIsRejected() {
        assertThatThrownBy(() -> validator.validate(
                new ArenaMatchCompleted(
                        ArenaMatchCompleted.CONTRACT_VERSION,
                        EVENT_ID,
                        MATCH_ID,
                        MatchMode.FIVE_VS_FIVE,
                        Instant.parse("2026-08-12T00:00:00Z"),
                        1,
                        List.of(team(1L, 101L, 102L, 103L, 104L),
                                team(2L, 201L, 202L, 203L, 204L)))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("exactly 5");
    }

    @Test
    void fiveVsFiveWithExactSizePasses() {
        assertThatCode(() -> validator.validate(
                new ArenaMatchCompleted(
                        ArenaMatchCompleted.CONTRACT_VERSION,
                        EVENT_ID,
                        MATCH_ID,
                        MatchMode.FIVE_VS_FIVE,
                        Instant.parse("2026-08-12T00:00:00Z"),
                        1,
                        List.of(team(1L, 101L, 102L, 103L, 104L, 105L),
                                team(2L, 201L, 202L, 203L, 204L, 205L)))))
                .doesNotThrowAnyException();
    }

    @Test
    void nullTeamsListIsRejected() {
        assertThatThrownBy(() -> validator.validate(event(null)))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void nullTeamElementIsRejected() {
        assertThatThrownBy(() -> validator.validate(event(
                java.util.Arrays.asList(team(1L, 101L, 102L, 103L), null))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void nullPlayerElementIsRejected() {
        ArenaMatchCompleted.Team teamA = teamOf(1L,
                new ArenaMatchCompleted.Player(101L, 1, 1, 1),
                null,
                new ArenaMatchCompleted.Player(103L, 1, 1, 1));

        assertThatThrownBy(() -> validator.validate(event(
                List.of(teamA, team(2L, 201L, 202L, 203L)))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"kills", "deaths", "assists"})
    void nullStatValueIsRejected(String stat) {
        ArenaMatchCompleted.Player player = switch (stat) {
            case "kills" -> new ArenaMatchCompleted.Player(101L, null, 1, 1);
            case "deaths" -> new ArenaMatchCompleted.Player(101L, 1, null, 1);
            default -> new ArenaMatchCompleted.Player(101L, 1, 1, null);
        };

        ArenaMatchCompleted.Team teamA = teamOf(1L,
                player,
                new ArenaMatchCompleted.Player(102L, 1, 1, 1),
                new ArenaMatchCompleted.Player(103L, 1, 1, 1));

        assertThatThrownBy(() -> validator.validate(event(
                List.of(teamA, team(2L, 201L, 202L, 203L)))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("invalid");
    }

    private ArenaMatchCompleted validEvent() {
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                twoTeams());
    }

    private List<ArenaMatchCompleted.Team> twoTeams() {
        return List.of(
                team(1, 101L, 102L, 103L),
                team(2, 201L, 202L, 203L));
    }

    // Build the team with given player's IDs
    private ArenaMatchCompleted.Team team(long teamId, Long... playerIds) {
        List<ArenaMatchCompleted.Player> players = java.util.Arrays.stream(playerIds)
                .map(id -> new ArenaMatchCompleted.Player(id, 1, 1, 1))
                .toList();

        return new ArenaMatchCompleted.Team(teamId, players);
    }

    private ArenaMatchCompleted event(List<ArenaMatchCompleted.Team> teams) {
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                teams);
    }

    // Build the team with the given player objects (null is allowed for structural tests)
    private ArenaMatchCompleted.Team teamOf(long teamId, ArenaMatchCompleted.Player... players) {
        return new ArenaMatchCompleted.Team(teamId, java.util.Arrays.asList(players));
    }


}
