package com.yanfan.arena.simulator.client;

import com.yanfan.arena.contract.MatchMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

// Call platform APIs needed for the match simulation.
@Component
public class PlatformClient {

    private final RestClient restClient;

    public PlatformClient(
            RestClient.Builder restClientBuilder,
            @Value("${arena.simulator.platform.base-url}") String baseUrl)
    {
        // Configure the platform address once so methods only need endpoint paths
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    public List<MatchCandidateResponse> getMatchCandidates(MatchMode mode) {

        // Send the request and convert the returned JSON array into Java records
        MatchCandidateResponse[] candidates =
                restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/internal/simulator/match-candidates")
                                .queryParam("mode", mode.name())
                                .build())
                        .retrieve()
                        .body(MatchCandidateResponse[].class);

        // Return an empty list to avoid handling null response
        if (candidates == null) {
            return List.of();
        }

        return Arrays.asList(candidates);
    }

    public Long createPlayer(String displayName) {

        CreatedPlayerResponse response = restClient.post()
                .uri("/api/v1/players")
                .body(new CreatePlayerRequest(displayName))
                .retrieve()
                .body(CreatedPlayerResponse.class);

        if (response == null || response.playerId() == null) {
            throw new IllegalStateException("Platform returned no player ID");
        }

        return response.playerId();
    }

    public Long createTeam(String name, MatchMode mode) {

        // Use the platform API so created teams follow normal creation rules
        CreatedTeamResponse response = restClient.post()
                .uri("/api/v1/teams")
                .body(new CreateTeamRequest(name, mode))
                .retrieve()
                .body(CreatedTeamResponse.class);

        if (response == null || response.teamId() == null) {
            throw new IllegalStateException("Platform returned no team ID");
        }

        return response.teamId();
    }

    public void replaceRoster(Long teamId, List<Long> playerIds) {
        restClient.put()
                .uri("/api/v1/teams/{teamId}/roster", teamId)
                .body(new ReplaceRosterRequest(playerIds))
                .retrieve()
                .toBodilessEntity();
    }

    public void activateTeam(Long teamId) {
        restClient.post()
                .uri("/api/v1/teams/{teamId}/activate", teamId)
                .retrieve()
                .toBodilessEntity();
    }

    // Keep simulation-setup HTTP bodies private to this client
    private record CreatePlayerRequest(String displayName) {

    }

    private record CreatedPlayerResponse(Long playerId) {

    }

    private record CreateTeamRequest(String name, MatchMode mode) {

    }

    private record CreatedTeamResponse(Long teamId) {

    }

    private record ReplaceRosterRequest(List<Long> playerIds) {

    }

}
