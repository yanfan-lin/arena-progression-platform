package com.yanfan.arena.platform.player;

import org.springframework.data.jpa.repository.JpaRepository;

// Database access layer for Player
public interface PlayerRepository extends JpaRepository<Player, Long> {

    boolean existsByDisplayNameIgnoreCase(String displayName);

}
