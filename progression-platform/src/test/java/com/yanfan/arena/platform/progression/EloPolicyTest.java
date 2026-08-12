package com.yanfan.arena.platform.progression;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

// Cover equal ratings, favorites, underdogs, the rating-gap cap, and the zero-rating floor.
class EloPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "1000, 1000, 1016, 984",
            "1400, 1000, 1403, 997",
            "1000, 1400, 1029, 1371",
            "2000, 1000, 2003, 997",
            "1000, 2000, 1029, 1971",
            "1000, 2, 1002, 0"
    })
    void calculatesNewRatings(int winnerRating,
                              int loserRating,
                              int expectedWinnerAfter,
                              int expectedLoserAfter
    ) {
        EloPolicy.RatingChange change = EloPolicy.calculate(winnerRating, loserRating);

        assertThat(change.winnerRatingAfter()).isEqualTo(expectedWinnerAfter);
        assertThat(change.loserRatingAfter()).isEqualTo(expectedLoserAfter);
    }

    @Test
    void changeIsZeroSum() {
        EloPolicy.RatingChange change = EloPolicy.calculate(1000, 1000);

        int winnerGain = change.winnerRatingAfter() - 1000;
        int loserLoss = 1000 - change.loserRatingAfter();

        assertThat(winnerGain).isEqualTo(loserLoss);
    }


}