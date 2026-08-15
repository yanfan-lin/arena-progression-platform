package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.match.validation.MatchEventValidationException;
import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.progression.EloPolicy;
import com.yanfan.arena.platform.progression.XpPolicy;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Calculate every progression change for one arena match.
// Return the change only, MatchProcessor.java handles database transaction
@Component
public class MatchProgressionCalculator {

    // Return a summary of what should be changed after one match:
    // team ratings, team stats, XP, and levels
    public MatchProcessingResult.ProcessedMatch calculate(
            ArenaMatchCompleted event,
            Team teamA,
            Team teamB,
            Map<Long, Player> playersById) {

        boolean teamAWon = teamA.getTeamId() == event.winnerTeamId();

        // Calculate new rating for both teams after the match
        EloPolicy.RatingChange ratingChange;
        try {
            ratingChange = EloPolicy.calculate(
                    teamAWon ? teamA.getRating() : teamB.getRating(),
                    teamAWon ? teamB.getRating() : teamA.getRating());
        } catch (ArithmeticException e) {
            throw new MatchEventValidationException("Elo rating would overflow");
        }

        // Build the team results for both teams
        List<MatchProcessingResult.TeamResult> teamResults = new ArrayList<>();
        teamResults.add(buildTeamResult(
                teamA,
                event.teams().get(0),
                teamAWon,
                ratingChange));

        teamResults.add(buildTeamResult(
                teamB,
                event.teams().get(1),
                !teamAWon,
                ratingChange));

        // Build player results for every participant
        List<MatchProcessingResult.PlayerResult> playerResults = new ArrayList<>();
        for (ArenaMatchCompleted.Team eventTeam : event.teams()) {
            boolean won = eventTeam.teamId() == event.winnerTeamId();

            for (ArenaMatchCompleted.Player participant : eventTeam.participants()) {
                // Load the player by ID
                Player player = playersById.get(participant.playerId());

                long xpEarned = XpPolicy.xpEarned(won);
                long totalXpAfter = addXp(player.getTotalXp(), xpEarned);

                ensureLevelFits(totalXpAfter);

                // Keep player's stats as a snapshot for history
                playerResults.add(new MatchProcessingResult.PlayerResult(
                        participant.playerId(),
                        eventTeam.teamId(),
                        player.getDisplayName(),
                        participant.kills(),
                        participant.deaths(),
                        participant.assists(),
                        xpEarned,
                        totalXpAfter,
                        XpPolicy.levelFor(totalXpAfter)));
            }
        }

        // Return the full summary of changes after calculation
        return new MatchProcessingResult.ProcessedMatch(
                event.matchId().toString(),
                event.eventId().toString(),
                toArenaMode(event.mode()),
                event.winnerTeamId(),
                event.contractVersion(),
                event.completedAt(),
                teamResults,
                playerResults);

    }

    // Build the snapshot for one team after the match
    private MatchProcessingResult.TeamResult buildTeamResult(
            Team team,
            ArenaMatchCompleted.Team eventTeam,
            boolean won,
            EloPolicy.RatingChange ratingChange) {

        int ratingBefore = team.getRating();
        int ratingAfter = won ? ratingChange.winnerRatingAfter() : ratingChange.loserRatingAfter();

        // Sum the K/D/A for this team
        int kills = 0;
        int deaths = 0;
        int assists = 0;
        for (ArenaMatchCompleted.Player participant : eventTeam.participants()) {
            kills += participant.kills();
            deaths += participant.deaths();
            assists += participant.assists();
        }

        // Compute the updated stats after the match
        int matchesPlayedAfter = addStat(team.getMatchesPlayed(), 1);
        int winsAfter = addStat(team.getWins(), won ? 1 : 0);
        int lossesAfter = addStat(team.getLosses(), won ? 0 : 1);
        int totalKillsAfter = addStat(team.getTotalKills(), kills);
        int totalDeathsAfter = addStat(team.getTotalDeaths(), deaths);
        int totalAssistsAfter = addStat(team.getTotalAssists(), assists);

        // Return the updated team results
        return new MatchProcessingResult.TeamResult(
                team.getTeamId(),
                team.getName(),
                ratingBefore,
                ratingAfter - ratingBefore,
                ratingAfter,
                matchesPlayedAfter,
                winsAfter,
                lossesAfter,
                totalKillsAfter,
                totalDeathsAfter,
                totalAssistsAfter
        );

    }

    private ArenaMode toArenaMode(MatchMode mode) {
        return switch (mode) {
            case THREE_VS_THREE -> ArenaMode.THREE_VS_THREE;
            case FIVE_VS_FIVE -> ArenaMode.FIVE_VS_FIVE;
        };
    }


    // Reject XP that would overflow the cumulative long total
    private long addXp(long currentXp, long xpEarned) {
        if (currentXp > Long.MAX_VALUE - xpEarned) {
            throw new MatchEventValidationException("Player XP would overflow");
        }

        return currentXp + xpEarned;
    }

    // Reject team statistics that would overflow the cumulative int total
    private int addStat(int currentValue, int addedValue) {
        if (currentValue > Integer.MAX_VALUE - addedValue) {
            throw new MatchEventValidationException("Team statistic would overflow");
        }

        return currentValue + addedValue;
    }

    // Level is stored as INT, so reject XP that would push it past Integer.MAX_VALUE
    private void ensureLevelFits(long totalXp) {
        if (totalXp / XpPolicy.XP_PER_LEVEL > Integer.MAX_VALUE - 1L) {
            throw new MatchEventValidationException("Player level would overflow");
        }
    }


}
