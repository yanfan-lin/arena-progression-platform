-- Enforce the progression invariant: level is always derived from total XP.
-- Fix any inconsistent rows before locking the rule down.

UPDATE players
SET level = 1 + FLOOR(total_xp / 1000);

ALTER TABLE players
    ADD CONSTRAINT chk_players_level_matches_xp
        CHECK ( level = 1 + FLOOR(total_xp / 1000) );