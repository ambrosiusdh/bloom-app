-- A mutable receipt balance cannot be converted into an auditable payment fact.
-- Refuse to discard any unexplained legacy balance during the ledger cut-over.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM goods_receipts
        WHERE paid_amount <> 0
    ) THEN
        RAISE EXCEPTION
            'Cannot enable supplier-payment ledger while goods_receipts.paid_amount contains non-zero unaudited balances';
    END IF;
END
$$;

ALTER TABLE goods_receipts
    DROP COLUMN paid_amount;

CREATE TABLE supplier_payments (
    id BIGSERIAL PRIMARY KEY,
    goods_receipt_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    cash_session_id BIGINT,
    amount NUMERIC(19,4) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    paid_at TIMESTAMP NOT NULL,
    reference VARCHAR(255),
    note VARCHAR(255),
    actor VARCHAR(255) NOT NULL,
    is_voided BOOLEAN NOT NULL DEFAULT FALSE,
    void_reason VARCHAR(255),
    voided_at TIMESTAMP,
    voided_by VARCHAR(255),
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_supplier_payments_receipt
        FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(id),
    CONSTRAINT fk_supplier_payments_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_supplier_payments_cash_session
        FOREIGN KEY (cash_session_id) REFERENCES cash_sessions(id),
    CONSTRAINT uq_supplier_payments_idempotency_key
        UNIQUE (idempotency_key),
    CONSTRAINT chk_supplier_payments_amount_positive
        CHECK (amount > 0),
    CONSTRAINT chk_supplier_payments_method
        CHECK (payment_method IN ('CASH', 'BANK_TRANSFER', 'QRIS')),
    CONSTRAINT chk_supplier_payments_cash_session
        CHECK (
            (payment_method = 'CASH' AND cash_session_id IS NOT NULL)
            OR
            (payment_method IN ('BANK_TRANSFER', 'QRIS') AND cash_session_id IS NULL)
        ),
    CONSTRAINT chk_supplier_payments_idempotency_key_not_blank
        CHECK (LENGTH(BTRIM(idempotency_key)) > 0),
    CONSTRAINT chk_supplier_payments_request_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_supplier_payments_actor_not_blank
        CHECK (LENGTH(BTRIM(actor)) > 0),
    CONSTRAINT chk_supplier_payments_void_lifecycle
        CHECK (
            (is_voided = FALSE
                AND void_reason IS NULL
                AND voided_at IS NULL
                AND voided_by IS NULL)
            OR
            (is_voided = TRUE
                AND void_reason IS NOT NULL
                AND LENGTH(BTRIM(void_reason)) > 0
                AND voided_at IS NOT NULL
                AND voided_by IS NOT NULL
                AND LENGTH(BTRIM(voided_by)) > 0)
        )
);

ALTER TABLE supplier_payments
    ALTER COLUMN is_voided DROP DEFAULT,
    ALTER COLUMN version DROP DEFAULT;

CREATE INDEX idx_supplier_payments_receipt_history
    ON supplier_payments(goods_receipt_id, paid_at DESC, id DESC);

CREATE INDEX idx_supplier_payments_supplier
    ON supplier_payments(supplier_id);

CREATE OR REPLACE FUNCTION enforce_supplier_payment_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    receipt_supplier_id BIGINT;
    receipt_total NUMERIC(19,4);
    receipt_status VARCHAR(20);
    already_paid NUMERIC(19,4);
BEGIN
    IF NEW.is_voided THEN
        RAISE EXCEPTION 'a supplier payment must be recorded active and voided separately';
    END IF;

    SELECT supplier_id, total_amount, status
    INTO receipt_supplier_id, receipt_total, receipt_status
    FROM goods_receipts
    WHERE id = NEW.goods_receipt_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'goods receipt % does not exist', NEW.goods_receipt_id;
    END IF;
    IF receipt_status <> 'POSTED' THEN
        RAISE EXCEPTION 'goods receipt % is not posted', NEW.goods_receipt_id;
    END IF;
    IF NEW.supplier_id <> receipt_supplier_id THEN
        RAISE EXCEPTION 'supplier payment supplier does not match its goods receipt';
    END IF;

    SELECT COALESCE(SUM(amount), 0)
    INTO already_paid
    FROM supplier_payments
    WHERE goods_receipt_id = NEW.goods_receipt_id
      AND is_voided = FALSE;

    IF already_paid + NEW.amount > receipt_total THEN
        RAISE EXCEPTION 'supplier payment would overpay goods receipt %', NEW.goods_receipt_id;
    END IF;

    IF NEW.payment_method = 'CASH' THEN
        PERFORM 1
        FROM cash_sessions
        WHERE id = NEW.cash_session_id
          AND status = 'OPEN'
        FOR UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'cash session % is closed or does not exist', NEW.cash_session_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_supplier_payments_insert
