-- The player's membership in a team
CREATE TABLE team_members
(
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id   BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    added_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_team_members_team_player UNIQUE (team_id, player_id),
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (team_id),
    CONSTRAINT fk_team_members_player FOREIGN KEY (player_id) REFERENCES players (player_id)
);

-- Fast player membership lookup for activation and retirement checks
CREATE INDEX idx_team_members_player ON team_members (player_id);
