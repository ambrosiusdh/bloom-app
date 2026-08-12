-- One-time development-database reconciliation for the unreleased audit-ledger cleanup.
--
-- Run this only against a database that previously applied Bloom migrations V1-V14.
-- The script preserves authoritative stock_movements and application data, removes only
-- the empty legacy audit tables, installs the final V12 constraints, and aligns Flyway's
-- checksums with the rewritten pre-release migration files.
--
-- Recommended invocation:
--   psql -h localhost -U postgres -d bloom-app-db \
--     -v ON_ERROR_STOP=1 -f scripts/dev/reconcile-unreleased-flyway-to-v12.sql

BEGIN;

DO $$
DECLARE
    legacy_row_count BIGINT;
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NULL THEN
        RAISE EXCEPTION 'flyway_schema_history does not exist';
    END IF;
    IF to_regclass('public.stock_movements') IS NULL THEN
        RAISE EXCEPTION 'stock_movements does not exist';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM flyway_schema_history
        WHERE version = '12' AND success
    ) THEN
        RAISE EXCEPTION 'Successful Flyway V12 history is required';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM stock_movements
        WHERE reference_no IS NULL OR btrim(reference_no) = ''
    ) THEN
        RAISE EXCEPTION 'Cannot reconcile: stock movements have missing references';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM stock_movements
        WHERE (source_type = 'STOCK_ADJUSTMENT'
                   AND (adjustment_action_type IS NULL
                       OR adjustment_action_type NOT IN ('ADD', 'REMOVE', 'CORRECTION')))
           OR (source_type <> 'STOCK_ADJUSTMENT'
                   AND adjustment_action_type IS NOT NULL)
    ) THEN
        RAISE EXCEPTION 'Cannot reconcile: stock movement adjustment metadata is inconsistent';
    END IF;

    IF to_regclass('public.stock_movement_legacy_audit_links') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM stock_movement_legacy_audit_links'
            INTO legacy_row_count;
        IF legacy_row_count <> 0 THEN
            RAISE EXCEPTION
                'Cannot remove stock_movement_legacy_audit_links: % rows remain',
                legacy_row_count;
        END IF;
    END IF;

    IF to_regclass('public.item_audit_logs') IS NOT NULL THEN
        EXECUTE 'SELECT count(*) FROM item_audit_logs' INTO legacy_row_count;
        IF legacy_row_count <> 0 THEN
            RAISE EXCEPTION 'Cannot remove item_audit_logs: % rows remain', legacy_row_count;
        END IF;
    END IF;
END
$$;

ALTER TABLE stock_movements
    ALTER COLUMN reference_no SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'public.stock_movements'::regclass
          AND conname = 'chk_stock_movements_adjustment_action'
    ) THEN
        ALTER TABLE stock_movements
            ADD CONSTRAINT chk_stock_movements_adjustment_action
            CHECK (
                (source_type = 'STOCK_ADJUSTMENT'
                    AND adjustment_action_type IN ('ADD', 'REMOVE', 'CORRECTION'))
                OR
                (source_type <> 'STOCK_ADJUSTMENT'
                    AND adjustment_action_type IS NULL)
            );
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_stock_movements_history_order
    ON stock_movements (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_stock_movements_product_history
    ON stock_movements (product_id, created_at DESC, id DESC);

DROP TABLE IF EXISTS stock_movement_legacy_audit_links;
DROP TABLE IF EXISTS item_audit_logs;

-- These values are Flyway 9.22.3 checksums for the migration files committed with
-- this reconciliation script. Do not copy this block to a different revision.
UPDATE flyway_schema_history
SET checksum = CASE version
        WHEN '1' THEN 1376590416
        WHEN '3' THEN 1483671887
        WHEN '7' THEN 358417064
        WHEN '12' THEN -1635802157
    END,
    description = CASE version
        WHEN '12' THEN 'add stock movement metadata'
        ELSE description
    END,
    script = CASE version
        WHEN '12' THEN 'V12__add_stock_movement_metadata.sql'
        ELSE script
    END
WHERE version IN ('1', '3', '7', '12');

DELETE FROM flyway_schema_history
WHERE version IN ('13', '14');

DO $$
BEGIN
    IF (SELECT count(*) FROM flyway_schema_history WHERE version IN ('1', '3', '7', '12')) <> 4 THEN
        RAISE EXCEPTION 'Flyway history reconciliation did not update all expected versions';
    END IF;
    IF EXISTS (SELECT 1 FROM flyway_schema_history WHERE version IN ('13', '14')) THEN
        RAISE EXCEPTION 'Flyway V13/V14 history still exists';
    END IF;
END
$$;

COMMIT;

SELECT version, description, script, checksum
FROM flyway_schema_history
ORDER BY installed_rank;
