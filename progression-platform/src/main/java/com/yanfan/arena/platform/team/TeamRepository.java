package com.yanfan.arena.platform.team;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByModeAndNameIgnoreCase(ArenaMode mode, String name);
}
