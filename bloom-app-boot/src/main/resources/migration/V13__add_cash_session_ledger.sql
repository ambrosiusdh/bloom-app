ALTER TABLE cash_sessions
    RENAME COLUMN user_id TO opened_by_id;

ALTER TABLE cash_sessions
    RENAME COLUMN closing_cash TO actual_closing_cash;

ALTER TABLE cash_sessions
    RENAME CONSTRAINT fk_cash_sessions_user TO fk_cash_sessions_opened_by;

ALTER INDEX idx_cash_sessions_user_id
    RENAME TO idx_cash_sessions_opened_by_id;

ALTER TABLE cash_sessions
    ADD COLUMN expected_closing_cash NUMERIC(19, 4),
    ADD COLUMN difference NUMERIC(19, 4),
    ADD COLUMN closed_by_id BIGINT;

UPDATE cash_sessions
SET expected_closing_cash = opening_cash;

-- Legacy CLOSED rows predate a ledger and closed-by field. Preserve the known
-- actual count, use it as the best available expectation, and retain the
-- original opener as the only historically known actor.
UPDATE cash_sessions
SET expected_closing_cash = COALESCE(actual_closing_cash, opening_cash),
    actual_closing_cash = COALESCE(actual_closing_cash, opening_cash),
    difference = 0.0000,
    closed_at = COALESCE(closed_at, opened_at),
    closed_by_id = opened_by_id
WHERE status = 'CLOSED';

ALTER TABLE cash_sessions
    ALTER COLUMN expected_closing_cash SET NOT NULL,
    ADD CONSTRAINT fk_cash_sessions_closed_by
        FOREIGN KEY (closed_by_id) REFERENCES users(id),
    ADD CONSTRAINT chk_cash_sessions_opening_cash_non_negative
        CHECK (opening_cash >= 0),
    ADD CONSTRAINT chk_cash_sessions_actual_cash_non_negative
        CHECK (actual_closing_cash IS NULL OR actual_closing_cash >= 0),
    ADD CONSTRAINT chk_cash_sessions_difference
        CHECK (
            status <> 'CLOSED'
            OR difference = actual_closing_cash - expected_closing_cash
        ),
    ADD CONSTRAINT chk_cash_sessions_status
        CHECK (status IN ('OPEN', 'CLOSED')),
    ADD CONSTRAINT chk_cash_sessions_lifecycle
        CHECK (
            (status = 'OPEN'
                AND actual_closing_cash IS NULL
                AND difference IS NULL
                AND closed_at IS NULL
                AND closed_by_id IS NULL)
            OR
            (status = 'CLOSED'
                AND actual_closing_cash IS NOT NULL
                AND difference IS NOT NULL
                AND closed_at IS NOT NULL
                AND closed_by_id IS NOT NULL)
        );

CREATE INDEX idx_cash_sessions_closed_by_id
    ON cash_sessions(closed_by_id);

-- This database invariant is intentionally repeated in the alignment migration:
-- an application-level check alone cannot stop two concurrent inserts.
DROP INDEX IF EXISTS uq_cash_sessions_single_open;
CREATE UNIQUE INDEX uq_cash_sessions_single_open
    ON cash_sessions(status)
    WHERE status = 'OPEN';

CREATE TABLE cash_movements (
    id BIGSERIAL PRIMARY KEY,
    cash_session_id BIGINT NOT NULL,
    movement_type VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT NOT NULL,
    reference_no VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    actor VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    CONSTRAINT fk_cash_movements_session
        FOREIGN KEY (cash_session_id) REFERENCES cash_sessions(id),
    CONSTRAINT chk_cash_movements_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_cash_movements_source_id_positive
        CHECK (source_id > 0),
    CONSTRAINT chk_cash_movements_direction
        CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT chk_cash_movements_type
        CHECK (movement_type IN ('SALE_PAYMENT', 'SUPPLIER_PAYMENT', 'EXPENSE')),
    CONSTRAINT chk_cash_movements_source_type
        CHECK (source_type IN ('SALE', 'SUPPLIER_PAYMENT', 'EXPENSE')),
    CONSTRAINT chk_cash_movements_approved_semantics
        CHECK (
            (movement_type = 'SALE_PAYMENT' AND source_type = 'SALE' AND direction = 'IN')
            OR
            (movement_type = 'SUPPLIER_PAYMENT'
                AND source_type = 'SUPPLIER_PAYMENT' AND direction = 'OUT')
            OR
            (movement_type = 'EXPENSE' AND source_type = 'EXPENSE' AND direction = 'OUT')
        )
);

CREATE INDEX idx_cash_movements_session_time
    ON cash_movements(cash_session_id, recorded_at, id);

CREATE INDEX idx_cash_movements_source
    ON cash_movements(source_type, source_id);

CREATE UNIQUE INDEX uq_cash_movements_idempotency_key
    ON cash_movements(idempotency_key);

CREATE OR REPLACE FUNCTION require_open_cash_session_for_movement()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- The row lock makes a direct SQL insert obey the same close-vs-post
    -- serialization rule as the application service.
    PERFORM 1
    FROM cash_sessions
    WHERE id = NEW.cash_session_id
      AND status = 'OPEN'
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'cash session % is closed or does not exist', NEW.cash_session_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_cash_movements_require_open_session
BEFORE INSERT ON cash_movements
FOR EACH ROW
EXECUTE FUNCTION require_open_cash_session_for_movement();

CREATE OR REPLACE FUNCTION reject_cash_movement_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'cash movements are immutable; record a compensating approved movement';
END;
$$;

CREATE TRIGGER trg_cash_movements_immutable
BEFORE UPDATE OR DELETE ON cash_movements
FOR EACH ROW
EXECUTE FUNCTION reject_cash_movement_mutation();

CREATE OR REPLACE FUNCTION reject_closed_cash_session_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'CLOSED' THEN
        RAISE EXCEPTION 'closed cash sessions are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_closed_cash_sessions_immutable
BEFORE UPDATE OR DELETE ON cash_sessions
FOR EACH ROW
EXECUTE FUNCTION reject_closed_cash_session_mutation();
