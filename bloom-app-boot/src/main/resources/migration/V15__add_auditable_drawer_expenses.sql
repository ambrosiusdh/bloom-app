-- Expense history predating the auditable ledger cannot be reconstructed safely.
-- Refuse to invent backdated movements, especially against reconciled sessions.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM expenses) THEN
        RAISE EXCEPTION
            'Cannot enable auditable expenses while unledgered historical expenses exist';
    END IF;
END
$$;

ALTER TABLE expenses
    ADD COLUMN voided_at TIMESTAMP,
    ADD COLUMN voided_by VARCHAR(255),
    ADD COLUMN create_idempotency_key VARCHAR(100) NOT NULL,
    ADD COLUMN create_request_hash VARCHAR(64) NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN created_by SET NOT NULL,
    ADD CONSTRAINT chk_expenses_amount_positive
        CHECK (amount > 0),
    ADD CONSTRAINT uq_expenses_create_idempotency_key
        UNIQUE (create_idempotency_key),
    ADD CONSTRAINT chk_expenses_create_idempotency_key_not_blank
        CHECK (LENGTH(BTRIM(create_idempotency_key)) > 0),
    ADD CONSTRAINT chk_expenses_create_request_hash
        CHECK (create_request_hash ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT chk_expenses_category
        CHECK (category IN (
            'STORE_OPERATIONAL',
            'FOOD_AND_DRINK',
            'CHARITY',
            'EMERGENCY_PURCHASE',
            'OWNER_WITHDRAWAL',
            'OTHER'
        )),
    ADD CONSTRAINT chk_expenses_other_description
        CHECK (
            category <> 'OTHER'
            OR (description IS NOT NULL AND LENGTH(BTRIM(description)) > 0)
        ),
    ADD CONSTRAINT chk_expenses_void_lifecycle
        CHECK (
            (is_voided = FALSE
                AND voided_reason IS NULL
                AND voided_at IS NULL
                AND voided_by IS NULL)
            OR
            (is_voided = TRUE
                AND voided_reason IS NOT NULL
                AND LENGTH(BTRIM(voided_reason)) > 0
                AND voided_at IS NOT NULL
                AND voided_by IS NOT NULL
                AND LENGTH(BTRIM(voided_by)) > 0)
        );

ALTER TABLE cash_movements
    DROP CONSTRAINT chk_cash_movements_type,
    DROP CONSTRAINT chk_cash_movements_approved_semantics,
    ADD CONSTRAINT chk_cash_movements_type
        CHECK (movement_type IN (
            'SALE_PAYMENT',
            'SUPPLIER_PAYMENT',
            'EXPENSE',
            'EXPENSE_REVERSAL'
        )),
    ADD CONSTRAINT chk_cash_movements_approved_semantics
        CHECK (
            (movement_type = 'SALE_PAYMENT' AND source_type = 'SALE' AND direction = 'IN')
            OR
            (movement_type = 'SUPPLIER_PAYMENT'
                AND source_type = 'SUPPLIER_PAYMENT' AND direction = 'OUT')
            OR
            (movement_type = 'EXPENSE'
                AND source_type = 'EXPENSE' AND direction = 'OUT')
            OR
            (movement_type = 'EXPENSE_REVERSAL'
                AND source_type = 'EXPENSE' AND direction = 'IN')
        );

CREATE UNIQUE INDEX uq_cash_movements_expense_posting
    ON cash_movements(source_id)
    WHERE movement_type = 'EXPENSE';

CREATE UNIQUE INDEX uq_cash_movements_expense_reversal
    ON cash_movements(source_id)
    WHERE movement_type = 'EXPENSE_REVERSAL';

CREATE OR REPLACE FUNCTION require_open_cash_session_for_expense()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM 1
    FROM cash_sessions
    WHERE id = NEW.cash_session_id
      AND status = 'OPEN'
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'cash session % is closed or does not exist', NEW.cash_session_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_expenses_require_open_session
BEFORE INSERT ON expenses
FOR EACH ROW
EXECUTE FUNCTION require_open_cash_session_for_expense();

CREATE OR REPLACE FUNCTION enforce_expense_immutability_and_void()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'expenses are immutable and cannot be deleted';
    END IF;

    IF NEW.cash_session_id IS DISTINCT FROM OLD.cash_session_id
        OR NEW.create_idempotency_key IS DISTINCT FROM OLD.create_idempotency_key
        OR NEW.create_request_hash IS DISTINCT FROM OLD.create_request_hash
        OR NEW.amount IS DISTINCT FROM OLD.amount
        OR NEW.category IS DISTINCT FROM OLD.category
        OR NEW.description IS DISTINCT FROM OLD.description
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.created_by IS DISTINCT FROM OLD.created_by THEN
        RAISE EXCEPTION
            'posted expense facts are immutable; use the void operation';
    END IF;

    IF OLD.is_voided
        OR NOT NEW.is_voided
        OR NEW.voided_reason IS NULL
        OR LENGTH(BTRIM(NEW.voided_reason)) = 0
        OR NEW.voided_at IS NULL
        OR NEW.voided_by IS NULL
        OR LENGTH(BTRIM(NEW.voided_by)) = 0 THEN
        RAISE EXCEPTION
            'the only permitted expense update is a complete first-time void';
    END IF;

    PERFORM 1
    FROM cash_sessions
    WHERE id = OLD.cash_session_id
      AND status = 'OPEN'
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION
            'cash session % is closed and rejects expense voids', OLD.cash_session_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_expenses_immutable_except_void
BEFORE UPDATE OR DELETE ON expenses
FOR EACH ROW
EXECUTE FUNCTION enforce_expense_immutability_and_void();

COMMENT ON COLUMN expenses.amount IS
    'Unexpected cash paid from the store drawer; always positive NUMERIC(19,4)';
COMMENT ON COLUMN expenses.category IS
    'OWNER_WITHDRAWAL is retained separately from ordinary operational expenses';
COMMENT ON COLUMN expenses.voided_reason IS
    'Mandatory audit reason for the immutable compensating reversal';
