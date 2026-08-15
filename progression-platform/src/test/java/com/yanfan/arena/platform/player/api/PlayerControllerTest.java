package com.yanfan.arena.platform.player.api;

import com.yanfan.arena.platform.error.ConflictException;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.player.domain.PlayerStatus;
import com.yanfan.arena.platform.player.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void retireReturns404ForUnknownPlayer() throws Exception {
        when(playerService.retire(99L))
                .thenThrow(new ResourceNotFoundException("PLAYER_NOT_FOUND", "Player not found"));

        mockMvc.perform(post("/api/v1/players/99/retire"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
    }

    @Test
    void retireReturns409ForPlayerOnActiveTeam() throws Exception {
        when(playerService.retire(1L))
                .thenThrow(new ConflictException("PLAYER_IN_ACTIVE_TEAM",
                        "Player is on an active team and cannot be retired"));

        mockMvc.perform(post("/api/v1/players/1/retire"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAYER_IN_ACTIVE_TEAM"));
    }


    @Test
    void unknownRouteReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound());
    }


}
