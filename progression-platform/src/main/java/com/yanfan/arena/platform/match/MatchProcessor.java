package com.yanfan.arena.platform.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.platform.player.Player;
import com.yanfan.arena.platform.player.PlayerRepository;
import com.yanfan.arena.platform.team.Team;
import com.yanfan.arena.platform.team.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Store one validated match event in the database.
// Roll back every change if any step fails.
@Service
public class MatchProcessor {

    private final MatchEventValidator eventValidator;
    private final MatchDomainValidator domainValidator;
    private final MatchStatisticsValidator statisticsValidator;
    private final MatchProgressionCalculator progressionCalculator;
    private final ProcessedEventRepository processedEventRepository;
    private final MatchResultRepository matchResultRepository;
    private final MatchTeamResultRepository matchTeamResultRepository;
    private final MatchParticipantResultRepository matchParticipantResultRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    // Used only by tests to force a rollback after the inserts
    private boolean failAfterPersist;

    @Autowired
    public MatchProcessor(
            MatchEventValidator eventValidator,
            MatchDomainValidator domainValidator, MatchStatisticsValidator statisticsValidator,
            MatchProgressionCalculator progressionCalculator,
            ProcessedEventRepository processedEventRepository,
            MatchResultRepository matchResultRepository,
            MatchTeamResultRepository matchTeamResultRepository,
            MatchParticipantResultRepository matchParticipantResultRepository,
            TeamRepository teamRepository,
            PlayerRepository playerRepository
    ) {
        this.eventValidator = eventValidator;
        this.domainValidator = domainValidator;
        this.statisticsValidator = statisticsValidator;
        this.progressionCalculator = progressionCalculator;
        this.processedEventRepository = processedEventRepository;
        this.matchResultRepository = matchResultRepository;
        this.matchTeamResultRepository = matchTeamResultRepository;
        this.matchParticipantResultRepository = matchParticipantResultRepository;
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
    }

    // For tests-only: make the next process() call fail after the snapshots are inserted
    void failAfterPersistForTest() {
        this.failAfterPersist = true;
    }

    // One transaction for the whole match
    // if anything fails, every change is rolled back
    @Transactional
    public MatchProcessingResult process(ArenaMatchCompleted event) {

        // Step 1
        // Structural validation first, so null or missing IDs
        // throw error and not NullPointerException
        eventValidator.validate(event);

        // Step 2
        // Idempotency check before processing
        // if either identifier already exists, this transaction is ignored
        String eventId = event.eventId().toString();
        String matchId = event.matchId().toString();

        if (processedEventRepository.existsById(eventId)
                || processedEventRepository.existsByMatchId(matchId)) {
            return MatchProcessingResult.duplicate();
        }

        // Step 3
        // Lock both teams in ascending ID order to prevent deadlocks
        long teamAId = event.teams().get(0).teamId();
        long teamBId = event.teams().get(1).teamId();

        long firstLockId = Math.min(teamAId, teamBId);
        long secondLockId = Math.max(teamAId, teamBId);

        Team firstTeam = teamRepository.findByIdForUpdate(firstLockId)
                .orElseThrow(() -> new MatchEventValidationException("Team not found: " + firstLockId));
        Team secondTeam = teamRepository.findByIdForUpdate(secondLockId)
                .orElseThrow(() -> new MatchEventValidationException("Team not found: " + secondLockId));

        // Map the locked teams to the event's A/B team order for the calculator
        Team teamA = firstLockId == teamAId ? firstTeam : secondTeam;
        Team teamB = firstLockId == teamAId ? secondTeam : firstTeam;

        // Step 4
        // Collect every player's id from both teams
        List<Long> playerIds = new ArrayList<>();
        for (ArenaMatchCompleted.Team team : event.teams()) {
            for (ArenaMatchCompleted.Player participant : team.participants()) {
                playerIds.add(participant.playerId());
            }
        }

        // Lock the players in ascending ID order
        playerIds.sort(null);
        List<Player> lockedPlayers = playerRepository.findAllByIdForUpdate(playerIds);

        // Map player IDs to locked player IDs for the calculator
        Map<Long, Player> playersById = new HashMap<>();
        for (Player player : lockedPlayers) {
            playersById.put(player.getPlayerId(), player);
        }

        // Every participant must have one corresponding player
        if (playersById.size() != playerIds.size()) {
            throw new MatchEventValidationException("One or more participants do not exist");
        }

        // Step 5
        // Validate team states
        domainValidator.validate(event);

        // Step 6
        // Validate statistics consistency
        statisticsValidator.validate(event);

        // Step 7
        // Calculate every progression value change from the pre-match state
        MatchProcessingResult.ProcessedMatch processed =
                progressionCalculator.calculate(
                        event,
                        teamA,
                        teamB,
                        playersById
                );

        // Step 8
        // Store the idempotency record and all the immutable snapshots
        persistMatch(eventId, matchId, processed);

        // Test-only hook: proves the transaction rolls back on failure
        if (failAfterPersist) {
            throw new IllegalStateException("Forced failure after match inserts");
        }

        // Step 9
        // Update player's XP and level after the match
        // Entities are managed by transaction,
        // so the changes are flushed automatically
        for (MatchProcessingResult.PlayerResult playerResult : processed.playerResults()) {
            Player player = playersById.get(playerResult.playerId());

            player.setTotalXp(playerResult.totalXpAfter());

            player.setLevel(playerResult.levelAfter());
        }

        // Step 10
        // Update team's stats after the match
        // Entities are managed by transaction,
        // so the changes are flushed automatically
        for (MatchProcessingResult.TeamResult teamResult : processed.teamResults()) {

            Team team = teamResult.teamId() == teamA.getTeamId() ? teamA : teamB;

            team.setRating(teamResult.ratingAfter());
            team.setMatchesPlayed(teamResult.matchesPlayedAfter());

            team.setWins(teamResult.winsAfter());
            team.setLosses(teamResult.lossesAfter());

            team.setTotalKills(teamResult.totalKillsAfter());
            team.setTotalDeaths(teamResult.totalDeathsAfter());
            team.setTotalAssists(teamResult.totalAssistsAfter());
        }

        // Step 11
        // Return the summary of changes for later Redis updates
        return new MatchProcessingResult(
                MatchProcessingResult.MatchProcessingOutcome.PROCESSED,
                processed);

    }

