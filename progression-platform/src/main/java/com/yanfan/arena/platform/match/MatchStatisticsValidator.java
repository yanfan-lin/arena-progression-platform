package com.yanfan.arena.platform.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import org.springframework.stereotype.Component;

// Validate the consistency of arena statistics for a match event.
@Component
public class MatchStatisticsValidator {

    public static final int MAX_INDIVIDUAL_STAT = 100_000;

    public void validate(ArenaMatchCompleted event) {
        ArenaMatchCompleted.Team teamA = event.teams().get(0);
        ArenaMatchCompleted.Team teamB = event.teams().get(1);

        checkIndividualStats(teamA);
        checkIndividualStats(teamB);

        long killsA = totalKills(teamA);
        long killsB = totalKills(teamB);
        long deathsA = totalDeaths(teamA);
        long deathsB = totalDeaths(teamB);
        long assistsA = totalAssists(teamA);
        long assistsB = totalAssists(teamB);

        // Total kills on one team must match the total deaths on the other team
        if (killsA != deathsB || killsB != deathsA) {
            throw new MatchEventValidationException("Kills and deaths do not match across teams");
        }

        // One kill can be assisted by at most every other player within the team
        long maxAssistsA = killsA * (teamA.participants().size() - 1);
        long maxAssistsB = killsB * (teamB.participants().size() - 1);

        if (assistsA > maxAssistsA || assistsB > maxAssistsB) {
            throw new MatchEventValidationException("Assists exceed the theoretical maximum");
        }
    }

    private long totalKills(ArenaMatchCompleted.Team team) {
        return team.participants().stream()
                .mapToLong(ArenaMatchCompleted.Player::kills)
                .sum();
    }

    private long totalDeaths(ArenaMatchCompleted.Team team) {
        return team.participants().stream()
                .mapToLong(ArenaMatchCompleted.Player::deaths)
                .sum();
    }

    private long totalAssists(ArenaMatchCompleted.Team team) {
        return team.participants().stream()
                .mapToLong(ArenaMatchCompleted.Player::assists)
                .sum();
    }

    private void checkIndividualStats(ArenaMatchCompleted.Team team) {
        for (ArenaMatchCompleted.Player player : team.participants()) {
            if (player.kills() > MAX_INDIVIDUAL_STAT
                    || player.deaths() > MAX_INDIVIDUAL_STAT
                    || player.assists() > MAX_INDIVIDUAL_STAT) {
                throw new MatchEventValidationException(
                        "Player " + player.playerId() + " has a statistic above the theoretical maximum");
            }
        }

    }

}
