package com.yanfan.arena.platform.leaderboard.service;

import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardMetric;
import com.yanfan.arena.platform.leaderboard.TeamLeaderboardScore;
import com.yanfan.arena.platform.leaderboard.api.TeamLeaderboardEntryResponse;
import com.yanfan.arena.platform.leaderboard.api.TeamLeaderboardResponse;
import com.yanfan.arena.platform.leaderboard.persistence.TeamLeaderboardRepository;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import com.yanfan.arena.platform.team.persistence.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// Read team leaderboards from MySQL when Redis is unavailable
// or missing leaderboard data
@Service
@Transactional(readOnly = true)
public class TeamLeaderboardFallbackService {

    private final TeamLeaderboardRepository leaderboardRepository;

    private final TeamRepository teamRepository;

    @Autowired
    public TeamLeaderboardFallbackService(
            TeamLeaderboardRepository leaderboardRepository,
            TeamRepository teamRepository)
    {
        this.leaderboardRepository = leaderboardRepository;
        this.teamRepository = teamRepository;
    }

    public TeamLeaderboardResponse getTop(
            ArenaMode mode,
            TeamLeaderboardMetric metric,
            int limit)
    {
        // Native queries compare the enum names as SQL strings
        List<Team> teams = leaderboardRepository.findTop(
                mode.name(),
                metric.name(),
                limit
        );

        List<TeamLeaderboardEntryResponse> entries =
                new ArrayList<>(teams.size());

        long rank = 1L;

        for (Team team : teams) {
            entries.add(TeamLeaderboardEntryResponse.from(rank, team));

            rank++;
        }

        return new TeamLeaderboardResponse(mode, metric, entries);
    }

    // A team's rank is the number of teams ahead of it plus one
    public TeamLeaderboardEntryResponse getRank(
            Long teamId,
            TeamLeaderboardMetric metric)
    {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEAM_NOT_FOUND",
                        "Team not found"
                ));

        // Team exists but has no leaderboard rank
        if (team.getStatus() != TeamStatus.ACTIVE) {
            throw new ResourceNotFoundException(
                    "TEAM_NOT_RANKED",
                    "Team is not on an active leaderboard"
            );
        }

        long score = TeamLeaderboardScore.calculate(
                metric,
                team.getRating(),
                team.getWins(),
                team.getMatchesPlayed()
        );

        long rank = leaderboardRepository.countTeamsAhead(
                team.getMode().name(),
                metric.name(),
                score,
                teamId) + 1L;

        return TeamLeaderboardEntryResponse.from(rank, team);
    }

}
