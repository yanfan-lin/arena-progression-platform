package com.yanfan.arena.platform.match.processing;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.progression.EloPolicy;
import com.yanfan.arena.platform.progression.XpPolicy;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Calculate match progression without changing the database.
@Component
public class MatchProgressionCalculator {

    // Return a summary of what should be changed after one match:
    // team ratings, team stats, XP, and levels
    public MatchProcessingResult.ProcessedMatch calculate(
            ArenaMatchCompleted event,
            Team teamA,
            Team teamB,
            Map<Long, Player> playersById)
    {
        boolean teamAWon = teamA.getTeamId() == event.winnerTeamId();

        // Calculate new rating for both teams after the match
        EloPolicy.RatingChange ratingChange = EloPolicy.calculate(
                teamAWon ? teamA.getRating() : teamB.getRating(),
                teamAWon ? teamB.getRating() : teamA.getRating());

        // Build the team results for both teams
        List<MatchProcessingResult.TeamResult> teamResults = List.of(
                buildTeamResult(
                        teamA,
                        event.teams().get(0),
                        teamAWon,
                        ratingChange),
                buildTeamResult(
                        teamB,
                        event.teams().get(1),
                        !teamAWon,
                        ratingChange)
        );

        // Build player results for every participant
        List<MatchProcessingResult.PlayerResult> playerResults = new ArrayList<>();

        for (ArenaMatchCompleted.Team eventTeam : event.teams()) {

            boolean won = eventTeam.teamId() == event.winnerTeamId();

            // Every player on the same team receives the same win or loss XP
            long xpEarned = XpPolicy.xpEarned(won);

            for (ArenaMatchCompleted.Player participant : eventTeam.participants()) {
                // Load the player by ID
                Player player = playersById.get(participant.playerId());

                long totalXpAfter = player.getTotalXp() + xpEarned;

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
                ArenaMode.from(event.mode()),
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
            EloPolicy.RatingChange ratingChange)
    {

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
        int matchesPlayedAfter = team.getMatchesPlayed() + 1;
        int winsAfter = team.getWins() + (won ? 1 : 0);
        int lossesAfter = team.getLosses() + (won ? 0 : 1);
        int totalKillsAfter = team.getTotalKills() + kills;
        int totalDeathsAfter = team.getTotalDeaths() + deaths;
        int totalAssistsAfter = team.getTotalAssists() + assists;

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

}
