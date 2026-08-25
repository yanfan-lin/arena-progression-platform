package com.yanfan.arena.platform.leaderboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// Verify leaderboard metric scores and team ID tie ordering
class TeamLeaderboardRankingTest {

    @ParameterizedTest
    @CsvSource({
            "RATING, 1250, 7, 10, 1250",
            "WINS, 1250, 7, 10, 7",
            "WIN_RATE, 1250, 7, 10, 7000",
            "WIN_RATE, 1250, 0, 0, 0"
    })
    void calculateMetricScore (
            TeamLeaderboardMetric metric,
            int rating,
            int wins,
            int matchesPlayed,
            long expectedScore)
    {
        long score = TeamLeaderboardScore.calculate(
                metric,
                rating,
                wins,
                matchesPlayed
        );

        assertThat(score).isEqualTo(expectedScore);
    }

    @Test
    void formatsTeamIdsForRedisTieOrdering() {

        String lowerTeamId = TeamLeaderboardMember.fromTeamId(9L);

        String higherTeamId = TeamLeaderboardMember.fromTeamId(10L);

        // Redis places the larger team ID first when
        // teams have the same value for the selected ranking metric
        assertThat(higherTeamId.compareTo(lowerTeamId))
                .isPositive();

        assertThat(lowerTeamId)
                .hasSize(19);

        assertThat(TeamLeaderboardMember.toTeamId(higherTeamId))
                .isEqualTo(10L);
    }

}
