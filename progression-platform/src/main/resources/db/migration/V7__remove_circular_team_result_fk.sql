-- Remove the FK that points back to matches.
-- Keeping fk_matches_winner still enforces that the winner
-- is one of the stored team-result rows, without a circular reference.
ALTER TABLE match_team_results
    DROP FOREIGN KEY fk_match_team_results_match;
