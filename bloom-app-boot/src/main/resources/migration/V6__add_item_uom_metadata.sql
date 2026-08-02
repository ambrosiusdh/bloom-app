-- Existing inventory is integer-based test data, so PIECE/non-fractional is
-- the deterministic backfill until item-specific business metadata is supplied.
ALTER TABLE items
    ADD COLUMN base_unit_of_measure VARCHAR(30),
    ADD COLUMN fractional_quantity_allowed BOOLEAN;

UPDATE items
SET base_unit_of_measure = 'PIECE',
    fractional_quantity_allowed = FALSE;

ALTER TABLE items
    ALTER COLUMN base_unit_of_measure SET DEFAULT 'PIECE',
    ALTER COLUMN base_unit_of_measure SET NOT NULL,
    ALTER COLUMN fractional_quantity_allowed SET DEFAULT FALSE,
    ALTER COLUMN fractional_quantity_allowed SET NOT NULL;
