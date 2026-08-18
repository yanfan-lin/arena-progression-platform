package com.yanfan.arena.platform.progression;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class XpPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "false, 100",
            "true, 150"
    })
    void xpEarnedUsesCompletionAndWinBonus(boolean won, long expectedXp) {
        assertThat(XpPolicy.xpEarned(won))
                .isEqualTo(expectedXp);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1",
            "999, 1",
            "1000, 2",
            "1500, 2",
            "2500, 3",
            "100000, 101"
    })
    void levelIsDerivedFromTotalXp(long totalXp, int expectedLevel) {
        assertThat(XpPolicy.levelFor(totalXp))
                .isEqualTo(expectedLevel);
    }


}