    // Store the idempotency record and all the immutable snapshots
    // Protected so that tests can inject a failure after the inserts
    protected void persistMatch(String eventId,
                                String matchId,
                                MatchProcessingResult.ProcessedMatch processed)
    {
        processedEventRepository.save(new ProcessedEvent(eventId, matchId));

        matchTeamResultRepository.saveAll(toTeamResultEntities(processed));

        matchParticipantResultRepository.saveAll(toParticipantResultEntities(processed));

        matchResultRepository.save(new MatchResult(
                matchId,
                processed.mode(),
                processed.winningTeamId(),
                processed.contractVersion(),
                processed.completedAt())
        );

    }


    // Convert computed team results into entity rows for the snapshot table
    private List<MatchTeamResult> toTeamResultEntities(MatchProcessingResult.ProcessedMatch processed) {
        List<MatchTeamResult> entities = new ArrayList<>();

        for (MatchProcessingResult.TeamResult teamResult : processed.teamResults()) {
            entities.add(new MatchTeamResult(
                            new MatchTeamResultId(processed.matchId(), teamResult.teamId()),
                            teamResult.teamNameSnapshot(),
                            teamResult.ratingBefore(),
                            teamResult.ratingChange(),
                            teamResult.ratingAfter()
                    )
            );
        }

        return entities;
    }

    // Convert calculated player results into entity rows for the snapshot table
    private List<MatchParticipantResult> toParticipantResultEntities(MatchProcessingResult.ProcessedMatch processed) {
        List<MatchParticipantResult> entities = new ArrayList<>();

        for (MatchProcessingResult.PlayerResult playerResult : processed.playerResults()) {
            entities.add(new MatchParticipantResult(
                            new MatchParticipantResultId(processed.matchId(), playerResult.playerId()),
                            playerResult.teamId(),
                            playerResult.playerNameSnapshot(),
                            playerResult.kills(),
                            playerResult.deaths(),
                            playerResult.assists(),
                            // XP values are small (100/150), so the cast is safe
                            (int) playerResult.xpEarned()
                    )
            );
        }

        return entities;
    }

}
