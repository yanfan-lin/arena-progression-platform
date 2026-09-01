package com.yanfan.arena.platform.team.api;

import com.yanfan.arena.platform.api.PageResponse;
import com.yanfan.arena.platform.team.service.TeamMatchHistoryService;
import com.yanfan.arena.platform.team.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TeamController.class)
class TeamControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TeamService teamService;

    @MockitoBean
    TeamMatchHistoryService teamMatchHistoryService;

    @Test
    void createReturns400ForBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"mode\":\"THREE_VS_THREE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // Use default pagination for team match history requests
    @Test
    void getMatchHistoryUsesDefaultPagination() throws Exception {

        Long teamId = 10L;

        PageResponse<TeamMatchHistoryResponse> response =
                new PageResponse<>(
                        List.of(),
                        0,
                        20,
                        0,
                        0
                );

        when(teamMatchHistoryService.getHistory(teamId, 0, 20))
                .thenReturn(response);

        mockMvc.perform(get(
                        "/api/v1/teams/{teamId}/matches",
                        teamId))
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

        mockMvc.perform(get("/api/v1/teams/10/matches")
                .param("size", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(teamMatchHistoryService);
    }

}
