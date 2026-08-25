package com.yanfan.arena.platform.leaderboard.persistence;

import com.yanfan.arena.platform.team.domain.Team;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

// MySQL fallback queries for team leaderboard reads
public interface TeamLeaderboardRepository extends Repository<Team, Long> {

    // Rank active teams by the selected metric, then by the larger team ID
    @Query(
            value = """
                    SELECT t.*
                    FROM teams t
                    WHERE t.mode = :mode
                      AND t.status = 'ACTIVE'
                    ORDER BY
                        CASE :metric
                            WHEN 'RATING' THEN t.rating
                            WHEN 'WINS' THEN t.wins
                            WHEN 'WIN_RATE' THEN
                                CASE
                                    WHEN t.matches_played = 0 THEN 0
                                    ELSE FLOOR(t.wins * 10000 / t.matches_played)
                                END
                        END DESC,
                        t.team_id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Team> findTop(
            String mode,
            String metric,
            int limit
    );

    // Count teams ranked before one team to calculate its exact rank
    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT t.team_id,
                               CASE :metric
                                   WHEN 'RATING' THEN t.rating
                                   WHEN 'WINS' THEN t.wins
                                   WHEN 'WIN_RATE' THEN
                                       CASE
                                           WHEN t.matches_played = 0 THEN 0
                                           ELSE FLOOR(t.wins * 10000 / t.matches_played)
                                       END
                               END AS metric_score
                        FROM teams t
                        WHERE t.mode = :mode
                          AND t.status = 'ACTIVE'
                    ) ranked
                    WHERE ranked.metric_score > :score
                       OR (
                           ranked.metric_score = :score
                           AND ranked.team_id > :teamId
                       )
                    """,
            nativeQuery = true
    )
    long countTeamsAhead(
            String mode,
            String metric,
            long score,
            long teamId
    );

}