package com.yanfan.arena.platform.player.service;

import com.yanfan.arena.platform.error.ConflictException;
import com.yanfan.arena.platform.error.ResourceNotFoundException;
import com.yanfan.arena.platform.player.api.CreatePlayerRequest;
import com.yanfan.arena.platform.player.api.PlayerResponse;
import com.yanfan.arena.platform.player.domain.Player;
import com.yanfan.arena.platform.player.domain.PlayerStatus;
import com.yanfan.arena.platform.player.persistence.PlayerRepository;
import com.yanfan.arena.platform.team.TeamMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    PlayerRepository playerRepository;

    @Mock
    TeamMemberRepository teamMemberRepository;

    @Mock
    Clock clock;

    @InjectMocks
    PlayerService playerService;

    @Test
    void createTrimsDisplayNameAndSaves() {
        when(playerRepository.existsByDisplayNameIgnoreCase("ArenaExamplePlayer")).thenReturn(false);

        when(playerRepository.saveAndFlush(any(Player.class)))
                .thenAnswer(invocation ->
                        {
                            Player player = invocation.getArgument(0);
                            player.setStatus(PlayerStatus.ACTIVE);

                            return player;
                        }
                );

        CreatePlayerRequest request = new CreatePlayerRequest();
        request.setDisplayName("   ArenaExamplePlayer  ");

        PlayerResponse response = playerService.create(request);

        assertThat(response.displayName()).isEqualTo("ArenaExamplePlayer");
        assertThat(response.status()).isEqualTo(PlayerStatus.ACTIVE);

        verify(playerRepository).saveAndFlush(any(Player.class));
    }

    @Test
    void createRejectsDuplicateDisplayName() {
        when(playerRepository.existsByDisplayNameIgnoreCase("ArenaExamplePlayer"))
                .thenReturn(true);

        CreatePlayerRequest request = new CreatePlayerRequest();
        request.setDisplayName("ArenaExamplePlayer");

        assertThatThrownBy(() -> playerService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createTranslatesDatabaseDuplicateToConflict() {
        when(playerRepository.existsByDisplayNameIgnoreCase("ArenaExamplePlayer"))
                .thenReturn(false);

        when(playerRepository.saveAndFlush(any(Player.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        CreatePlayerRequest request = new CreatePlayerRequest();
        request.setDisplayName("ArenaExamplePlayer");

        assertThatThrownBy(() -> playerService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getReturnsExistingPlayer() {
        Player player = new Player();
        player.setDisplayName("ArenaExamplePlayer");

        when(playerRepository.findById(1L)).
                thenReturn(Optional.of(player));

        PlayerResponse response = playerService.get(1L);

        assertThat(response.displayName()).isEqualTo("ArenaExamplePlayer");
    }

    @Test
    void getThrowsNotFoundForUnknownPlayer() {
        when(playerRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> playerService.get(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void retireMarksPlayerAsRetired() {

        Player player = new Player();
        player.setDisplayName("ArenaExamplePlayer");

        when(playerRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(player));

        when(teamMemberRepository.countActiveTeamMemberships(1L))
                .thenReturn(0L);

        when(clock.instant())
                .thenReturn(Instant.parse("2026-08-09T00:00:00Z"));

        when(playerRepository.saveAndFlush(any(Player.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PlayerResponse response = playerService.retire(1L);

        assertThat(response.status()).isEqualTo(PlayerStatus.RETIRED);
        assertThat(player.getRetiredAt()).isEqualTo(Instant.parse("2026-08-09T00:00:00Z"));
    }

    @Test
    void retireRejectsAlreadyRetiredPlayer() {

        Player player = new Player();
        player.setDisplayName("ArenaExamplePlayer");

        player.retire(Instant.parse("2026-08-09T00:00:00Z"));

        when(playerRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(player));

        assertThatThrownBy(() -> playerService.retire(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already retired");

    }

    @Test
    void retireRejectsPlayerOnActiveTeam() {
        Player player = new Player();
        player.setDisplayName("ArenaExamplePlayer");

        when(playerRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(player));

        when(teamMemberRepository.countActiveTeamMemberships(1L))
                .thenReturn(1L);

        assertThatThrownBy(() -> playerService.retire(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("active team");

    }


}
