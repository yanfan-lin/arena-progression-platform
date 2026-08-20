package com.yanfan.arena.platform.player.api;

import com.yanfan.arena.platform.api.PageResponse;
import com.yanfan.arena.platform.error.ConflictException;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.match.api.MatchOutcome;
import com.yanfan.arena.platform.player.domain.PlayerStatus;
import com.yanfan.arena.platform.player.service.PlayerMatchHistoryService;
import com.yanfan.arena.platform.player.service.PlayerService;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlayerController.class)
class PlayerControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PlayerService playerService;

    @MockitoBean
    PlayerMatchHistoryService playerMatchHistoryService;

    @Test
    void createReturns201WithLocation() throws Exception {
        PlayerResponse response = new PlayerResponse(
                1L,
                "ArenaExamplePlayer",
                PlayerStatus.ACTIVE,
                0,
                1,
                java.time.Instant.now(),
                java.time.Instant.now()
        );

        when(playerService.create(any(CreatePlayerRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"ArenaExamplePlayer\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/players/1"))
                .andExpect(jsonPath("$.displayName").value("ArenaExamplePlayer"));
    }

    @Test
    void createReturns400ForBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.displayName").exists());
    }

    @Test
    void createReturns409ForDuplicateName() throws Exception {
        when(playerService.create(any(CreatePlayerRequest.class)))
                .thenThrow(new ConflictException("PLAYER_NAME_TAKEN",
                        "A player with this display name already exists"));

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"ArenaExamplePlayer\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAYER_NAME_TAKEN"));
    }

    @Test
    void getReturns200ForExistingPlayer() throws Exception {
        when(playerService.get(1L))
                .thenReturn(new PlayerResponse(
                                1L,
                                "ArenaExamplePlayer",
                                PlayerStatus.ACTIVE,
                                0,
                                1,
                                java.time.Instant.now(),
                                java.time.Instant.now()
                        )
                );

        mockMvc.perform(get("/api/v1/players/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("ArenaExamplePlayer"));
    }

    @Test
    void getReturns400ForNonNumericPlayerId() throws Exception {
        mockMvc.perform(get("/api/v1/players/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturns404ForUnknownPlayer() throws Exception {
        when(playerService.get(99L))
                .thenThrow(new ResourceNotFoundException("PLAYER_NOT_FOUND", "Player not found"));

        mockMvc.perform(get("/api/v1/players/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    @Test
    void retireReturns200() throws Exception {
        when(playerService.retire(1L))
                .thenReturn(new PlayerResponse(
                                1L,
                                "ArenaExamplePlayer",
                                PlayerStatus.RETIRED,
                                0,
                                1,
                                java.time.Instant.now(),
                                java.time.Instant.now()
                        )
                );

        mockMvc.perform(post("/api/v1/players/1/retire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"));
    }

    // Return stored player history using the default pagination values
    @Test
    void getMatchHistoryReturnsStoredPage() throws Exception {
        Long playerId = 7L;

        UUID matchId = UUID.fromString(
                "11111111-1111-1111-1111-111111111111");

        PlayerMatchHistoryResponse historyEntry =
                new PlayerMatchHistoryResponse(
                        matchId,
                        ArenaMode.THREE_VS_THREE,
                        Instant.parse("2026-08-19T12:00:00Z"),
                        playerId,
                        "Stored Player",
                        10L,
                        "Stored Winners",
                        MatchOutcome.WIN,
                        1000,
                        16,
                        1016,
                        8,
                        2,
                        5,
                        150
                );

        PageResponse<PlayerMatchHistoryResponse> response =
                new PageResponse<>(
                        List.of(historyEntry),
                        0,
                        20,
                        1,
                        1
                );

        when(playerMatchHistoryService.getHistory(playerId, 0, 20))
                .thenReturn(response);

        mockMvc.perform(get(
                        "/api/v1/players/{playerId}/matches",
                        playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].matchId")
                        .value(matchId.toString()))
                .andExpect(jsonPath("$.content[0].playerName")
                        .value("Stored Player"))
                .andExpect(jsonPath("$.content[0].outcome")
                        .value("WIN"))
                .andExpect(jsonPath("$.page")
                        .value(0))
                .andExpect(jsonPath("$.size")
                        .value(20))
                .andExpect(jsonPath("$.totalElements")
                        .value(1));
    }

    // Reject a page size above API limit (100)
    @Test
    void getMatchHistoryRejectsExcessivePageSize() throws Exception {
        mockMvc.perform(get("/api/v1/players/7/matches")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());

        // Stop before calling the service for invalid pagination
        verifyNoInteractions(playerMatchHistoryService);
    }

}
