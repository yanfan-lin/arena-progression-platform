package com.yanfan.arena.platform.team.api;

import com.yanfan.arena.platform.api.PageResponse;
import com.yanfan.arena.platform.error.ConflictException;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import com.yanfan.arena.platform.team.service.TeamMatchHistoryService;
import com.yanfan.arena.platform.team.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void createReturns201WithLocation() throws Exception {
        when(teamService.create(any(CreateTeamRequest.class)))
                .thenReturn(new TeamResponse(
                        1L,
                        "ExampleTeam",
                        ArenaMode.THREE_VS_THREE,
                        TeamStatus.DRAFT,
                        null,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        Instant.now(),
                        Instant.now(),
                        List.of()
                ));

        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ExampleTeam\",\"mode\":\"THREE_VS_THREE\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/teams/1"))
                .andExpect(jsonPath("$.name").value("ExampleTeam"));
    }

    @Test
    void createReturns400ForBlankName() throws Exception {
        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"mode\":\"THREE_VS_THREE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createReturns409ForDuplicateName() throws Exception {
        when(teamService.create(any(CreateTeamRequest.class)))
                .thenThrow(new ConflictException("TEAM_NAME_TAKEN",
                        "A team with this name already exists in this mode"));

        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ArenaForce\",\"mode\":\"THREE_VS_THREE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_NAME_TAKEN"));
    }

    @Test
    void getReturns200WithRoster() throws Exception {
        when(teamService.get(1L))
                .thenReturn(new TeamResponse(
                        1L,
                        "ExampleTeam",
                        ArenaMode.THREE_VS_THREE,
                        TeamStatus.DRAFT,
                        null,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        Instant.now(),
                        Instant.now(),
                        List.of(10L, 11L, 12L)
                ));

        mockMvc.perform(get("/api/v1/teams/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ExampleTeam"))
                .andExpect(jsonPath("$.playerIds[0]").value(10));
    }

    @Test
    void getReturns404ForUnknownTeam() throws Exception {
        when(teamService.get(99L))
                .thenThrow(new ResourceNotFoundException("TEAM_NOT_FOUND", "Team not found"));

        mockMvc.perform(get("/api/v1/teams/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    void replaceReturns200() throws Exception {
        when(teamService.replaceRoster(eq(1L), any(ReplaceRosterRequest.class)))
                .thenReturn(new TeamResponse(
                        1L,
                        "ExampleTeam",
                        ArenaMode.THREE_VS_THREE,
                        TeamStatus.DRAFT,
                        null,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        Instant.now(),
                        Instant.now(),
                        List.of()
                ));

        mockMvc.perform(put("/api/v1/teams/1/roster")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerIds\":[10,11]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ExampleTeam"));
    }

    @Test
    void activateReturns200() throws Exception {
        when(teamService.activate(1L))
                .thenReturn(new TeamResponse(
                        1L,
                        "ExampleTeam",
                        ArenaMode.THREE_VS_THREE,
                        TeamStatus.ACTIVE,
                        1000,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        Instant.now(),
                        Instant.now(),
                        List.of()
                ));

        mockMvc.perform(post("/api/v1/teams/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rating").value(1000));

    }

    @Test
    void retireReturns200() throws Exception {
        when(teamService.retire(1L))
                .thenReturn(new TeamResponse(
                        1L,
                        "ExampleTeam",
                        ArenaMode.THREE_VS_THREE,
                        TeamStatus.RETIRED,
                        1000,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        Instant.now(),
                        Instant.now(),
                        List.of(10L, 11L, 12L)
                ));

        mockMvc.perform(post("/api/v1/teams/1/retire"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETIRED"));

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
