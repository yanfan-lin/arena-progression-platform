package com.yanfan.arena.platform.progression;

// Calculates team rating changes after a match
// A team's rating goes up when it wins and down when it loses

// Beating a much weaker team gains few points, losing to a much weaker team loses more

public final class EloPolicy {

    // K controls how many points move after one match.
    // Bigger K means ratings change faster.
    public static final int K_FACTOR = 32;

    // A 400-point rating difference gives the stronger team about a 91% expected score
    public static final int SCALE = 400;

    // Limit the rating difference used by the Elo calculation to 400 points
    public static final int MAX_RATING_DIFFERENCE = 400;

    // The result of one match:
    // the winner team gains points and the loser loses exactly the same number,
    // so total points stay balanced
    public record RatingChange(int winnerRatingAfter, int loserRatingAfter) {

    }

    private EloPolicy() {

    }

    public static RatingChange calculate(int winnerRating, int loserRating) {
        // Use loser minus winner so a positive value means the winner was the underdog.
        int effectiveDifference = clamp(
                loserRating - winnerRating,
                -MAX_RATING_DIFFERENCE,
                MAX_RATING_DIFFERENCE);

        // Calculate the winner's expected score before the match:
        // Equal ratings produce 0.5; 1400 vs 1000 produces about 0.91
        double expectedWinner = 1.0 / (1.0 + Math.pow(10.0, effectiveDifference / (double) SCALE));

        // The winner gains K x (1 - expected) points. Winning when already the
        // stronger side gives little; winning as the underdog gives a lot
        long calculatedChange = Math.round(K_FACTOR * (1.0 - expectedWinner));

        // A team can never drop below 0 rating, so the loser's loss is capped
        // at their current rating
        int appliedChange = (int) Math.min(calculatedChange, loserRating);

        return new RatingChange(
                Math.addExact(winnerRating, appliedChange),
                Math.addExact(loserRating, -appliedChange)
        );

    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }


}