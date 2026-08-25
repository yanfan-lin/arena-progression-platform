package com.yanfan.arena.platform.leaderboard.redis;

import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMember;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardScore;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


// Store current team leaderboard scores in Redis
@Component
public class TeamLeaderboardRedisStore {

    private static final Logger log =
            LoggerFactory.getLogger(TeamLeaderboardRedisStore.class);

    private final StringRedisTemplate redisTemplate;

    @Autowired
    public TeamLeaderboardRedisStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void update(Team team) {
        if (team.getStatus() != TeamStatus.ACTIVE) {
            remove(team);

            return;
        }

        String member =
                TeamLeaderboardMember.fromTeamId(team.getTeamId());

        try {
            for (TeamLeaderboardMetric metric : TeamLeaderboardMetric.values()) {
                long score = TeamLeaderboardScore.calculate(
                        metric,
                        team.getRating(),
                        team.getWins(),
                        team.getMatchesPlayed());

                // Replace the previous score instead of adding to it
                redisTemplate.opsForZSet().add(
                        TeamLeaderboardKey.from(team.getMode(), metric),
                        member,
                        score);
            }
        }
        catch (DataAccessException exception) {
            log.warn(
                    "Failed to update team leaderboard: teamId={} cause={}",
                    team.getTeamId(),
                    exception.getClass().getSimpleName());
        }
    }

    public void remove(Team team) {
        String member =
                TeamLeaderboardMember.fromTeamId(team.getTeamId());

        try {
            for (TeamLeaderboardMetric metric : TeamLeaderboardMetric.values()) {
                redisTemplate.opsForZSet().remove(
                        TeamLeaderboardKey.from(team.getMode(), metric),
                        member);
            }
        }
        catch (DataAccessException exception) {
            log.warn(
                    "Failed to remove team from leaderboard: teamId={} cause={}",
                    team.getTeamId(),
                    exception.getClass().getSimpleName());
        }
    }

}
