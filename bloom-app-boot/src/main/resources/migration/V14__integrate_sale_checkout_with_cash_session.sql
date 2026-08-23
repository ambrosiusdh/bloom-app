-- Checkout facts cannot be reconstructed safely for historical sales: there is
-- no deterministic cash session or client idempotency key to assign after the fact.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sales) THEN
        RAISE EXCEPTION
            'Cannot integrate historical sales with cash sessions without an approved backfill';
    END IF;
END
$$;

ALTER TABLE sales
    ADD COLUMN cash_session_id BIGINT NOT NULL,
    ADD COLUMN change_amount NUMERIC(19,4) NOT NULL,
    ADD COLUMN checkout_idempotency_key VARCHAR(100) NOT NULL,
    ADD COLUMN checkout_request_hash VARCHAR(64) NOT NULL,
    ADD CONSTRAINT fk_sales_cash_session
        FOREIGN KEY (cash_session_id) REFERENCES cash_sessions(id),
    ADD CONSTRAINT uq_sales_checkout_idempotency_key
        UNIQUE (checkout_idempotency_key),
    ADD CONSTRAINT chk_sales_checkout_idempotency_key_not_blank
        CHECK (LENGTH(BTRIM(checkout_idempotency_key)) > 0),
    ADD CONSTRAINT chk_sales_checkout_request_hash
        CHECK (checkout_request_hash ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT chk_sales_change_non_negative
        CHECK (change_amount >= 0),
    ADD CONSTRAINT chk_sales_payment_settlement
        CHECK (
            (payment_type = 'CASH'
                AND paid_amount >= total_amount
                AND change_amount = paid_amount - total_amount)
            OR
            (payment_type = 'QRIS'
                AND paid_amount = total_amount
                AND change_amount = 0.0000)
        );

CREATE INDEX idx_sales_cash_session_id
    ON sales(cash_session_id);

CREATE OR REPLACE FUNCTION require_open_cash_session_for_sale()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- Serialize sale posting with session close even for non-cash checkout.
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

CREATE TRIGGER trg_sales_require_open_session
BEFORE INSERT ON sales
FOR EACH ROW
EXECUTE FUNCTION require_open_cash_session_for_sale();

COMMENT ON COLUMN sales.paid_amount IS
    'Tendered cash for CASH checkout; exact settled amount for QRIS checkout';
COMMENT ON COLUMN sales.change_amount IS
    'Server-calculated paid_amount minus total_amount for CASH; zero for QRIS';
COMMENT ON COLUMN sales.checkout_request_hash IS
    'SHA-256 of the canonical checkout request, used to reject conflicting key reuse';
