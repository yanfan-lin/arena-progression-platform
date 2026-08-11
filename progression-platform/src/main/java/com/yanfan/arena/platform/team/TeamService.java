package com.yanfan.arena.platform.team;

import com.yanfan.arena.platform.common.ConflictException;
import com.yanfan.arena.platform.common.ResourceNotFoundException;
import com.yanfan.arena.platform.player.Player;
import com.yanfan.arena.platform.player.PlayerRepository;
import com.yanfan.arena.platform.player.PlayerStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

// Team lifecycle operations
@Service
public class TeamService {

    private final TeamRepository teamRepository;

    private final TeamMemberRepository teamMemberRepository;

    private final PlayerRepository playerRepository;

    private final Clock clock;


    @Autowired
    // Constructor injection
    public TeamService(TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       PlayerRepository playerRepository, Clock clock) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.playerRepository = playerRepository;
        this.clock = clock;
    }


    @Transactional
    public TeamResponse create(CreateTeamRequest request) {

        String name = request.getName().trim();

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
        } catch (DataIntegrityViolationException ex) {
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

    @Transactional
    public TeamResponse replaceRoster(Long teamId, ReplaceRosterRequest request) {
        Team team = teamRepository.findById(teamId)
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

        // Perform deleting the old roaster and inserting the new one in the same transaction,
        // so an update failure leaves the previous roaster untouched
        teamMemberRepository.deleteByTeamId(teamId);

        for (Long playerId : playerIds) {
            TeamMember member = new TeamMember();

            member.setTeamId(teamId);
            member.setPlayerId(playerId);

            teamMemberRepository.save(member);
        }

        return TeamResponse.from(team, playerIds.stream().sorted().toList());

    }

    @Transactional
    public TeamResponse activate(Long teamId) {
        // Lock the team row so two team activation requests
        // can not pass the checks at the same time.
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

        List<Long> playerIds = members
                .stream()
                .map(TeamMember::getPlayerId)
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

        List<Long> rosterPlayerIds = members.stream()
                .map(TeamMember::getPlayerId)
                .sorted()
                .toList();

        team.activate(clock.instant());

        return TeamResponse.from(teamRepository.saveAndFlush(team), rosterPlayerIds);

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

        return TeamResponse.from(teamRepository.saveAndFlush(team), playerIds);

    }




}
