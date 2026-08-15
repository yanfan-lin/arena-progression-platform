package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.match.validation.MatchEventValidationException;
import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Verify that the progression calculator produces the correct
// Elo changes, team stats, XP, and levels for one match.
class MatchProgressionCalculatorTest {

    private static final UUID EVENT_ID = UUID.fromString("4e74866d-5a18-4695-bf5e-ff8b79226b79");
    private static final UUID MATCH_ID = UUID.fromString("0775a8e0-cd3a-4d03-a9d4-62a43fc09d86");

    private final MatchProgressionCalculator calculator = new MatchProgressionCalculator();

    // Verify that the calculator handles 3v3 match
    @Test
    void computesEloXpAndTeamStats() {
        // Team A is the winner: 5 matches played, 3 wins, 2 losses
        Team teamA = team(1L, "Alpha", 1000, 5, 3, 2, 10, 8, 6);

        // Team B is the loser: 4 matches played, 2 wins, 2 losses
        Team teamB = team(2L, "Beta", 1000, 4, 2, 2, 5, 7, 3);

        // Players hold their current XP and AlphaTwo is close to a level-up
        Player alphaOne = player("AlphaOne", 500L);
        Player alphaTwo = player("AlphaTwo", 900L);
        Player alphaThree = player("AlphaThree", 0L);
        Player betaOne = player("BetaOne", 500L);
        Player betaTwo = player("BetaTwo", 500L);
        Player betaThree = player("BetaThree", 500L);

        // A completed 3v3 match event where team 1L wins
        ArenaMatchCompleted event = event(1L,
                eventTeam(1L,
                        eventPlayer(101L, 5, 2, 3),
                        eventPlayer(102L, 2, 1, 1),
                        eventPlayer(103L, 0, 0, 0)),
                eventTeam(2L,
                        eventPlayer(201L, 1, 4, 2),
                        eventPlayer(202L, 0, 3, 1),
                        eventPlayer(203L, 2, 2, 0)));

        // Run the calculation
        MatchProcessingResult.ProcessedMatch result = calculator.calculate(
                event, teamA, teamB,
                Map.of(101L, alphaOne, 102L, alphaTwo, 103L, alphaThree,
                        201L, betaOne, 202L, betaTwo, 203L, betaThree));

        // The summary carries the event and match identifiers
        assertThat(result.eventId()).isEqualTo(EVENT_ID.toString());
        assertThat(result.matchId()).isEqualTo(MATCH_ID.toString());

        assertThat(result.mode()).isEqualTo(ArenaMode.THREE_VS_THREE);
        assertThat(result.winningTeamId()).isEqualTo(1L);
        assertThat(result.contractVersion()).isEqualTo(ArenaMatchCompleted.CONTRACT_VERSION);

        // Team A wins: equal ratings gain 16 points, so 1000 -> 1016
        MatchProcessingResult.TeamResult teamAResult = result.teamResults().get(0);
        assertThat(teamAResult.teamId()).isEqualTo(1L);
        assertThat(teamAResult.teamNameSnapshot()).isEqualTo("Alpha");
        assertThat(teamAResult.ratingBefore()).isEqualTo(1000);
        assertThat(teamAResult.ratingChange()).isEqualTo(16);
        assertThat(teamAResult.ratingAfter()).isEqualTo(1016);
        assertThat(teamAResult.matchesPlayedAfter()).isEqualTo(6);
        assertThat(teamAResult.winsAfter()).isEqualTo(4);
        assertThat(teamAResult.lossesAfter()).isEqualTo(2);

        // Team A's lifetime K/D/A updated with this match's sums (7/3/4)
        assertThat(teamAResult.totalKillsAfter()).isEqualTo(17);
        assertThat(teamAResult.totalDeathsAfter()).isEqualTo(11);
        assertThat(teamAResult.totalAssistsAfter()).isEqualTo(10);

        // Team B loses: the same 16 points deduced
        MatchProcessingResult.TeamResult teamBResult = result.teamResults().get(1);
        assertThat(teamBResult.ratingBefore()).isEqualTo(1000);
        assertThat(teamBResult.ratingChange()).isEqualTo(-16);
        assertThat(teamBResult.ratingAfter()).isEqualTo(984);
        assertThat(teamBResult.matchesPlayedAfter()).isEqualTo(5);
        assertThat(teamBResult.winsAfter()).isEqualTo(2);
        assertThat(teamBResult.lossesAfter()).isEqualTo(3);

        // Team B's lifetime K/D/A updated with this match's sums (3/9/3)
        assertThat(teamBResult.totalKillsAfter()).isEqualTo(8);
        assertThat(teamBResult.totalDeathsAfter()).isEqualTo(16);
        assertThat(teamBResult.totalAssistsAfter()).isEqualTo(6);

        // Every participant gets one result row
        assertThat(result.playerResults()).hasSize(6);

        // Winning player: 150 XP (100 play + 50 win), 500 -> 650 XP, still level 1
        MatchProcessingResult.PlayerResult alphaOneResult = result.playerResults().get(0);
        assertThat(alphaOneResult.playerId()).isEqualTo(101L);
        assertThat(alphaOneResult.teamId()).isEqualTo(1L);
        assertThat(alphaOneResult.playerNameSnapshot()).isEqualTo("AlphaOne");
        assertThat(alphaOneResult.kills()).isEqualTo(5);
        assertThat(alphaOneResult.deaths()).isEqualTo(2);
        assertThat(alphaOneResult.assists()).isEqualTo(3);
        assertThat(alphaOneResult.xpEarned()).isEqualTo(150);
        assertThat(alphaOneResult.totalXpAfter()).isEqualTo(650);
        assertThat(alphaOneResult.levelAfter()).isEqualTo(1);

        // AlphaTwo started at 900 XP: +150 crosses 1000, so the level rises to 2
        MatchProcessingResult.PlayerResult alphaTwoResult = result.playerResults().get(1);
        assertThat(alphaTwoResult.xpEarned()).isEqualTo(150);
        assertThat(alphaTwoResult.totalXpAfter()).isEqualTo(1050);
        assertThat(alphaTwoResult.levelAfter()).isEqualTo(2);

        // Losing player: only 100 XP, 500 -> 600 XP, level stays 1
        MatchProcessingResult.PlayerResult betaOneResult = result.playerResults().get(3);
        assertThat(betaOneResult.xpEarned()).isEqualTo(100);
        assertThat(betaOneResult.totalXpAfter()).isEqualTo(600);
        assertThat(betaOneResult.levelAfter()).isEqualTo(1);

    }

