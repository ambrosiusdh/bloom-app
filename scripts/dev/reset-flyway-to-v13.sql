-- Use only when the former V14 demo seed was the last successful migration.
-- This retains all seeded business data and resets only Flyway history to V13.
-- The next application start applies V14__snapshot_legacy_stock_movement_metadata.sql.

BEGIN;

DO $$
DECLARE
    latest_version VARCHAR(50);
    latest_script VARCHAR(1000);
BEGIN
    SELECT version, script
    INTO latest_version, latest_script
    FROM flyway_schema_history
    WHERE success
    ORDER BY installed_rank DESC
    LIMIT 1;

    IF latest_version IS DISTINCT FROM '14'
        OR latest_script IS DISTINCT FROM 'V14__seed_release_1_demo_data.sql' THEN
        RAISE EXCEPTION
            'Refusing Flyway reset: expected deleted demo V14 to be the latest successful migration, found version %, script %',
            latest_version,
            latest_script;
    END IF;
END
$$;

DELETE FROM flyway_schema_history
WHERE version = '14'
  AND script = 'V14__seed_release_1_demo_data.sql';

DO $$
DECLARE
    latest_version VARCHAR(50);
BEGIN
    SELECT version
    INTO latest_version
    FROM flyway_schema_history
    WHERE success
    ORDER BY installed_rank DESC
    LIMIT 1;

    IF latest_version IS DISTINCT FROM '13' THEN
        RAISE EXCEPTION
            'Flyway reset did not stop at V13; latest successful version is %',
            latest_version;
    END IF;
END
$$;

COMMIT;
