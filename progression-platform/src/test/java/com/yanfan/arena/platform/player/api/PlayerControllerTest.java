package com.yanfan.arena.platform.player.api;

import com.yanfan.arena.platform.api.PageResponse;
import com.yanfan.arena.platform.error.ConflictException;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.player.service.PlayerMatchHistoryService;
import com.yanfan.arena.platform.player.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
    void createReturns400ForBlankName() throws Exception {

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.displayName")
                        .exists());
    }

    @Test
    void createReturns409ForDuplicateName() throws Exception {

        when(playerService.create(any(CreatePlayerRequest.class)))
                .thenThrow(new ConflictException(
                        "PLAYER_NAME_TAKEN",
                        "A player with this display name already exists")
                );

        mockMvc.perform(post("/api/v1/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"ArenaExamplePlayer\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("PLAYER_NAME_TAKEN"));
    }

    @Test
    void getReturns404ForUnknownPlayer() throws Exception {

        when(playerService.get(99L))
                .thenThrow(new ResourceNotFoundException("PLAYER_NOT_FOUND", "Player not found"));

        mockMvc.perform(get("/api/v1/players/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("PLAYER_NOT_FOUND"));
    }

    // Use default pagination for player match history requests
    @Test
    void getMatchHistoryUsesDefaultPagination() throws Exception {

        Long playerId = 7L;

        PageResponse<PlayerMatchHistoryResponse> response =
                new PageResponse<>(
                        List.of(),
                        0,
                        20,
                        0,
                        0
                );

        when(playerMatchHistoryService.getHistory(playerId, 0, 20))
                .thenReturn(response);

        mockMvc.perform(get(
                        "/api/v1/players/{playerId}/matches",
                        playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page")
                        .value(0))
                .andExpect(jsonPath("$.size")
                        .value(20));
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
