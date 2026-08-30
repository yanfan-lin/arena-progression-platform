package com.yanfan.arena.simulator.simulation;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;

import java.time.Instant;
import java.util.UUID;

// Track the settings, progress and result of one continuous match simulation run.
final class SimulationRun {

    // Keep the run identity and settings unchanged after it starts
    private final UUID runId;

    private final MatchMode mode;

    private final long intervalMs;

    private final int maxMatches;

    private final Instant startedAt;

    // Store changing progress for run status responses
    private SimulationRunState state;

    // Count only matches acknowledged by Kafka
    private int publishedMatches;

    private UUID lastEventId;

    private UUID lastMatchId;

    private Instant endedAt;

    private String lastError;

    // Start a new run using the request settings
    SimulationRun(SimulationRunRequest request, Instant startedAt) {
        this.runId = UUID.randomUUID();
        this.mode = request.mode();
        this.intervalMs = request.intervalMs();
        this.maxMatches = request.maxMatches();
        this.startedAt = startedAt;
        this.state = SimulationRunState.RUNNING;
    }

    // Check whether another match simulation run must wait for this one to finish
    // Synchronized because scheduler and REST requests can use this run at the same time
    synchronized boolean isActive() {

        return state == SimulationRunState.RUNNING
                || state == SimulationRunState.WAITING_FOR_TEAMS
                || state == SimulationRunState.STOPPING;
    }

    synchronized boolean beginNextMatch() {

        // Prevent another match from starting
        // after the run begins stopping or has ended
        if (state != SimulationRunState.RUNNING
                && state != SimulationRunState.WAITING_FOR_TEAMS)
        {
            return false;
        }

        state = SimulationRunState.RUNNING;

        return true;
    }

    // Record Kafka-acked match events,
    // including a match that finishes while stopping
    synchronized void recordPublished(ArenaMatchCompleted event) {

        publishedMatches++;

        lastEventId = event.eventId();

        lastMatchId = event.matchId();
    }

    // Mark the run as waiting when there are not enough teams for another match
    synchronized void markWaitingForTeams() {

        // Keep STOPPING if a stop was requested while the simulator checked for eligible teams
        if (state != SimulationRunState.STOPPING) {

            state = SimulationRunState.WAITING_FOR_TEAMS;
        }
    }

    // Request a graceful run stop without interrupting
    // a currently publishing event
    synchronized void requestStop() {

        // The same thread can call synchronized isActive()
        // because this lock is reentrant
        if (isActive()) {
            state = SimulationRunState.STOPPING;
        }
    }

    // Check whether the scheduler should stop before starting another match
    synchronized boolean isStopRequested() {
        return state == SimulationRunState.STOPPING;
    }

    // Check whether the run has published its requested number of matches
    synchronized boolean hasReachedMatchLimit() {
        return publishedMatches >= maxMatches;
    }

    // Finish one match simulation run normally after
    // publishing the requested number of matches
    synchronized void complete(Instant completedAt) {

        state = SimulationRunState.COMPLETED;

        endedAt = completedAt;
    }

    // Finish the run early after a requested stop
    synchronized void stop(Instant stoppedAt) {

        state = SimulationRunState.STOPPED;

        endedAt = stoppedAt;
    }

    // Finish the run when an unexpected error occurs
    synchronized void fail(String error, Instant failedAt) {

        state = SimulationRunState.FAILED;

        lastError = error;

        endedAt = failedAt;
    }

    // Return the snapshot of current run details for the status response
    synchronized SimulationRunResponse toResponse() {

        // Copy the current values so the later run updates do not change this response
        return new SimulationRunResponse(
                runId,
                mode,
                state,
                intervalMs,
                maxMatches,
                publishedMatches,
                lastEventId,
                lastMatchId,
                startedAt,
                endedAt,
                lastError
        );
    }

}
