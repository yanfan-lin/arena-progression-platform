package com.yanfan.arena.contract;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArenaMatchCompletedTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static final UUID EVENT_ID = UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79");

    private static final UUID MATCH_ID = UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86");


    @Test
    void validContractPassesStructuralValidation() {
        assertThat(VALIDATOR.validate(validEvent()))
                .isEmpty();
    }

    @Test
    void oneTeamFailsStructuralValidation() {
        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(team(1, 101, 102, 103)));

        assertThat(VALIDATOR.validate(event)).isNotEmpty();
    }

    private ArenaMatchCompleted validEvent() {
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(
                        team(1, 101, 102, 103),
                        team(2, 201, 202, 203)));
    }

    private ArenaMatchCompleted.Team team(long teamId, long... playerIds) {
        List<ArenaMatchCompleted.Player> players = java.util.Arrays
                .stream(playerIds)
                .mapToObj(id -> new ArenaMatchCompleted.Player(id, 1, 1, 1))
                .toList();

        return new ArenaMatchCompleted.Team(teamId, players);
    }

}
