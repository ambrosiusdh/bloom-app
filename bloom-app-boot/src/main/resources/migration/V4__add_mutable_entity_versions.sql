ALTER TABLE cash_sessions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE expenses
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE item_category_counters
    DROP COLUMN version;

ALTER TABLE cash_sessions
    ALTER COLUMN version DROP DEFAULT;

ALTER TABLE expenses
    ALTER COLUMN version DROP DEFAULT;
