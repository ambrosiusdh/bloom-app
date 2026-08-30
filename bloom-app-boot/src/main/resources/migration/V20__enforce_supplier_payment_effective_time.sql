-- Supplier-payment creation/posting acquires its idempotency advisory lock,
-- then the cash-session row for CASH, then the receipt row. This statement is
-- deliberately limited to creation; voids have a separate immutable-ledger path.
CREATE OR REPLACE FUNCTION enforce_supplier_payment_insert()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    receipt_supplier_id BIGINT;
    receipt_total NUMERIC(19,4);
    receipt_status VARCHAR(20);
    already_paid NUMERIC(19,4);
    cash_session_opened_at TIMESTAMP;
BEGIN
    IF NEW.is_voided THEN
        RAISE EXCEPTION 'a supplier payment must be recorded active and voided separately';
    END IF;

    IF NEW.paid_at > clock_timestamp()::timestamp THEN
        RAISE EXCEPTION 'supplier payment paid_at cannot be in the future';
    END IF;

    IF NEW.payment_method = 'CASH' THEN
        SELECT opened_at
        INTO cash_session_opened_at
        FROM cash_sessions
        WHERE id = NEW.cash_session_id
          AND status = 'OPEN'
        FOR UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'cash session % is closed or does not exist', NEW.cash_session_id;
        END IF;
        IF NEW.paid_at < cash_session_opened_at THEN
            RAISE EXCEPTION 'cash supplier payment cannot predate cash session %', NEW.cash_session_id;
        END IF;
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

    RETURN NEW;
END;
$$;

COMMENT ON COLUMN cash_movements.recorded_at IS
    'Server recording timestamp; supplier_payments.paid_at is the separate business-effective time';
