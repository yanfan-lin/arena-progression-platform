package com.yanfan.arena.platform.leaderboard.api;

import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.leaderboard.service.TeamLeaderboardService;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.springframework.web.bind.annotation.*;

// Expose team leaderboards through the API
@RestController
@RequestMapping("/api/v1/leaderboards/teams")
public class TeamLeaderboardController {

    private final TeamLeaderboardService leaderboardService;

    public TeamLeaderboardController(TeamLeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    // Return the requested top team leaderboard
    @GetMapping
    public TeamLeaderboardResponse getTop(
            @RequestParam ArenaMode mode,
            @RequestParam(defaultValue = "RATING") TeamLeaderboardMetric metric,
            @RequestParam(defaultValue = "10") int limit)
    {
        return leaderboardService.getTop(mode, metric, limit);
    }

    // Return one active team's exact leaderboard rank
    @GetMapping("/{teamId}/rank")
    public TeamLeaderboardEntryResponse getRank(
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "RATING") TeamLeaderboardMetric metric)
    {
        return leaderboardService.getRank(teamId, metric);
    }

}
