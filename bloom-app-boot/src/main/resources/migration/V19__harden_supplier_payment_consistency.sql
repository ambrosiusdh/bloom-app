CREATE INDEX idx_supplier_payments_active_receipt
    ON supplier_payments(goods_receipt_id)
    WHERE is_voided = FALSE;

CREATE INDEX idx_supplier_payments_active_supplier
    ON supplier_payments(supplier_id)
    WHERE is_voided = FALSE;

-- Drawer-affecting workflows use cash session -> business document as their
-- global lock order. Replacing the V18 function here preserves that order for
-- direct SQL without changing the checksum of an already-shared migration.
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

CREATE OR REPLACE FUNCTION reject_goods_receipt_cancellation_with_active_payments()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'POSTED'
        AND NEW.status = 'CANCELLED'
        AND EXISTS (
            SELECT 1
            FROM supplier_payments payment
            WHERE payment.goods_receipt_id = OLD.id
              AND payment.is_voided = FALSE
        ) THEN
        RAISE EXCEPTION
            'goods receipt % cannot be cancelled while it has active supplier payments', OLD.id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_goods_receipts_reject_cancel_with_active_payments
BEFORE UPDATE OF status ON goods_receipts
FOR EACH ROW
WHEN (OLD.status IS DISTINCT FROM NEW.status)
EXECUTE FUNCTION reject_goods_receipt_cancellation_with_active_payments();

COMMENT ON COLUMN supplier_payments.paid_at IS
    'Business-effective payment time; CASH payments cannot predate their linked cash session';
