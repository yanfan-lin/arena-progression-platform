package com.yanfan.arena.platform.match;

import com.yanfan.arena.contract.ArenaMatchCompleted;
import com.yanfan.arena.contract.MatchMode;
import com.yanfan.arena.platform.progression.XpPolicy;
import com.yanfan.arena.platform.team.domain.ArenaMode;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Shared helpers for match processor integration tests
final class MatchProcessorTestData {

    private MatchProcessorTestData() {
    }

    // Build a completed match event with current time,
    // so the domain validator never sees a future completedAt
    static ArenaMatchCompleted event(UUID eventId,
                                     UUID matchId,
                                     long winnerTeamId,
                                     MatchMode mode,
                                     ArenaMatchCompleted.Team... teams) {

        return new ArenaMatchCompleted(
                ArenaMatchCompleted.CONTRACT_VERSION,
                eventId,
                matchId,
                mode,
                Instant.now(),
                winnerTeamId,
                List.of(teams)
        );
    }

    // Build one participating team inside the event
    static ArenaMatchCompleted.Team eventTeam(long teamId, ArenaMatchCompleted.Player... players) {
        return new ArenaMatchCompleted.Team(teamId, List.of(players));
    }

    // Build one participant with their K/D/A for the match
    static ArenaMatchCompleted.Player eventPlayer(long playerId, int kills, int deaths, int assists) {
        return new ArenaMatchCompleted.Player(playerId, kills, deaths, assists);
    }

    // Insert an ACTIVE player with the given XP and the matching level
    static void insertPlayer(JdbcTemplate jdbcTemplate, long playerId, String displayName, long totalXp) {
        jdbcTemplate.update(
                """
                INSERT INTO players (player_id, display_name, status, total_xp, level, created_at, updated_at)
                VALUES (?, ?, 'ACTIVE', ?, ?, NOW(6), NOW(6))
                """,
                playerId, displayName, totalXp, XpPolicy.levelFor(totalXp));
    }

    // Insert an ACTIVE team with the given mode and starting rating
    static void insertTeam(JdbcTemplate jdbcTemplate, long teamId, String name, ArenaMode mode, int rating) {
        jdbcTemplate.update(
                """
                INSERT INTO teams (team_id, name, mode, status, rating, matches_played, wins, losses,
                                   total_kills, total_deaths, total_assists, created_at, updated_at, activated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, 0, 0, 0, 0, 0, 0, NOW(6), NOW(6), NOW(6))
                """,
                teamId, name, mode.name(), rating);
    }

    // Lock a player into a team's roster
    static void addMember(JdbcTemplate jdbcTemplate, long teamId, long playerId) {
        jdbcTemplate.update(
                "INSERT INTO team_members (team_id, player_id, added_at) VALUES (?, ?, NOW(6))",
                teamId, playerId);
    }

    // Count rows in a table to prove nothing was written twice
    static int countRows(JdbcTemplate jdbcTemplate, String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

}
