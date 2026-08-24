package com.yanfan.arena.platform.player.service;

import com.yanfan.arena.platform.error.ConflictException;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.player.api.CreatePlayerRequest;
import com.yanfan.arena.platform.player.api.PlayerResponse;
import com.yanfan.arena.platform.player.cache.PlayerProfileChangedEvent;
import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.player.domain.PlayerStatus;
import com.yanfan.arena.platform.player.persistence.PlayerRepository;
import com.yanfan.arena.platform.team.persistence.TeamMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import com.yanfan.arena.platform.player.cache.PlayerProfileCache;

import java.time.Clock;
import java.util.Optional;

// Handle player lifecycle and cached profile reads
@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    private final PlayerProfileCache playerProfileCache;

    private final TeamMemberRepository teamMemberRepository;

    private final ApplicationEventPublisher eventPublisher;

    private final Clock clock;

    @Autowired
    public PlayerService(PlayerRepository playerRepository,
                         PlayerProfileCache playerProfileCache,
                         TeamMemberRepository teamMemberRepository,
                         ApplicationEventPublisher eventPublisher,
                         Clock clock)
    {
        this.playerRepository = playerRepository;
        this.playerProfileCache = playerProfileCache;
        this.teamMemberRepository = teamMemberRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public PlayerResponse create(CreatePlayerRequest request) {
        String displayName = request.getDisplayName().trim();

        // Check whether the name is already used
        if (playerRepository.existsByDisplayNameIgnoreCase(displayName)) {
            throw new ConflictException("PLAYER_NAME_TAKEN",
                    "A player with this display name already exists");
        }

        Player player = new Player();
        player.setDisplayName(displayName);

        try {

            return PlayerResponse.from(playerRepository.saveAndFlush(player));
        }
        catch (DataIntegrityViolationException e) {

            // Handle duplicate request made at the same time
            throw new ConflictException("PLAYER_NAME_TAKEN",
                    "A player with this display name already exists");
        }
    }

    public PlayerResponse get(Long playerId) {
        Optional<PlayerResponse> cachedResponse =
                playerProfileCache.find(playerId);

        if (cachedResponse.isPresent()) {
            return cachedResponse.get();
        }

        // Redis failures are returned as misses, so this path falls back to MySQL
        PlayerResponse response = playerRepository.findById(playerId)
                .map(PlayerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PLAYER_NOT_FOUND",
                        "Player not found"));

        // Cache the MySQL response for later reads
        playerProfileCache.put(response);

        return response;
    }

    @Transactional
    public PlayerResponse retire(Long playerId) {
        // Prevent retirement while another request is activating a team with this player
        Player player = playerRepository.findByIdForUpdate(playerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PLAYER_NOT_FOUND",
                        "Player not found"));

        if (player.getStatus() == PlayerStatus.RETIRED) {
            throw new ConflictException("PLAYER_RETIRED", "Player is already retired");
        }

        if (teamMemberRepository.countActiveTeamMemberships(playerId) > 0) {
            throw new ConflictException("PLAYER_IN_ACTIVE_TEAM",
                    "Player on an active team can not be retired");
        }

        player.retire(clock.instant());

        // Flush so @PreUpdate sets updatedAt before building the response
        PlayerResponse response = PlayerResponse
                .from(playerRepository.saveAndFlush(player));

        // The listener removes the cache only after the transaction succeeds
        eventPublisher.publishEvent(new PlayerProfileChangedEvent(playerId));

        return response;
    }


}
