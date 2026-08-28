package com.yanfan.arena.simulator.simulation;

import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.simulator.client.PlatformClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Create the players and active teams needed for simulations
@Service
public class SimulationSetupService {

    private final PlatformClient platformClient;

    @Autowired
    public SimulationSetupService(PlatformClient platformClient) {
        this.platformClient = platformClient;
    }

    public SimulationSetupResponse setup(SimulationSetupRequest request) {

        MatchMode mode = request.mode();

        // Count existing teams and calculate how many more are needed
        int existingTeamCount =
                platformClient.getMatchCandidates(mode).size();

        int teamsToCreate =
                Math.max(0, request.targetTeamCount() - existingTeamCount);

        // Match each new roster to the requested arena mode
        int rosterSize =
                mode == MatchMode.THREE_VS_THREE ? 3 : 5;

        int createdPlayerCount = teamsToCreate * rosterSize;

        // Avoid write requests when the requested number of teams already exists
        if (teamsToCreate == 0) {
            return new SimulationSetupResponse(
                    mode,
                    request.targetTeamCount(),
                    existingTeamCount,
                    0,
                    0
            );
        }

        // Give this request unique names to avoid conflicts with earlier runs
        String simulationNamePrefix = "Simulation-"
                + (mode == MatchMode.THREE_VS_THREE ? "3v3-" : "5v5-")
                + UUID.randomUUID().toString().substring(0, 8);

        for (int teamNumber = 1; teamNumber <= teamsToCreate; teamNumber++) {

            // Create the players needed for one complete roster
            List<Long> playerIds = new ArrayList<>();

            for (int playerNumber = 1; playerNumber <= rosterSize; playerNumber++) {
                String displayName =
                        simulationNamePrefix + "-P" + teamNumber + "-" + playerNumber;

                playerIds.add(platformClient.createPlayer(displayName));
            }

            // Create the draft team, assign its roster, and then activate it
            Long teamId =
                    platformClient.createTeam(
                            simulationNamePrefix + "-T" + teamNumber,
                            mode);

            platformClient.replaceRoster(teamId, playerIds);

            platformClient.activateTeam(teamId);
        }

        return new SimulationSetupResponse(
                mode,
                request.targetTeamCount(),
                existingTeamCount,
                teamsToCreate,
                createdPlayerCount
        );
    }

}
