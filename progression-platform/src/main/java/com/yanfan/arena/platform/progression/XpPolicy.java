package com.yanfan.arena.platform.progression;

// Player's XP and level rules
public class XpPolicy {

    public static final long COMPLETION_XP = 100;
    public static final long WIN_BONUS_XP = 50;
    public static final long XP_PER_LEVEL = 1000;

    private XpPolicy() {

    }

    // XP gained by player for playing an arena match.
    // Wins receive a fixed win bonus XP
    // Kills, deaths, and assists do not affect XP
    public static long xpEarned(boolean won) {
        return COMPLETION_XP + (won ? WIN_BONUS_XP : 0);
    }

    // Level is calculated based on cumulative XP: 1 + floor(totalXp / 1000)
    // integer division is safe because totalXp is always nonnegative
    public static int levelFor(long totalXp) {
        return 1 + (int) (totalXp / XP_PER_LEVEL);
    }


}
