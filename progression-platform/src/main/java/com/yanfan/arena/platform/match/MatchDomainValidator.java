package com.yanfan.arena.platform.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.team.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Validate a match event against the current teams and locked rosters.
// Run after the validation, and before any match is processed.
@Component
public class MatchDomainValidator {

    private final TeamRepository teamRepository;

    private final TeamMemberRepository teamMemberRepository;

    @Autowired
    public MatchDomainValidator(TeamRepository teamRepository, TeamMemberRepository teamMemberRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    // Check the following status before anything is processed:
    // 1. Both team exist and are active,
    // 2. Two teams are in the same arena mode,
    // 3. no player(s) appear on both teams,
    // 4. the winner is one of the two teams,
    // 5. the submitted team rosters match the locked ones exactly.
    public void validate(ArenaMatchCompleted event) {
        long teamAId = event.teams().get(0).teamId();
        long teamBId = event.teams().get(1).teamId();

        if (teamAId == teamBId) {
            throw new MatchEventValidationException("Both teams in the match are the same");
        }

        List<Team> teams = teamRepository.findAllById(List.of(teamAId, teamBId));
        if (teams.size() != 2) {
            throw new MatchEventValidationException("One or both teams do not exist");
        }

        ArenaMode mode = toArenaMode(event.mode());
        for (Team team : teams) {
            if (team.getStatus() != TeamStatus.ACTIVE) {
                throw new MatchEventValidationException("Team " + team.getTeamId() + " is not active");
            }
            if (team.getMode() != mode) {
                throw new MatchEventValidationException("Team " + team.getTeamId() + " is not in the match mode");
            }
        }

        if (event.winnerTeamId() != teamAId && event.winnerTeamId() != teamBId) {
            throw new MatchEventValidationException("Winner is not one of the participating teams");
        }

        List<Long> participantsAList = participantIds(event.teams().get(0));
        List<Long> participantsBList = participantIds(event.teams().get(1));

        if (hasDuplicates(participantsAList) || hasDuplicates(participantsBList)) {
            throw new MatchEventValidationException("A player appears more than once in a team");
        }

        if (!Collections.disjoint(participantsAList, participantsBList)) {
            throw new MatchEventValidationException("One player appears on both teams");
        }

        Set<Long> rosterA = rosterIds(teamAId);
        Set<Long> rosterB = rosterIds(teamBId);

        if (!rosterA.equals(new HashSet<>(participantsAList))
                || !rosterB.equals(new HashSet<>(participantsBList))) {
            throw new MatchEventValidationException("Submitted participants do not match the locked rosters");
        }

    }

    private Set<Long> rosterIds(long teamId) {
        return teamMemberRepository.findByTeamId(teamId).stream()
                .map(TeamMember::getPlayerId)
                .collect(Collectors.toSet());
    }

    private List<Long> participantIds(ArenaMatchCompleted.Team team) {
        return team.participants().stream()
                .map(ArenaMatchCompleted.Player::playerId)
                .toList();
    }

    private boolean hasDuplicates(List<Long> ids) {
        return ids.stream().distinct().count() != ids.size();
    }

    private ArenaMode toArenaMode(MatchMode mode) {
        return switch (mode) {
            case THREE_VS_THREE -> ArenaMode.THREE_VS_THREE;
            case FIVE_VS_FIVE -> ArenaMode.FIVE_VS_FIVE;
        };

    }

}
