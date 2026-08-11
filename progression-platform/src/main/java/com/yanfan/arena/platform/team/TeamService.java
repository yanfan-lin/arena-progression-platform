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

import java.util.List;

// Team lifecycle operations
@Service
public class TeamService {

    private final TeamRepository teamRepository;

    private final TeamMemberRepository teamMemberRepository;

    private final PlayerRepository playerRepository;


    @Autowired
    // Constructor injection
    public TeamService(TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       PlayerRepository playerRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.playerRepository = playerRepository;
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
            return TeamResponse.from(teamRepository.saveAndFlush(team));
        } catch (DataIntegrityViolationException ex) {
            // The name was already saved by another request - return 409 just like above
            throw new ConflictException("TEAM_NAME_TAKEN",
                    "A team with this name already exists in this mode");
        }

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

        return TeamResponse.from(team);

    }


}
