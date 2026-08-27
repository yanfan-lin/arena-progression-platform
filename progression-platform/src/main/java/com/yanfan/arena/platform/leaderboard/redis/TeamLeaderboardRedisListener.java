package com.yanfan.arena.platform.leaderboard.redis;

import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.persistence.TeamRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

// Refresh Redis leaderboard scores after team changes commit to MySQL
@Component
public class TeamLeaderboardRedisListener {

    private final TeamRepository teamRepository;

    private final TeamLeaderboardRedisStore leaderboardStore;

    private final TeamLeaderboardProjectionLock projectionLock;

    @Autowired
    public TeamLeaderboardRedisListener(
            TeamRepository teamRepository,
            TeamLeaderboardRedisStore leaderboardStore,
            TeamLeaderboardProjectionLock projectionLock)
    {
        this.teamRepository = teamRepository;
        this.leaderboardStore = leaderboardStore;
        this.projectionLock = projectionLock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void refreshChangedTeams(TeamLeaderboardChangedEvent event) {
        // Lock to prevent this update from running
        // in the middle of a leaderboard rebuild
        projectionLock.lock();

        try {
            List<Team> teams = teamRepository.findAllById(event.teamIds());

            for (Team team : teams) {
                leaderboardStore.update(team);
            }
        }
        finally {
            // Unlock even if the update fails
            projectionLock.unlock();
        }
    }

}
