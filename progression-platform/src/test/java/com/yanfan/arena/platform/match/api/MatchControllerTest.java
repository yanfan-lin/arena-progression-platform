package com.yanfan.arena.platform.match.api;

import com.yanfan.arena.platform.match.service.MatchQueryService;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchController.class)
class MatchControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MatchQueryService matchQueryService;

    // Return one complete match response through the public endpoint
    @Test
    void getReturnsMatchDetails() throws Exception{
        UUID matchId = UUID.fromString(
                "11111111-1111-1111-1111-111111111111");

        MatchDetailsResponse response = new MatchDetailsResponse(
                matchId,
                ArenaMode.THREE_VS_THREE,
                10L,
                1,
                Instant.parse("2026-08-19T12:00:00Z"),
                List.of(
                        new MatchTeamResponse(
                                10L,
                                "Stored Winners",
                                MatchOutcome.WIN,
                                1000,
                                16,
                                1016
                        )
                ),
                List.of(
                        new MatchParticipantResponse(
                                1L,
                                10L,
                                "Stored Player",
                                8,
                                2,
                                5,
                                150
                        )
                )
        );

        when(matchQueryService.get(matchId))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/matches/{matchId}", matchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId")
                        .value(matchId.toString()))
                .andExpect(jsonPath("$.teams[0].teamName")
                        .value("Stored Winners"))
                .andExpect(jsonPath("$.teams[0].outcome")
                        .value("WIN"))
                .andExpect(jsonPath("$.participants[0].playerName")
                        .value("Stored Player"))
                .andExpect(jsonPath("$.participants[0].xpEarned")
                        .value(150));
    }


}
