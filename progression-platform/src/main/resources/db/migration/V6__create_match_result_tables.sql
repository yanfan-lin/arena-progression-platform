-- Record of accepted match events.
CREATE TABLE processed_events
(
    event_id     CHAR(36) NOT NULL,
    match_id     CHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL,

    PRIMARY KEY (event_id),

    CONSTRAINT uk_processed_events_match_id UNIQUE (match_id)
);

-- One accepted arena match
CREATE TABLE matches
(
    match_id         CHAR(36)    NOT NULL,
    mode             VARCHAR(24) NOT NULL,
    winning_team_id  BIGINT      NOT NULL,
    contract_version INT         NOT NULL,
    completed_at     DATETIME(6) NOT NULL,

    PRIMARY KEY (match_id),

    CONSTRAINT chk_matches_mode CHECK (mode IN ('THREE_VS_THREE', 'FIVE_VS_FIVE')),

    CONSTRAINT chk_matches_contract_version_positive CHECK (contract_version >= 1),

    INDEX            idx_matches_history (completed_at DESC, match_id DESC)
);

-- One row per participating team
CREATE TABLE match_team_results
(
    match_id           CHAR(36)    NOT NULL,
    team_id            BIGINT      NOT NULL,
    team_name_snapshot VARCHAR(50) NOT NULL,
    rating_before      INT         NOT NULL,
    rating_change      INT         NOT NULL,
    rating_after       INT         NOT NULL,

    PRIMARY KEY (match_id, team_id),

    CONSTRAINT fk_match_team_results_match FOREIGN KEY (match_id) REFERENCES matches (match_id),

    CONSTRAINT fk_match_team_results_team FOREIGN KEY (team_id) REFERENCES teams (team_id),

    CONSTRAINT chk_match_team_results_rating_math CHECK (rating_before + rating_change = rating_after),

    CONSTRAINT chk_match_team_results_rating_nonnegative CHECK (rating_before >= 0 AND rating_after >= 0),

    INDEX              idx_match_team_results_team_history (team_id, match_id)
);

-- One row per participant, storing arena stats and XP snapshot
CREATE TABLE match_participant_results
(
    match_id             CHAR(36)    NOT NULL,
    player_id            BIGINT      NOT NULL,
    team_id              BIGINT      NOT NULL,
    player_name_snapshot VARCHAR(30) NOT NULL,
    kills                INT         NOT NULL,
    deaths               INT         NOT NULL,
    assists              INT         NOT NULL,
    xp_earned            INT         NOT NULL,

    PRIMARY KEY (match_id, player_id),

    CONSTRAINT fk_match_participant_results_team_result FOREIGN KEY (match_id, team_id)
        REFERENCES match_team_results (match_id, team_id),

    CONSTRAINT fk_match_participant_results_player FOREIGN KEY (player_id) REFERENCES players (player_id),

    CONSTRAINT chk_match_participant_results_stats CHECK (
        kills >= 0 AND deaths >= 0 AND assists >= 0 AND xp_earned >= 0
        ),

    INDEX                idx_match_participant_results_player_history (player_id, match_id)
);

-- Enforce that the winning team is
-- one of the two teams in stored team-result rows
ALTER TABLE matches
    ADD CONSTRAINT fk_matches_winner
        FOREIGN KEY (match_id, winning_team_id)
            REFERENCES match_team_results (match_id, team_id);