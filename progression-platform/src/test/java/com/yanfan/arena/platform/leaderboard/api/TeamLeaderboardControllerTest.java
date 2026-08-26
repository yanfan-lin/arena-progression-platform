package com.yanfan.arena.platform.leaderboard.api;

import com.yanfan.arena.platform.error.BadRequestException;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.leaderboard.service.TeamLeaderboardService;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verify team leaderboard endpoints
@WebMvcTest(TeamLeaderboardController.class)
public class TeamLeaderboardControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TeamLeaderboardService leaderboardService;

    // Convert query parameters and return the top leaderboard as JSON
    @Test
    void getTopReturnsLeaderboard() throws Exception {

        TeamLeaderboardResponse response =
                new TeamLeaderboardResponse(
                        ArenaMode.THREE_VS_THREE,
                        TeamLeaderboardMetric.WINS,
                        List.of(
                                new TeamLeaderboardEntryResponse(
                                        1L,
                                        10L,
                                        "Example Team",
                                        1500,
                                        6,
                                        4,
                                        60.0
                                )
                        )
                );

        when(leaderboardService.getTop(
                ArenaMode.THREE_VS_THREE,
                TeamLeaderboardMetric.WINS,
                10
        ))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/leaderboards/teams")
                        .param("mode", "THREE_VS_THREE")
                        .param("metric", "WINS")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode")
                        .value("THREE_VS_THREE"))
                .andExpect(jsonPath("$.metric")
                        .value("WINS"))
                .andExpect(jsonPath("$.entries[0].rank")
                        .value(1))
                .andExpect(jsonPath("$.entries[0].teamId")
                        .value(10))
                .andExpect(jsonPath("$.entries[0].winRate")
                        .value(60.0));
    }

    // Convert the team path and metric parameter for an exact-rank request
    @Test
    void getRankReturnsTeamRank() throws Exception {
        TeamLeaderboardEntryResponse response =
                new TeamLeaderboardEntryResponse(
                        3L,
                        10L,
                        "Example Team",
                        1500,
                        6,
                        4,
                        60.0
                );

        when(leaderboardService.getRank(
                10L,
                TeamLeaderboardMetric.WIN_RATE
        ))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/leaderboards/teams/{teamId}/rank",
                        10L)
                        .param("metric", "WIN_RATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank")
                        .value(3))
                .andExpect(jsonPath("$.teamId")
                        .value(10));
    }

    // Map an unsupported leaderboard limit to a 400 response
    @Test
    void getTopReturnsBadRequestForUnsupportedLimit() throws Exception {
        when(leaderboardService.getTop(
                ArenaMode.THREE_VS_THREE,
                TeamLeaderboardMetric.RATING,
                20
        ))
                .thenThrow(new BadRequestException(
                        "INVALID_LEADERBOARD_LIMIT",
                        "Limit must be 10, 30, 50, or 100"));

        mockMvc.perform(get("/api/v1/leaderboards/teams")
                        .param("mode", "THREE_VS_THREE")
                        .param("metric", "RATING")
                        .param("limit", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_LEADERBOARD_LIMIT"))
                .andExpect(jsonPath("$.detail")
                        .value("Limit must be 10, 30, 50, or 100"));
    }

}
