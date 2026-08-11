package com.yanfan.arena.platform.team;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    @GetMapping("/{teamId}")
    public TeamResponse get(@PathVariable Long teamId) {
        return teamService.get(teamId);
    }

    @PutMapping("/{teamId}/roster")
    public TeamResponse replaceRoster(
            @PathVariable Long teamId,
            @Valid @RequestBody ReplaceRosterRequest request
    ) {
        return teamService.replaceRoster(teamId, request);
    }

    @PostMapping("/{teamId}/activate")
    public TeamResponse activateTeam(@PathVariable Long teamId) {
        return teamService.activate(teamId);
    }

    @PostMapping("/{teamId}/retire")
    public TeamResponse retireTeam(@PathVariable Long teamId) {
        return teamService.retire(teamId);
    }




}
