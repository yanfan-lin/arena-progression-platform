package com.yanfan.arena.platform.team;

import com.yanfan.arena.platform.common.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    TeamRepository teamRepository;

    @InjectMocks
    TeamService teamService;


    @Test
    void createTrimsNameAndSaves() {
        when(teamRepository.existsByModeAndNameIgnoreCase(ArenaMode.THREE_VS_THREE, "ExampleTeam"))
                .thenReturn(false);

        when(teamRepository.saveAndFlush(any(Team.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateTeamRequest request = new CreateTeamRequest();
        request.setName("  ExampleTeam    ");
        request.setMode(ArenaMode.THREE_VS_THREE);

        TeamResponse response = teamService.create(request);

        assertThat(response.name()).isEqualTo("ExampleTeam");
        assertThat(response.mode()).isEqualTo(ArenaMode.THREE_VS_THREE);
        assertThat(response.status()).isEqualTo(TeamStatus.DRAFT);
    }

    @Test
    void createRejectsDuplicateNameInMode() {
        when(teamRepository.existsByModeAndNameIgnoreCase(ArenaMode.THREE_VS_THREE, "ExampleTeam"))
                .thenReturn(true);

        CreateTeamRequest request = new CreateTeamRequest();
        request.setName("ExampleTeam");
        request.setMode(ArenaMode.THREE_VS_THREE);

        assertThatThrownBy(() -> teamService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createTranslatesDatabaseDuplicateToConflict() {
        when(teamRepository.existsByModeAndNameIgnoreCase(ArenaMode.THREE_VS_THREE, "ExampleTeam"))
                .thenReturn(false);

        when(teamRepository.saveAndFlush(any(Team.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        CreateTeamRequest request = new CreateTeamRequest();
        request.setName("ExampleTeam");
        request.setMode(ArenaMode.THREE_VS_THREE);

        assertThatThrownBy(() -> teamService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
    }


}
