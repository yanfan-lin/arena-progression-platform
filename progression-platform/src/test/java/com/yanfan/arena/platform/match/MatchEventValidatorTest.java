package com.yanfan.arena.platform.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                EVENT_ID.toString(),
                MATCH_ID.toString(),
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
                EVENT_ID.toString(),
                MATCH_ID.toString(),
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(team(1, 101L, 102L, 103L)));

        assertThatThrownBy(() -> validator.validate(event))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("invalid");
    }

    private ArenaMatchCompleted validEvent() {
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID.toString(),
                MATCH_ID.toString(),
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


}
