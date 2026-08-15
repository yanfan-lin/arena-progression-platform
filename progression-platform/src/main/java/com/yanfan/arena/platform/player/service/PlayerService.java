package com.yanfan.arena.platform.player.service;

import com.yanfan.arena.platform.error.ConflictException;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.player.api.CreatePlayerRequest;
import com.yanfan.arena.platform.player.api.PlayerResponse;
import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.player.domain.PlayerStatus;
import com.yanfan.arena.platform.player.persistence.PlayerRepository;
import com.yanfan.arena.platform.team.TeamMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;

// Player lifecycle operations
@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    private final TeamMemberRepository teamMemberRepository;

    private final Clock clock;


    @Autowired
    public PlayerService(PlayerRepository playerRepository, TeamMemberRepository teamMemberRepository, Clock clock) {
        this.playerRepository = playerRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.clock = clock;
    }

    @Transactional
    public PlayerResponse create(CreatePlayerRequest request) {
        String displayName = request.getDisplayName().trim();

        // If the name already exists, stop and return 409
        if (playerRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new ConflictException("PLAYER_NAME_TAKEN",
                    "A player with this display name already exists");
        }

        Player player = new Player();
        player.setDisplayName(displayName);

        try {

            // Save the player now: If two requests use the same name at the same time,
            // the database rejects the second one
            return PlayerResponse.from(playerRepository.saveAndFlush(player));
        } catch (DataIntegrityViolationException e) {

            // Return 409 if the name was already saved by another request
            throw new ConflictException("PLAYER_NAME_TAKEN",
                    "A player with this display name already exists");
        }

    }

    public PlayerResponse get(Long playerId) {
        return playerRepository.findById(playerId)
                .map(PlayerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("PLAYER_NOT_FOUND",
                        "Player not found"));
    }

    @Transactional
    public PlayerResponse retire(Long playerId) {
        // Lock the player row so retirement request is separate
        // from team activation request
        Player player = playerRepository.findByIdForUpdate(playerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PLAYER_NOT_FOUND",
                        "Player not found"));

        if (player.getStatus() == PlayerStatus.RETIRED) {
            throw new ConflictException("PLAYER_RETIRED", "Player is already retired");
        }

        // A player on an active arena team can not retire
        if (teamMemberRepository.countActiveTeamMemberships(playerId) > 0) {
            throw new ConflictException("PLAYER_IN_ACTIVE_TEAM",
                    "Player on an active team can not be retired");
        }

        player.retire(clock.instant());

        // Flush before building the response,
        // so that @PreUpdate refreshes updatedAt
        return PlayerResponse.from(playerRepository.saveAndFlush(player));
    }


}