    // Verify that the calculator also handles a 5v5 match
    @Test
    void computesFiveVsFiveProgression() {
        // Both teams start at 1000 so the expected Elo is the same as 3v3: +/-16
        Team teamA = team(1L, "Alpha", 1000, 10, 6, 4, 30, 20, 15);
        Team teamB = team(2L, "Beta", 1000, 8, 3, 5, 18, 25, 10);

        // Five players per team, all starting at 500 XP
        Player alphaOne = player("AlphaOne", 500L);
        Player alphaTwo = player("AlphaTwo", 500L);
        Player alphaThree = player("AlphaThree", 500L);
        Player alphaFour = player("AlphaFour", 500L);
        Player alphaFive = player("AlphaFive", 500L);
        Player betaOne = player("BetaOne", 500L);
        Player betaTwo = player("BetaTwo", 500L);
        Player betaThree = player("BetaThree", 500L);
        Player betaFour = player("BetaFour", 500L);
        Player betaFive = player("BetaFive", 500L);

        // A completed 5v5 match where team 1 wins
        // Team A players each get 1 kill; team B players each get 1 death
        ArenaMatchCompleted event = eventFiveVsFive(1L,
                eventTeam(1L,
                        eventPlayer(101L, 1, 0, 0),
                        eventPlayer(102L, 1, 0, 0),
                        eventPlayer(103L, 1, 0, 0),
                        eventPlayer(104L, 1, 0, 0),
                        eventPlayer(105L, 1, 0, 0)),
                eventTeam(2L,
                        eventPlayer(201L, 0, 1, 0),
                        eventPlayer(202L, 0, 1, 0),
                        eventPlayer(203L, 0, 1, 0),
                        eventPlayer(204L, 0, 1, 0),
                        eventPlayer(205L, 0, 1, 0)));

        // Run the calculation with all ten players
        MatchProcessingResult.ProcessedMatch result = calculator.calculate(
                event, teamA, teamB,
                Map.of(101L, alphaOne, 102L, alphaTwo, 103L, alphaThree,
                        104L, alphaFour, 105L, alphaFive,
                        201L, betaOne, 202L, betaTwo, 203L, betaThree,
                        204L, betaFour, 205L, betaFive));

        // The mode is converted to the platform's 5v5 value
        assertThat(result.mode()).isEqualTo(ArenaMode.FIVE_VS_FIVE);

        // Two team rows and ten player rows
        assertThat(result.teamResults()).hasSize(2);
        assertThat(result.playerResults()).hasSize(10);

        // Winner: 1000 -> 1016, matches 11, wins 7, and 5 more kills
        MatchProcessingResult.TeamResult teamAResult = result.teamResults().get(0);
        assertThat(teamAResult.ratingAfter()).isEqualTo(1016);
        assertThat(teamAResult.matchesPlayedAfter()).isEqualTo(11);
        assertThat(teamAResult.winsAfter()).isEqualTo(7);
        assertThat(teamAResult.lossesAfter()).isEqualTo(4);
        assertThat(teamAResult.totalKillsAfter()).isEqualTo(35);
        assertThat(teamAResult.totalDeathsAfter()).isEqualTo(20);
        assertThat(teamAResult.totalAssistsAfter()).isEqualTo(15);

        // Loser: 1000 -> 984, matches 9, losses 6, and 5 more deaths
        MatchProcessingResult.TeamResult teamBResult = result.teamResults().get(1);
        assertThat(teamBResult.ratingAfter()).isEqualTo(984);
        assertThat(teamBResult.matchesPlayedAfter()).isEqualTo(9);
        assertThat(teamBResult.winsAfter()).isEqualTo(3);
        assertThat(teamBResult.lossesAfter()).isEqualTo(6);
        assertThat(teamBResult.totalKillsAfter()).isEqualTo(18);
        assertThat(teamBResult.totalDeathsAfter()).isEqualTo(30);
        assertThat(teamBResult.totalAssistsAfter()).isEqualTo(10);

        // First winner: 150 XP, 500 -> 650, still level 1
        MatchProcessingResult.PlayerResult alphaOneResult = result.playerResults().get(0);
        assertThat(alphaOneResult.xpEarned()).isEqualTo(150);
        assertThat(alphaOneResult.totalXpAfter()).isEqualTo(650);
        assertThat(alphaOneResult.levelAfter()).isEqualTo(1);

        // First loser: 100 XP, 500 -> 600, still level 1
        MatchProcessingResult.PlayerResult betaOneResult = result.playerResults().get(5);
        assertThat(betaOneResult.xpEarned()).isEqualTo(100);
        assertThat(betaOneResult.totalXpAfter()).isEqualTo(600);
        assertThat(betaOneResult.levelAfter()).isEqualTo(1);
    }