BEFORE INSERT ON supplier_payments
FOR EACH ROW
EXECUTE FUNCTION enforce_supplier_payment_insert();

CREATE OR REPLACE FUNCTION enforce_supplier_payment_immutability_and_void()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'supplier payments are immutable and cannot be deleted';
    END IF;

    IF NEW.goods_receipt_id IS DISTINCT FROM OLD.goods_receipt_id
        OR NEW.supplier_id IS DISTINCT FROM OLD.supplier_id
        OR NEW.cash_session_id IS DISTINCT FROM OLD.cash_session_id
        OR NEW.amount IS DISTINCT FROM OLD.amount
        OR NEW.payment_method IS DISTINCT FROM OLD.payment_method
        OR NEW.paid_at IS DISTINCT FROM OLD.paid_at
        OR NEW.reference IS DISTINCT FROM OLD.reference
        OR NEW.note IS DISTINCT FROM OLD.note
        OR NEW.actor IS DISTINCT FROM OLD.actor
        OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key
        OR NEW.request_hash IS DISTINCT FROM OLD.request_hash
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'recorded supplier-payment facts are immutable; use the void operation';
    END IF;

    IF OLD.is_voided
        OR NOT NEW.is_voided
        OR NEW.void_reason IS NULL
        OR LENGTH(BTRIM(NEW.void_reason)) = 0
        OR NEW.voided_at IS NULL
        OR NEW.voided_by IS NULL
        OR LENGTH(BTRIM(NEW.voided_by)) = 0 THEN
        RAISE EXCEPTION 'the only permitted supplier-payment update is a complete first-time void';
    END IF;

    IF OLD.payment_method = 'CASH' THEN
        PERFORM 1
        FROM cash_sessions
        WHERE id = OLD.cash_session_id
          AND status = 'OPEN'
        FOR UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION
                'cash session % is closed and rejects supplier-payment voids', OLD.cash_session_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_supplier_payments_immutable_except_void
BEFORE UPDATE OR DELETE ON supplier_payments
FOR EACH ROW
EXECUTE FUNCTION enforce_supplier_payment_immutability_and_void();

ALTER TABLE cash_movements
    DROP CONSTRAINT chk_cash_movements_type,
    DROP CONSTRAINT chk_cash_movements_approved_semantics,
    ADD CONSTRAINT chk_cash_movements_type
        CHECK (movement_type IN (
            'SALE_PAYMENT',
            'SUPPLIER_PAYMENT',
            'SUPPLIER_PAYMENT_REVERSAL',
            'EXPENSE',
            'EXPENSE_REVERSAL'
        )),
    ADD CONSTRAINT chk_cash_movements_approved_semantics
        CHECK (
            (movement_type = 'SALE_PAYMENT'
                AND source_type = 'SALE' AND direction = 'IN')
            OR
            (movement_type = 'SUPPLIER_PAYMENT'
                AND source_type = 'SUPPLIER_PAYMENT' AND direction = 'OUT')
            OR
            (movement_type = 'SUPPLIER_PAYMENT_REVERSAL'
                AND source_type = 'SUPPLIER_PAYMENT' AND direction = 'IN')
            OR
            (movement_type = 'EXPENSE'
                AND source_type = 'EXPENSE' AND direction = 'OUT')
            OR
            (movement_type = 'EXPENSE_REVERSAL'
                AND source_type = 'EXPENSE' AND direction = 'IN')
        );

CREATE UNIQUE INDEX uq_cash_movements_supplier_payment_posting
    ON cash_movements(source_id)
    WHERE movement_type = 'SUPPLIER_PAYMENT';

CREATE UNIQUE INDEX uq_cash_movements_supplier_payment_reversal
    ON cash_movements(source_id)
    WHERE movement_type = 'SUPPLIER_PAYMENT_REVERSAL';

COMMENT ON TABLE supplier_payments IS
    'Immutable supplier accounts-payable transactions; active rows are the paid-amount source of truth';
COMMENT ON COLUMN supplier_payments.cash_session_id IS
    'Present only for CASH payments because bank transfer and QRIS do not affect drawer cash';
COMMENT ON COLUMN supplier_payments.is_voided IS
    'A void retains the original payment and excludes it from valid-payment aggregation';
