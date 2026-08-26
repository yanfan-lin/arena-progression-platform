package com.yanfan.arena.platform.leaderboard.service;

import com.yanfan.arena.platform.error.BadRequestException;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.leaderboard.api.TeamLeaderboardEntryResponse;
import com.yanfan.arena.platform.leaderboard.api.TeamLeaderboardResponse;
import com.yanfan.arena.platform.leaderboard.redis.TeamLeaderboardRedisStore;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import com.yanfan.arena.platform.team.persistence.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

// Read team leaderboards from Redis with MySQL fallback
@Service
@Transactional(readOnly = true)
public class TeamLeaderboardService {

    // Limit sizes supported by the leaderboard API
    private static final Set<Integer> ALLOWED_LIMITS = Set.of(10, 30, 50, 100);

    private final TeamLeaderboardRedisStore redisStore;

    private final TeamLeaderboardFallbackService fallbackService;

    private final TeamRepository teamRepository;

    @Autowired
    public TeamLeaderboardService(
            TeamLeaderboardRedisStore redisStore,
            TeamLeaderboardFallbackService fallbackService,
            TeamRepository teamRepository)
    {
        this.redisStore = redisStore;
        this.fallbackService = fallbackService;
        this.teamRepository = teamRepository;
    }

    // Return top teams in Redis order, or
    // use MySQL result when Redis has no usable data
    public TeamLeaderboardResponse getTop(
            ArenaMode mode,
            TeamLeaderboardMetric metric,
            int limit)
    {
        validateLimit(limit);

        Optional<List<Long>> redisTeamIds =
                redisStore.findTopTeamIds(mode, metric, limit);

        if (redisTeamIds.isEmpty()) {
            return fallbackService.getTop(mode, metric, limit);
        }

        List<Long> teamIds = redisTeamIds.get();

        // MySQL may return teams in a different order, so map them by ID
        List<Team> teams = teamRepository.findAllById(teamIds);

        Map<Long, Team> teamsById = new HashMap<>();

        for (Team team : teams) {
            teamsById.put(team.getTeamId(), team);
        }

        List<TeamLeaderboardEntryResponse> entries = new ArrayList<>(teams.size());

        long rank = 1L;

        for (Long teamId : teamIds) {
            Team team = teamsById.get(teamId);

            // Use MySQL if Redis contains an outdated team member
            if (team == null
                    || team.getStatus() != TeamStatus.ACTIVE
                    || team.getMode() != mode)
            {
                return fallbackService.getTop(mode, metric, limit);
            }

            entries.add(TeamLeaderboardEntryResponse.from(rank, team));

            rank++;
        }

        return new TeamLeaderboardResponse(mode, metric, entries);
    }

    // Return an active team's exact rank,
    // using MySQL when Redis has no stored rank
    public TeamLeaderboardEntryResponse getRank(
            Long teamId,
            TeamLeaderboardMetric metric)
    {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEAM_NOT_FOUND",
                        "Team not found")
                );

        // Handle existing team without a rank on the leaderboard
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new ResourceNotFoundException(
                    "TEAM_NOT_RANKED",
                    "Team is not on an active leaderboard"
            );
        }

        Optional<Long> redisRank =
                redisStore.findRank(team.getMode(), metric, teamId);

        if (redisRank.isEmpty()) {
            return fallbackService.getRank(teamId, metric);
        }

        return TeamLeaderboardEntryResponse.from(redisRank.get(), team);
    }

    private static void validateLimit(int limit) {
        if (!ALLOWED_LIMITS.contains(limit)) {
            throw new BadRequestException(
                    "INVALID_LEADERBOARD_LIMIT",
                    "Limit must be 10, 30, 50, or 100");
        }
    }

}
