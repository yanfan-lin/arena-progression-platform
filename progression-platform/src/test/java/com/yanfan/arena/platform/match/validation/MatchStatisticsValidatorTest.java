package com.yanfan.arena.platform.match.validation;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Validate the statistics of match events: balanced stats pass,
// kills must equal the opponent team's deaths,
// and assists cannot exceed the theoretical maximum.
class MatchStatisticsValidatorTest {

    private static final UUID EVENT_ID = UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79");

    private static final UUID MATCH_ID = UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86");

    private final MatchStatisticsValidator validator = new MatchStatisticsValidator();

    @Test
    void balancedEventPasses() {
        assertThatCode(() -> validator.validate(event(
                team(1L,
                        player(101L, 2, 1, 1),
                        player(102L, 1, 1, 1),
                        player(103L, 1, 0, 0)),
                team(2L,
                        player(201L, 1, 2, 1),
                        player(202L, 1, 1, 0),
                        player(203L, 0, 1, 0)))))
                .doesNotThrowAnyException();
    }

    @Test
    void killsMustMatchOpponentDeaths() {
        // Team A has 5 kills but Team B only has 4 deaths
        assertThatThrownBy(() -> validator.validate(event(
                team(1L,
                        player(101L, 2, 1, 1),
                        player(102L, 2, 1, 1),
                        player(103L, 1, 0, 0)),
                team(2L,
                        player(201L, 1, 2, 1),
                        player(202L, 1, 1, 0),
                        player(203L, 0, 1, 0)))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("Kills and deaths");
    }

    @Test
    void assistsCannotExceedTheoreticalMaximum() {
        // Team A has 4 kills, so max assists = 4 x (3 - 1) = 8, but has 9
        assertThatThrownBy(() -> validator.validate(event(
                team(1L,
                        player(101L, 2, 1, 3),
                        player(102L, 1, 1, 3),
                        player(103L, 1, 0, 3)),
                team(2L,
                        player(201L, 1, 2, 0),
                        player(202L, 1, 1, 0),
                        player(203L, 0, 1, 0)))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("Assists exceed");
    }

    @Test
    void individualStatsAtMaximumPass() {

        int max = MatchStatisticsValidator.MAX_INDIVIDUAL_STAT;

        assertThatCode(() -> validator.validate(event(
                team(1L,
                        player(101L, max, max, max),
                        player(102L, max, max, max),
                        player(103L, max, max, max)),
                team(2L,
                        player(201L, max, max, max),
                        player(202L, max, max, max),
                        player(203L, max, max, max)))))
                .doesNotThrowAnyException();
    }

    @Test
    void individualKillsAboveMaximumIsRejected() {

        int max = MatchStatisticsValidator.MAX_INDIVIDUAL_STAT;

        assertThatThrownBy(() -> validator.validate(event(
                team(1L,
                        player(101L, max + 1, 0, 0),
                        player(102L, 0, 0, 0),
                        player(103L, 0, 0, 0)),
                team(2L,
                        player(201L, 0, 0, 0),
                        player(202L, 0, 0, 0),
                        player(203L, 0, 0, 0)))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("maximum");
    }

    private ArenaMatchCompleted event(ArenaMatchCompleted.Team teamA, ArenaMatchCompleted.Team teamB) {
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-12T00:00:00Z"),
                1,
                List.of(teamA, teamB));
    }

    private ArenaMatchCompleted.Team team(long teamId, ArenaMatchCompleted.Player... players) {
        return new ArenaMatchCompleted.Team(teamId, List.of(players));
    }

    private ArenaMatchCompleted.Player player(long playerId, int kills, int deaths, int assists) {
        return new ArenaMatchCompleted.Player(playerId, kills, deaths, assists);
    }

}
