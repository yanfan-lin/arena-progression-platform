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
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    // Replace every live leaderboard in one Redis operation,
    // so live keys never contain a mix of old and rebuilt data
    private static final DefaultRedisScript<Long> REPLACE_LEADERBOARDS_SCRIPT =
            new DefaultRedisScript<>(
                    """
                            -- KEYS contains temporary and live keys in pairs
                            for i = 1, #KEYS, 2 do
                                if redis.call('EXISTS', KEYS[i]) == 1 then
                                    -- A completed temporary key can replace the live key
                                    redis.call('RENAME', KEYS[i], KEYS[i + 1])
                                else
                                    -- Remove old data when this leaderboard has no active teams
                                    redis.call('DEL', KEYS[i + 1])
                                end
                            end
                            
                            return 1
                            """,
                    Long.class
            );

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

        String member = TeamLeaderboardMember.fromTeamId(team.getTeamId());

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

    // Build temporary leaderboards so a failed rebuild leaves live data unchanged
    public boolean writeTemporaryLeaderboards(List<Team> teams, String rebuildId) {
        try {
            // Add each active team to a separate Redis sorted set for every ranking metric
            for (Team team : teams) {
                String member = TeamLeaderboardMember.fromTeamId(team.getTeamId());

                for (TeamLeaderboardMetric metric : TeamLeaderboardMetric.values()) {
                    long score = TeamLeaderboardScore.calculate(
                            metric,
                            team.getRating(),
                            team.getWins(),
                            team.getMatchesPlayed()
                    );

                    redisTemplate.opsForZSet().add(
                            TeamLeaderboardKey.temporaryKey(team.getMode(), metric, rebuildId),
                            member,
                            score);
                }
            }

            return true;
        }
        catch (DataAccessException e) {
            // Mark Redis unhealthy so leaderboard reads use MySQL
            projectionHealth.markDegraded();

            log.warn(
                    "Failed to build temporary team leaderboards: rebuildId={} cause={}",
                    rebuildId,
                    e.getClass().getSimpleName());

            return false;
        }
    }

    // Replace all live leaderboards only after the temporary data is complete
    public boolean replaceLiveLeaderboards(String rebuildId) {

        List<String> keys = new ArrayList<>();

        // Include every mode and metric so empty leaderboards also remove stale data
        for (ArenaMode mode : ArenaMode.values()) {
            for (TeamLeaderboardMetric metric : TeamLeaderboardMetric.values()) {
                // Keep each key pair in the order expected by the Lua script
                keys.add(TeamLeaderboardKey.temporaryKey(mode, metric, rebuildId));

                keys.add(TeamLeaderboardKey.from(mode, metric));
            }
        }

        try {
            // Run the Lua script to replace all live leaderboard keys at the same time
            Long result = redisTemplate.execute(REPLACE_LEADERBOARDS_SCRIPT, keys);

            if (result != null && result == 1L) {
                return true;
            }

            // Mark Redis unhealthy so leaderboard reads use MySQL
            projectionHealth.markDegraded();

            return false;
        }
        catch (DataAccessException exception) {
            // Mark Redis unhealthy so leaderboard reads use MySQL
            projectionHealth.markDegraded();

            log.warn(
                    "Failed to replace live team leaderboards: rebuildId={} cause={}",
                    rebuildId,
                    exception.getClass().getSimpleName());

            return false;
        }
    }

    // Remove unfinished rebuild data so failed attempts do not leave unused Redis keys
    public void deleteTemporaryLeaderboards(String rebuildId) {
        List<String> keys = new ArrayList<>();

        // Collect the temporary key for every mode and ranking metric
        for (ArenaMode mode : ArenaMode.values()) {
            for (TeamLeaderboardMetric metric : TeamLeaderboardMetric.values()) {
                keys.add(TeamLeaderboardKey.temporaryKey(
                        mode,
                        metric,
                        rebuildId));
            }
        }

        try {
            redisTemplate.delete(keys);
        }
        catch (DataAccessException exception) {
            log.warn(
                    "Failed to delete temporary team leaderboards: rebuildId={} cause={}",
                    rebuildId,
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
