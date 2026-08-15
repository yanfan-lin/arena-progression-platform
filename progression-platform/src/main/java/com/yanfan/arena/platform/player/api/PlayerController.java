package com.yanfan.arena.platform.player.api;

import com.yanfan.arena.platform.player.service.PlayerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

// REST endpoints for the player operations
@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

    private final PlayerService playerService;

    @Autowired
    // Constructor injection
    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> create(
            @Valid @RequestBody CreatePlayerRequest request,
            UriComponentsBuilder uriBuilder
    ) {
        PlayerResponse response = playerService.create(request);

        URI location = uriBuilder.path("/api/v1/players/{playerId}")
                .buildAndExpand(response.playerId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{playerId}")
    public PlayerResponse get(@PathVariable Long playerId) {
        return playerService.get(playerId);
    }

    @PostMapping("/{playerId}/retire")
    public PlayerResponse retire(@PathVariable Long playerId) {
        return playerService.retire(playerId);
    }

}
