package com.yanfan.arena.platform.team.persistence;

import com.yanfan.arena.platform.team.domain.ArenaMode;
import com.yanfan.arena.platform.team.domain.Team;
import com.yanfan.arena.platform.team.domain.TeamStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByModeAndNameIgnoreCase(ArenaMode mode, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Team t WHERE t.teamId = :teamId")
    Optional<Team> findByIdForUpdate(Long teamId);

    List<Team> findAllByStatus(TeamStatus status);

    List<Team> findAllByModeAndStatusOrderByTeamIdAsc(ArenaMode mode, TeamStatus status);

}
