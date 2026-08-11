package com.yanfan.arena.platform.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamId(Long teamId);

    void deleteByTeamId(Long teamId);

    @Query("""
                SELECT COUNT(tm) FROM TeamMember tm
                JOIN Team t
                ON t.teamId = tm.teamId
                WHERE tm.playerId IN :playerIds
                AND t.mode = :mode
                AND t.status = com.yanfan.arena.platform.team.TeamStatus.ACTIVE
            """)
    long countActiveMemberships(Collection<Long> playerIds, ArenaMode mode);

}
