package com.yanfan.arena.platform.leaderboard.redis;

import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMember;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardScore;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;


// Read and update Redis team leaderboards and report Redis failures
@Component
public class TeamLeaderboardRedisStore {

    private static final Logger log =
            LoggerFactory.getLogger(TeamLeaderboardRedisStore.class);

    private final StringRedisTemplate redisTemplate;

    private final TeamLeaderboardProjectionHealth projectionHealth;

    @Autowired
    public TeamLeaderboardRedisStore(
            StringRedisTemplate redisTemplate,
            TeamLeaderboardProjectionHealth projectionHealth)
    {
        this.redisTemplate = redisTemplate;
        this.projectionHealth = projectionHealth;
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
            // Mark Redis as degraded
            projectionHealth.markDegraded();

            log.warn(
                    "Failed to update team leaderboard: teamId={} cause={}",
                    team.getTeamId(),
                    exception.getClass().getSimpleName());
        }
    }

    public Optional<List<Long>> findTopTeamIds(
            ArenaMode mode,
            TeamLeaderboardMetric metric,
            int limit)
    {
        try {
            // Read members from the highest score downward.
            Set<String> members =
                    redisTemplate.opsForZSet().reverseRange(
                            TeamLeaderboardKey.from(mode, metric),
                            0,
                            limit - 1L
                    );

            if (members == null || members.isEmpty()) {
                return Optional.empty();
            }

            List<Long> teamIds = new ArrayList<>(members.size());

            for (String member : members) {
                teamIds.add(TeamLeaderboardMember.toTeamId(member));
            }

            return Optional.of(teamIds);
        }
        catch (DataAccessException | NumberFormatException exception) {
            // Mark Redis as degraded
            projectionHealth.markDegraded();

            log.warn(
                    "Failed to read team leaderboard: mode={} metric={} cause={}",
                    mode,
                    metric,
                    exception.getClass().getSimpleName()
            );

            return Optional.empty();
        }
    }

    public Optional<Long> findRank(
            ArenaMode mode,
            TeamLeaderboardMetric metric,
            long teamId)
    {
        try {
            Long zeroBasedRank =
                    redisTemplate.opsForZSet().reverseRank(
                            TeamLeaderboardKey.from(mode, metric),
                            TeamLeaderboardMember.fromTeamId(teamId)
                    );

            if (zeroBasedRank == null) {
                return Optional.empty();
            }

            // Redis starts rank at zero, the API starts at one
            return Optional.of(zeroBasedRank + 1L);
        }
        catch (DataAccessException exception) {
            // Mark Redis as degraded
            projectionHealth.markDegraded();

            log.warn(
                    "Failed to read team rank: teamId={} metric={} cause={}",
                    teamId,
                    metric,
                    exception.getClass().getSimpleName()
            );

            return Optional.empty();
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
            // Mark Redis as degraded
            projectionHealth.markDegraded();

            log.warn(
                    "Failed to remove team from leaderboard: teamId={} cause={}",
                    team.getTeamId(),
                    exception.getClass().getSimpleName());
        }
    }

}
