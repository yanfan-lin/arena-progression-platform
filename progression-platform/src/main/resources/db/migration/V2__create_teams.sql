-- Arena teams and their state.
-- A team belongs to exactly one arena mode (3v3 or 5v5),
-- and starts with no arena rating.
CREATE TABLE teams
(
    team_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name           VARCHAR(50) NOT NULL,
    mode           VARCHAR(24) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    rating         INT NULL,
    matches_played INT         NOT NULL DEFAULT 0,
    wins           INT         NOT NULL DEFAULT 0,
    losses         INT         NOT NULL DEFAULT 0,
    total_kills    INT         NOT NULL DEFAULT 0,
    total_deaths   INT         NOT NULL DEFAULT 0,
    total_assists  INT         NOT NULL DEFAULT 0,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    activated_at   DATETIME(6) NULL,
    retired_at     DATETIME(6) NULL,
    CONSTRAINT uk_teams_mode_name UNIQUE (mode, name),
    CONSTRAINT chk_teams_mode CHECK (mode IN ('THREE_VS_THREE', 'FIVE_VS_FIVE')),
    CONSTRAINT chk_teams_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT chk_teams_rating_draft CHECK (
        (status = 'DRAFT' AND rating IS NULL)
            OR (status IN ('ACTIVE', 'RETIRED') AND rating IS NOT NULL)
        ),
    CONSTRAINT chk_teams_matches_played CHECK (matches_played = wins + losses),
    CONSTRAINT chk_teams_stats_nonnegative CHECK (
        wins >= 0 AND losses >= 0 AND matches_played >= 0 AND
        total_kills >= 0 AND total_deaths >= 0 AND total_assists >= 0
        )

);