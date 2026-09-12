-- All report APIs require authenticated membership.
-- Anonymous share identifiers and per-report visibility are no longer used.
ALTER TABLE report DROP COLUMN public_id;
ALTER TABLE report DROP COLUMN visibility;
