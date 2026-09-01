package com.yanfan.arena.platform.team.service;

import com.yanfan.arena.platform.error.ConflictException;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.leaderboard.redis.TeamLeaderboardChangedEvent;
import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.player.persistence.PlayerRepository;
import com.yanfan.arena.platform.player.domain.PlayerStatus;
import com.yanfan.arena.platform.team.api.CreateTeamRequest;
import com.yanfan.arena.platform.team.api.MatchCandidateResponse;
import com.yanfan.arena.platform.team.api.ReplaceRosterRequest;
import com.yanfan.arena.platform.team.api.TeamResponse;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamMember;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import com.yanfan.arena.platform.team.persistence.TeamMemberRepository;
import com.yanfan.arena.platform.team.persistence.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Manage team lifecycle operations
@Service
public class TeamService {

    private final TeamRepository teamRepository;

    private final TeamMemberRepository teamMemberRepository;

    private final PlayerRepository playerRepository;

    private final Clock clock;

    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public TeamService(TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       PlayerRepository playerRepository,
                       Clock clock,
                       ApplicationEventPublisher eventPublisher)
    {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.playerRepository = playerRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TeamResponse create(CreateTeamRequest request) {

        String name = request.getName();

        // If the same team name already exists in this mode, stop and return 409
        if (teamRepository.existsByModeAndNameIgnoreCase(request.getMode(), name)) {
            throw new ConflictException("TEAM_NAME_TAKEN",
                    "A team with this name already exists in this mode");
        }

        Team team = new Team();
        team.setName(name);
        team.setMode(request.getMode());

        try {
            // Save the team now. If two requests use the same name at the same time,
            // the database rejects the second one
            return TeamResponse.from(teamRepository.saveAndFlush(team), List.of());
        }
        catch (DataIntegrityViolationException ex) {
            // The name was already saved by another request - return 409 just like above
            throw new ConflictException("TEAM_NAME_TAKEN",
                    "A team with this name already exists in this mode");
        }
    }

    @Transactional(readOnly = true)
    public TeamResponse get(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team not found"));

        // Roster IDs are sorted so the response is stable
        List<Long> playerIds = teamMemberRepository.findByTeamId(teamId)
                .stream()
                .map(TeamMember::getPlayerId)
                .sorted()
                .toList();

        return TeamResponse.from(team, playerIds);
    }

    @Transactional(readOnly = true)
    public List<MatchCandidateResponse> getMatchCandidates(ArenaMode mode) {

        // Load all active teams from the requested arena mode
        List<Team> teams =
                teamRepository.findAllByModeAndStatusOrderByTeamIdAsc(mode, TeamStatus.ACTIVE);

        if (teams.isEmpty()) {
            return List.of();
        }

        // Collect team IDs for roster query
        List<Long> teamIds = teams.stream()
                .map(Team::getTeamId)
                .toList();

        // Load all rosters together to avoid a separate database query for every team
        List<TeamMember> teamMembers =
                teamMemberRepository.findAllByTeamIdInOrderByTeamIdAscPlayerIdAsc(teamIds);

        Map<Long, List<Long>> playerIdsByTeam = new HashMap<>();

        // Group player IDs by team ID
        for (TeamMember member : teamMembers) {
            playerIdsByTeam.computeIfAbsent(member.getTeamId(), teamId -> new ArrayList<>())
                    .add(member.getPlayerId());
        }

        List<MatchCandidateResponse> candidates = new ArrayList<>();

        // Combine each team with its grouped roster
        for (Team team : teams) {
            candidates.add(new MatchCandidateResponse(
                    team.getTeamId(),
                    team.getMode(),
                    team.getActivatedAt(),
                    playerIdsByTeam.getOrDefault(team.getTeamId(), List.of())
            ));
        }

        return candidates;
    }

    @Transactional
    public TeamResponse replaceRoster(Long teamId, ReplaceRosterRequest request) {

        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team not found"));

        if (team.getStatus() != TeamStatus.DRAFT) {
            throw new ConflictException("TEAM_NOT_DRAFT", "Only draft teams can change their roster");
        }

        List<Long> playerIds = request.getPlayerIds();

        if (playerIds.stream().distinct().count() != playerIds.size()) {
            throw new ConflictException("ROSTER_INVALID", "Roster contains duplicate players");
        }

        List<Player> players = playerRepository.findAllById(playerIds);

        if (players.size() != playerIds.size()) {
            throw new ResourceNotFoundException("PLAYER_NOT_FOUND", "One or more players do not exist");
        }

        for (Player player : players) {
            if (player.getStatus() != PlayerStatus.ACTIVE) {
                throw new ConflictException("PLAYER_NOT_ACTIVE", "Retired players cannot join a roster");
            }
        }

        // Replace the roster in one transaction
        // so a failure keeps the previous roster unchanged
        teamMemberRepository.deleteByTeamId(teamId);

        for (Long playerId : playerIds) {
            TeamMember member = new TeamMember();

            member.setTeamId(teamId);
            member.setPlayerId(playerId);

            teamMemberRepository.save(member);
        }

        return TeamResponse.from(team, playerIds.stream().sorted().toList());

    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TeamResponse activate(Long teamId) {

        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team not found"));

        if (team.getStatus() != TeamStatus.DRAFT) {
            throw new ConflictException("TEAM_NOT_DRAFT", "Only draft teams can be activated");
        }

        List<TeamMember> members = teamMemberRepository.findByTeamId(teamId);

        int requiredSize = team.getMode() == ArenaMode.THREE_VS_THREE ? 3 : 5;

        if (members.size() != requiredSize) {
            throw new ConflictException("ROSTER_INCOMPLETE",
                    "A " + team.getMode() + " team needs exactly " + requiredSize + " players");
        }

        List<Long> playerIds = members.stream()
                .map(TeamMember::getPlayerId)
                .sorted()
                .toList();

        // Lock players in ascending ID order so concurrent team activations never deadlock
        // Circular wait case like: team A wants player 1 2 3, and team B wants player 2 3 5;
        //                          A waits for player 2, B waits for player 3
        // will not happen.
        List<Player> players = playerRepository.findAllByIdForUpdate(playerIds);

        for (Player player : players) {
            if (player.getStatus() != PlayerStatus.ACTIVE) {
                throw new ConflictException("PLAYER_NOT_ACTIVE", "Retired players cannot be in an active team");
            }
        }

        // A player can only belong to one active team per mode
        if (teamMemberRepository.countActiveMemberships(playerIds, team.getMode()) > 0) {
            throw new ConflictException("PLAYER_ALREADY_IN_ACTIVE_TEAM",
                    "A player is already on an active team in this mode");
        }

        team.activate(clock.instant());

        Team savedTeam = teamRepository.saveAndFlush(team);

        // Add the activated team to Redis after MySQL commits
        eventPublisher.publishEvent(
                new TeamLeaderboardChangedEvent(
                        List.of(savedTeam.getTeamId())
                )
        );

        return TeamResponse.from(savedTeam, playerIds);
    }

    @Transactional
    public TeamResponse retire(Long teamId) {

        // Lock the team row to separate a retirement request from a roster change
        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("TEAM_NOT_FOUND", "Team not found"));

        if (team.getStatus() == TeamStatus.RETIRED) {
            throw new ConflictException("TEAM_ALREADY_RETIRED", "Team is already retired");
        }

        team.retire(clock.instant());

        List<Long> playerIds = teamMemberRepository.findByTeamId(teamId)
                .stream()
                .map(TeamMember::getPlayerId)
                .sorted()
                .toList();

        Team savedTeam = teamRepository.saveAndFlush(team);

        // Remove the retired team from Redis after MySQL commits
        eventPublisher.publishEvent(
                new TeamLeaderboardChangedEvent(
                        List.of(savedTeam.getTeamId())
                )
        );

        return  TeamResponse.from(savedTeam, playerIds);
    }

}
