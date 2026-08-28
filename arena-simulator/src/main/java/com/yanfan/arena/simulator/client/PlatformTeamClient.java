package com.yanfan.arena.simulator.client;

import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.simulator.config.PlatformClientProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

// Read teams and locked rosters from the platform
@Component
public class PlatformTeamClient {

    private final RestClient restClient;

    @Autowired
    public PlatformTeamClient(
            RestClient.Builder restClientBuilder,
            PlatformClientProperties properties)
    {
        // Configure the platform address once so methods only need endpoint paths
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl().toString())
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


}
