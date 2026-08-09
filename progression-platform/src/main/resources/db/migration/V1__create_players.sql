-- Player accounts and progression state;

-- display_name is case-insensitive;
-- ex: "John" and "john" are the same name,
-- so a retired name is never reused

CREATE TABLE players
(
    player_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(30) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    total_xp     BIGINT      NOT NULL DEFAULT 0,
    level        INT         NOT NULL DEFAULT 1,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    retired_at   DATETIME(6) NULL,
    CONSTRAINT uk_players_display_name UNIQUE (display_name),
    CONSTRAINT chk_players_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT chk_players_total_xp_nonnegative CHECK (total_xp >= 0),
    CONSTRAINT chk_players_level_min CHECK (level >= 1),
    CONSTRAINT chk_players_lifecycle CHECK (
        (status = 'ACTIVE' AND retired_at IS NULL)
            OR (status = 'RETIRED' AND retired_at IS NOT NULL)
        )

);
