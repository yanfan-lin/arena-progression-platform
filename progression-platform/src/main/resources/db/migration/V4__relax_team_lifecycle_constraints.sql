-- Teams may retire as drafts (no rating) or after activation (with rating),
-- replace old rating rule with new lifecycle rules
ALTER TABLE teams
DROP
CHECK chk_teams_rating_draft,
    ADD CONSTRAINT chk_teams_rating_lifecycle CHECK (
        (status = 'DRAFT' AND rating IS NULL)
        OR (status = 'ACTIVE' AND rating IS NOT NULL AND rating >= 0)
        OR (status = 'RETIRED' AND (rating IS NULL OR rating >= 0))
    ),
    ADD CONSTRAINT chk_teams_timestamps CHECK (
        (status = 'DRAFT' AND activated_at IS NULL AND retired_at IS NULL)
        OR (status = 'ACTIVE' AND activated_at IS NOT NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND retired_at IS NOT NULL)
    );