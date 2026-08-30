package com.yanfan.arena.simulator.simulation.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.simulator.client.MatchCandidateResponse;
import com.yanfan.arena.simulator.client.PlatformClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

// Generate valid completed matches from active arena teams.
@Component
public class MatchGenerator {

    private final PlatformClient platformClient;

    private final Clock clock;

    @Autowired
    public MatchGenerator(PlatformClient platformClient, Clock clock) {
        this.platformClient = platformClient;
        this.clock = clock;
    }

    public ArenaMatchCompleted generateMatch(MatchMode mode) {

        // Identify this generated event so repeated deliveries are processed only once
        UUID eventId = UUID.randomUUID();

        // Identify this arena match so another event cannot process it again
        UUID matchId = UUID.randomUUID();

        // Load active teams with locked rosters for the requested mode
        List<MatchCandidateResponse> candidates = platformClient.getMatchCandidates(mode);

        if (candidates.size() < 2) {
            throw new InsufficientTeamsException(mode);
        }

        // Randomly select the first team from the active team candidates
        MatchCandidateResponse firstTeam =
                candidates.get(ThreadLocalRandom.current()
                        .nextInt(candidates.size()));

        // Select a different team for the second match slot
        MatchCandidateResponse secondTeam =
                selectDifferentTeam(candidates, firstTeam.teamId());

        // Randomly decide whether the first team wins the match
        // Choose the winner first because its total kills must equal the losing team's roster size
        boolean firstTeamWins = ThreadLocalRandom.current().nextBoolean();

        MatchCandidateResponse winner =
                firstTeamWins ? firstTeam : secondTeam;

        MatchCandidateResponse loser =
                firstTeamWins ? secondTeam : firstTeam;

        // Both teams use the same roster size (3 or 5)
        int rosterSize = winner.playerIds().size();

        // Keep the winner team's deaths below its roster size so at least one player survives
        int winnerDeathCount =
                ThreadLocalRandom.current().nextInt(rosterSize);

        // Randomly distribute the kills needed to eliminate the losing team
        // among the winning team players
        int[] winnerKills = distributeKills(rosterSize, rosterSize);

        // Randomly assign the winner's deaths while leaving at least one survivor
        int[] winnerDeaths = generateDeaths(rosterSize, winnerDeathCount);

        // Match loser's total kills to winner's deaths
        int[] loserKills = distributeKills(winnerDeathCount, rosterSize);

        // Mark every losing team player as dead because the entire team was eliminated
        int[] loserDeaths = generateDeaths(rosterSize, rosterSize);

        // Attach the generated match stats to each team's roster
        ArenaMatchCompleted.Team winnerResult =
                buildTeamResult(winner, winnerKills, winnerDeaths);

        ArenaMatchCompleted.Team loserResult =
                buildTeamResult(loser, loserKills, loserDeaths);

        List<ArenaMatchCompleted.Team> teams =
                List.of(winnerResult, loserResult);

        // Build the event that will later be published to Kafka
        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                eventId,
                matchId,
                mode,
                clock.instant(), // Record the exact match completion time
                winner.teamId(),
                teams
        );
    }

    private MatchCandidateResponse selectDifferentTeam(
            List<MatchCandidateResponse> candidates,
            Long firstTeamId) {
        MatchCandidateResponse candidate;

        // Keep selecting until the two match slots contain different arena teams
        do {
            candidate = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }
        while (candidate.teamId().equals(firstTeamId));

        return candidate;
    }

    // Randomly assign a fixed team kill total across its players
    private int[] distributeKills(int totalKills, int playerCount) {

        // Initialize every player's kill count to zero
        int[] kills = new int[playerCount];

        // Assign each kill to one randomly selected player
        for (int assignedKills = 0; assignedKills < totalKills; assignedKills++) {
            int playerIndex =
                    ThreadLocalRandom.current().nextInt(playerCount);

            kills[playerIndex]++;
        }

        return kills;
    }

    // Randomly select which players died in the match
    private int[] generateDeaths(int playerCount, int deathCount) {

        // Each position represents one player,
        // 0 means alive and 1 means dead
        int[] deaths = new int[playerCount];

        int assignedDeaths = 0;

        // Continue until the required number of different players are dead
        while (assignedDeaths < deathCount) {
            // Randomly pick one player from the team
            int playerIndex = ThreadLocalRandom.current().nextInt(playerCount);

            // Skip players who were already marked dead
            if (deaths[playerIndex] == 1) {
                continue;
            }

            // Mark the selected alive player as dead
            deaths[playerIndex] = 1;

            assignedDeaths++;
        }

        return deaths;
    }

    // Build one team's match result from its roster and generated stats
    private ArenaMatchCompleted.Team buildTeamResult(
            MatchCandidateResponse team,
            int[] kills,
            int[] deaths)
    {
        // Build the K/D/A result for each player
        List<ArenaMatchCompleted.Player> players = new ArrayList<>();

        int teamKills = calculateTotalKills(kills);

        // Build each roster player's kills, deaths, and assists result
        for (int playerIndex = 0; playerIndex < team.playerIds().size(); playerIndex++) {

            // A player can only assist with kills made by their teammates
            int maximumAssists = teamKills - kills[playerIndex];

            int assists = ThreadLocalRandom.current()
                    .nextInt(maximumAssists + 1);

            players.add(new ArenaMatchCompleted.Player(
                    team.playerIds().get(playerIndex),
                    kills[playerIndex],
                    deaths[playerIndex],
                    assists));
        }

        // Combine the team ID with its generated player stats
        return new ArenaMatchCompleted.Team(team.teamId(), players);
    }

    // Add every player's kills to get the team total
    private int calculateTotalKills(int[] kills) {
        int totalKills = 0;

        for (int playerKills : kills) {
            totalKills += playerKills;
        }

        return totalKills;
    }

}
