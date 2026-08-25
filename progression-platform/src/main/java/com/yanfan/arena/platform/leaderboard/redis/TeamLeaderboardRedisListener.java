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

    @Autowired
    public TeamLeaderboardRedisListener(
            TeamRepository teamRepository,
            TeamLeaderboardRedisStore leaderboardStore)
    {
        this.teamRepository = teamRepository;
        this.leaderboardStore = leaderboardStore;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void refreshChangedTeams(TeamLeaderboardChangedEvent event) {
        // Reload the final values stored by the committed transaction
        List<Team> teams =
                teamRepository.findAllById(event.teamIds());

        for (Team team : teams) {
            leaderboardStore.update(team);
        }
    }

}