    // Reject XP that would overflow the cumulative long total
    @Test
    void rejectsPlayerXpOverflow() {
        Team teamA = team(1L, "Alpha", 1000, 0, 0, 0, 0, 0, 0);
        Team teamB = team(2L, "Beta", 1000, 0, 0, 0, 0, 0, 0);

        Player maxXp = player("MaxXp", Long.MAX_VALUE - 100L);

        ArenaMatchCompleted event = event(1L,
                eventTeam(1L,
                        eventPlayer(101L, 0, 0, 0),
                        eventPlayer(102L, 0, 0, 0),
                        eventPlayer(103L, 0, 0, 0)),
                eventTeam(2L,
                        eventPlayer(201L, 0, 0, 0),
                        eventPlayer(202L, 0, 0, 0),
                        eventPlayer(203L, 0, 0, 0)));

        assertThatThrownBy(() -> calculator.calculate(event, teamA, teamB,
                Map.of(101L, maxXp, 102L, player("A2", 500L), 103L, player("A3", 500L),
                        201L, player("B1", 500L), 202L, player("B2", 500L),
                        203L, player("B3", 500L))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("XP");
    }

    // Reject team statistics that would overflow the cumulative int total
    @Test
    void rejectsTeamStatOverflow() {
        Team teamA = team(1L, "Alpha", 1000, Integer.MAX_VALUE, 0, 0, 0, 0, 0);
        Team teamB = team(2L, "Beta", 1000, 0, 0, 0, 0, 0, 0);

        ArenaMatchCompleted event = event(1L,
                eventTeam(1L,
                        eventPlayer(101L, 0, 0, 0),
                        eventPlayer(102L, 0, 0, 0),
                        eventPlayer(103L, 0, 0, 0)),
                eventTeam(2L,
                        eventPlayer(201L, 0, 0, 0),
                        eventPlayer(202L, 0, 0, 0),
                        eventPlayer(203L, 0, 0, 0)));

        assertThatThrownBy(() -> calculator.calculate(event, teamA, teamB,
                Map.of(101L, player("A1", 500L), 102L, player("A2", 500L),
                        103L, player("A3", 500L), 201L, player("B1", 500L),
                        202L, player("B2", 500L), 203L, player("B3", 500L))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("statistic");
    }

    // Reject XP that would push the derived level past Integer.MAX_VALUE
    @Test
    void rejectsLevelOverflow() {
        Team teamA = team(1L, "Alpha", 1000, 0, 0, 0, 0, 0, 0);
        Team teamB = team(2L, "Beta", 1000, 0, 0, 0, 0, 0, 0);

        Player hugeXp = player("HugeXp", Integer.MAX_VALUE * 1000L);

        ArenaMatchCompleted event = event(1L,
                eventTeam(1L,
                        eventPlayer(101L, 0, 0, 0),
                        eventPlayer(102L, 0, 0, 0),
                        eventPlayer(103L, 0, 0, 0)),
                eventTeam(2L,
                        eventPlayer(201L, 0, 0, 0),
                        eventPlayer(202L, 0, 0, 0),
                        eventPlayer(203L, 0, 0, 0)));

        assertThatThrownBy(() -> calculator.calculate(event, teamA, teamB,
                Map.of(101L, hugeXp, 102L, player("A2", 500L), 103L, player("A3", 500L),
                        201L, player("B1", 500L), 202L, player("B2", 500L),
                        203L, player("B3", 500L))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("level");
    }

    // Reject Elo ratings that would overflow when the rating change is applied
    @Test
    void rejectsEloOverflow() {
        Team teamA = team(1L, "Alpha", Integer.MAX_VALUE, 0, 0, 0, 0, 0, 0);
        Team teamB = team(2L, "Beta", Integer.MAX_VALUE, 0, 0, 0, 0, 0, 0);

        ArenaMatchCompleted event = event(1L,
                eventTeam(1L,
                        eventPlayer(101L, 0, 0, 0),
                        eventPlayer(102L, 0, 0, 0),
                        eventPlayer(103L, 0, 0, 0)),
                eventTeam(2L,
                        eventPlayer(201L, 0, 0, 0),
                        eventPlayer(202L, 0, 0, 0),
                        eventPlayer(203L, 0, 0, 0)));

        assertThatThrownBy(() -> calculator.calculate(event, teamA, teamB,
                Map.of(101L, player("A1", 500L), 102L, player("A2", 500L),
                        103L, player("A3", 500L), 201L, player("B1", 500L),
                        202L, player("B2", 500L), 203L, player("B3", 500L))))
                .isInstanceOf(MatchEventValidationException.class)
                .hasMessageContaining("Elo");
    }

    // Build a Team with the state before a match
    private Team team(long teamId,
                      String name,
                      int rating,
                      int matchesPlayed,
                      int wins,
                      int losses,
                      int totalKills,
                      int totalDeaths,
                      int totalAssists) {
        Team team = new Team();
        team.setTeamId(teamId);
        team.setName(name);
        team.setMode(ArenaMode.THREE_VS_THREE);
        team.setStatus(TeamStatus.ACTIVE);
        team.setRating(rating);
        team.setMatchesPlayed(matchesPlayed);
        team.setWins(wins);
        team.setLosses(losses);
        team.setTotalKills(totalKills);
        team.setTotalDeaths(totalDeaths);
        team.setTotalAssists(totalAssists);

        return team;
    }

    // Build a player with only the values the calculator reads
    private Player player(String displayName, long totalXp) {
        Player player = mock(Player.class);

        when(player.getDisplayName()).thenReturn(displayName);

        when(player.getTotalXp()).thenReturn(totalXp);

        return player;
    }

    // Build a completed 3v3 match event
    private ArenaMatchCompleted event(long winnerTeamId, ArenaMatchCompleted.Team... teams) {
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.THREE_VS_THREE,
                Instant.parse("2026-08-13T00:00:00Z"),
                winnerTeamId,
                List.of(teams));
    }

    // Build a completed 5v5 match event
    private ArenaMatchCompleted eventFiveVsFive(long winnerTeamId, ArenaMatchCompleted.Team... teams) {
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                EVENT_ID,
                MATCH_ID,
                MatchMode.FIVE_VS_FIVE,
                Instant.parse("2026-08-13T00:00:00Z"),
                winnerTeamId,
                List.of(teams));
    }

    // Build one participating team inside the event
    private ArenaMatchCompleted.Team eventTeam(long teamId, ArenaMatchCompleted.Player... players) {
        return new ArenaMatchCompleted.Team(teamId, List.of(players));
    }

    // Build one participant with the K/D/A for the match
    private ArenaMatchCompleted.Player eventPlayer(long playerId, int kills, int deaths, int assists) {
        return new ArenaMatchCompleted.Player(playerId, kills, deaths, assists);
    }

}
