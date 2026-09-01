package com.yanfan.arena.platform.team.api;

import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.service.TeamService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Expose active teams and rosters to the arena simulator.
@RestController
@RequestMapping("/api/internal/simulator")
public class SimulatorTeamController {

    private final TeamService teamService;

    public SimulatorTeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping("/match-candidates")
    public List<MatchCandidateResponse> getMatchCandidates(@RequestParam ArenaMode mode) {
        return teamService.getMatchCandidates(mode);
    }

}
