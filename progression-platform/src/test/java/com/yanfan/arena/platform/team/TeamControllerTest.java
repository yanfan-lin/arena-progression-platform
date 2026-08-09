package com.yanfan.arena.platform.team;

import com.yanfan.arena.platform.common.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TeamController.class)
class TeamControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TeamService teamService;


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
                        Instant.now()
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


}