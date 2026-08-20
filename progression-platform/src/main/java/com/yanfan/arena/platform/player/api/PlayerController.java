package com.yanfan.arena.platform.player.api;

import com.yanfan.arena.platform.api.PageResponse;
import com.yanfan.arena.platform.player.service.PlayerMatchHistoryService;
import com.yanfan.arena.platform.player.service.PlayerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

// Expose player lifecycle and match history through the API
@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    private final PlayerMatchHistoryService playerMatchHistoryService;

    @Autowired
    public PlayerController(PlayerService playerService, PlayerMatchHistoryService playerMatchHistoryService) {
        this.playerService = playerService;
        this.playerMatchHistoryService = playerMatchHistoryService;
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> create(
            @Valid @RequestBody CreatePlayerRequest request,
            UriComponentsBuilder uriBuilder)
    {
        PlayerResponse response = playerService.create(request);

        URI location = uriBuilder.path("/api/v1/players/{playerId}")
                .buildAndExpand(response.playerId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/{playerId}/retire")
    public PlayerResponse retire(@PathVariable Long playerId) {
        return playerService.retire(playerId);
    }

    @GetMapping("/{playerId}")
    public PlayerResponse get(@PathVariable Long playerId) {
        return playerService.get(playerId);
    }

    @GetMapping("/{playerId}/matches")
    public PageResponse<PlayerMatchHistoryResponse> getMatchHistory(
            @PathVariable Long playerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size)
    {
        return playerMatchHistoryService.getHistory(playerId, page, size);
    }

}
