package com.yanfan.arena.platform.team;

import com.yanfan.arena.platform.common.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Team lifecycle operations
@Service
public class TeamService {

    private final TeamRepository teamRepository;

    @Autowired
    // Constructor injection
    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
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


}
