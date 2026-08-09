package com.yanfan.arena.platform.team;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

// REST endpoints for the teams
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    @Autowired
    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<TeamResponse> create(
            @Valid @RequestBody CreateTeamRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        TeamResponse response = teamService.create(request);

        URI location = uriBuilder.path("/api/v1/teams/{teamId}")
                .buildAndExpand(response.teamId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }


}
