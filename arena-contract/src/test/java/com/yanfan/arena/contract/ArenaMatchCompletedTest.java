package com.yanfan.arena.contract;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArenaMatchCompletedTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void contractVersionIsOne() {
        assertThat(ArenaMatchCompleted.CONTRACT_VERSION)
                .isEqualTo(1);
    }

    @Test
    void validContractPassesStructuralValidation() {
        assertThat(VALIDATOR.validate(validEvent()))
                .isEmpty();
    }

    @Test
    void oneTeamFailsStructuralValidation() {
        ArenaMatchCompleted event = new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                "event-1",
                "match-1",
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(team(1, 101, 102, 103)));

        assertThat(VALIDATOR.validate(event)).isNotEmpty();
    }

    private ArenaMatchCompleted validEvent() {
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                "event-1",
                "match-1",
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
