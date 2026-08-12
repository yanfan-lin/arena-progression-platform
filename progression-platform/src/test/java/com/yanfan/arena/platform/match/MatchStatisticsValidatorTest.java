package com.yanfan.arena.platform.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchStatisticsValidatorTest {

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

    private ArenaMatchCompleted event(ArenaMatchCompleted.Team teamA, ArenaMatchCompleted.Team teamB) {
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                "event-1",
                "match-1",
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