package com.yanfan.arena.simulator.simulation.run;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.simulator.simulation.match.InsufficientTeamsException;
import com.yanfan.arena.simulator.simulation.match.MatchSimulationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

// Manage scheduled match generation.
@Service
public class SimulationRunService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationRunService.class);

    private final MatchSimulationService matchSimulationService;

    // Continue generating matches after the start request returns
    private final TaskScheduler taskScheduler;

    private final Clock clock;

    // Keep the current or most recent match simulation run for status and stop requests
    private SimulationRun currentRun;

    // Keep the scheduled match task so future match executions can be
    // canceled when the match simulation run completes, stops or fails
    private ScheduledFuture<?> currentTask;

    @Autowired
    public SimulationRunService(MatchSimulationService matchSimulationService,
                                TaskScheduler taskScheduler,
                                Clock clock)
    {
        this.matchSimulationService = matchSimulationService;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
    }

    // Start one match generation run in the background at a time
    public synchronized SimulationRunResponse startRun(SimulationRunRequest request) {

        if (currentRun != null && currentRun.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A simulation run is already active.");
        }

        SimulationRun run = new SimulationRun(request, clock.instant());

        currentRun = run;

        // Start the next delay only after the previous match attempt finishes
        currentTask = taskScheduler.scheduleWithFixedDelay(
                () -> runNextMatch(run, request.mode()),
                Duration.ofMillis(request.intervalMs())
        );

        SimulationRunResponse response = run.toResponse();

        LOGGER.info(
                "Started simulation run runId={} mode={} maxMatches={}",
                response.runId(),
                response.mode(),
                response.maxMatches());

        return response;
    }

    public synchronized SimulationRunResponse getCurrentRun() {

        return requireCurrentRun().toResponse();
    }

    // Request a graceful stop so the publisher can finish
    // sending the current match to Kafka
    public synchronized SimulationRunResponse stopCurrentRun() {

        SimulationRun run = requireCurrentRun();

        run.requestStop();

        SimulationRunResponse response = run.toResponse();

        LOGGER.info(
                "Requested simulation run stop runId={}",
                response.runId()
        );

        return response;
    }

    // Generate and publish one match for the scheduled run
    // Keep this method unsynchronized so stop requests can be handled during publishing
    private void runNextMatch(SimulationRun run, MatchMode mode) {

        // Do not start another match after the run starts stopping or has ended
        if (!run.beginNextMatch()) {
            cancelScheduledTask(run);

            if (run.isStopRequested()) {
                run.stop(clock.instant());

                logFinishedRun(run);
            }

            return;
        }

        try {
            // Count the match only after Kafka acknowledges this event
            ArenaMatchCompleted event = matchSimulationService.simulateMatch(mode);

            run.recordPublished(event);

            if (run.isStopRequested()) {
                cancelScheduledTask(run);

                run.stop(clock.instant());

                logFinishedRun(run);
            }
            else if (run.hasReachedMatchLimit()) {
                cancelScheduledTask(run);

                run.complete(clock.instant());

                logFinishedRun(run);
            }
        }
        catch (InsufficientTeamsException e) {
            // Keep the run active so it can retry when
            // enough eligible arena teams are available
            run.markWaitingForTeams();
        }
        catch (RuntimeException ex) {
            // Do not repeat the run after an unexpected failure
            cancelScheduledTask(run);

            run.fail(ex.getMessage(), clock.instant());

            LOGGER.error(
                    "Simulation run failed runId={}",
                    run.toResponse().runId(),
                    ex);
        }
    }

    // Cancel the scheduled task that belongs to the current run
    private synchronized void cancelScheduledTask(SimulationRun run) {

        // Prevent an older run from cancelling a newer run's task
        if (currentRun == run && currentTask != null) {

            // Let a running match finish while preventing later executions
            currentTask.cancel(false);
        }
    }

    private SimulationRun requireCurrentRun() {

        if (currentRun == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No simulation run is available.");
        }

        return currentRun;
    }

    private void logFinishedRun(SimulationRun run) {

        SimulationRunResponse response = run.toResponse();

        LOGGER.info(
                "Finished simulation run runId={} state={} publishedMatches={}",
                response.runId(),
                response.state(),
                response.publishedMatches());
    }

}
