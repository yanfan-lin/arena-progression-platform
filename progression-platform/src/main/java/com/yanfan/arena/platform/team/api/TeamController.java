package com.yanfan.arena.platform.team.api;

import com.yanfan.arena.platform.api.PageResponse;
import com.yanfan.arena.platform.team.service.TeamMatchHistoryService;
import com.yanfan.arena.platform.team.service.TeamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

// Expose team lifecycle and match history through the API
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    private final TeamMatchHistoryService teamMatchHistoryService;

    public TeamController(TeamService teamService, TeamMatchHistoryService teamMatchHistoryService) {
        this.teamService = teamService;
        this.teamMatchHistoryService = teamMatchHistoryService;
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

    @GetMapping("/{teamId}/matches")
    public PageResponse<TeamMatchHistoryResponse> getMatchHistory(
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size)
    {
        return teamMatchHistoryService.getHistory(teamId, page, size);
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
