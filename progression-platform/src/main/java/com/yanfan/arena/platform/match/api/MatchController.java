package com.yanfan.arena.platform.match.api;

import com.yanfan.arena.platform.match.service.MatchQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Expose match details through API
@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    private final MatchQueryService matchQueryService;

    public MatchController(MatchQueryService matchQueryService) {
        this.matchQueryService = matchQueryService;
    }

    @GetMapping("/{matchId}")
    public MatchDetailsResponse get(@PathVariable UUID matchId) {
        return matchQueryService.get(matchId);
    }

}
