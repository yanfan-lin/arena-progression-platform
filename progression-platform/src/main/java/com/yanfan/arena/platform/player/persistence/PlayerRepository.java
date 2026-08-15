package com.yanfan.arena.platform.player.persistence;

import com.yanfan.arena.platform.player.domain.Player;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

// Database access layer for Player
public interface PlayerRepository extends JpaRepository<Player, Long> {

    boolean existsByDisplayNameIgnoreCase(String displayName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Player p WHERE p.playerId = :playerId")
    Optional<Player> findByIdForUpdate(Long playerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Player p WHERE p.playerId IN :playerIds ORDER BY p.playerId")
    List<Player> findAllByIdForUpdate(Collection<Long> playerIds);

}
