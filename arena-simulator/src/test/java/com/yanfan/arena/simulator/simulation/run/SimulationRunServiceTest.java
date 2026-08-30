package com.yanfan.arena.simulator.simulation.run;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.simulator.simulation.match.MatchSimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Verify scheduled match generation completes at
// the requested match limit and stops gracefully
@ExtendWith(MockitoExtension.class)
class SimulationRunServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private MatchSimulationService matchSimulationService;

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ScheduledFuture<?> scheduledTask;

    @Mock
    private ArenaMatchCompleted event;

    private SimulationRunService simulationRunService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        simulationRunService =
                new SimulationRunService(
                        matchSimulationService,
                        taskScheduler,
                        clock);

        doReturn(scheduledTask)
                .when(taskScheduler)
                .scheduleWithFixedDelay(
                        any(Runnable.class),
                        any(Duration.class));

    }

    @Test
    void stopsSchedulingAtRequestedMatchLimit() {

        SimulationRunRequest request =
                new SimulationRunRequest(
                        MatchMode.THREE_VS_THREE,
                        100,
                        2);

        when(matchSimulationService.simulateMatch(
                MatchMode.THREE_VS_THREE))
                .thenReturn(event);

        // Capture the scheduled work so the test can run it without waiting
        ArgumentCaptor<Runnable> taskCaptor =
                ArgumentCaptor.forClass(Runnable.class);

        simulationRunService.startRun(request);

        verify(taskScheduler)
                .scheduleWithFixedDelay(
                        taskCaptor.capture(),
                        eq(Duration.ofMillis(100)));

        Runnable scheduledExecution = taskCaptor.getValue();

        scheduledExecution.run();
        scheduledExecution.run();

        SimulationRunResponse response =
                simulationRunService.getCurrentRun();

        assertThat(response.state())
                .isEqualTo(SimulationRunState.COMPLETED);

        verify(scheduledTask)
                .cancel(false);
    }

}
