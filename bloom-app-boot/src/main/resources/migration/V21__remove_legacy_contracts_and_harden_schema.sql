-- Release 1 destructive cleanup. Refuse to discard audit rows from databases
-- that passed through an older, unreleased lineage: there is no approved rule
-- for reconstructing those rows as authoritative stock movements.
DO $$
DECLARE
    legacy_row_count BIGINT;
BEGIN
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
            RAISE EXCEPTION
                'Cannot remove item_audit_logs: % rows remain; migrate required history before V21',
                legacy_row_count;
        END IF;
    END IF;
END
$$;

DROP TABLE IF EXISTS stock_movement_legacy_audit_links;
DROP TABLE IF EXISTS item_audit_logs;

-- stock_store and stock_warehouse were populated from this legacy aggregate in
-- V3 and have been the only runtime-maintained balances since then.
ALTER TABLE items
    DROP CONSTRAINT chk_items_stock_quantity_nonnegative,
    DROP COLUMN stock_quantity;

-- Preserve the immutable receipt-time supplier name while making its snapshot
-- semantics explicit in both the database and the JPA mapping.
ALTER TABLE goods_receipts
    RENAME COLUMN supplier_name TO supplier_name_snapshot;

COMMENT ON COLUMN goods_receipts.supplier_name_snapshot IS
    'Immutable supplier name snapshot captured when the receipt is posted';

-- Authoritative Release 1 facts must be present and use NUMERIC(19,4).
ALTER TABLE items
    ALTER COLUMN price TYPE NUMERIC(19,4) USING price::NUMERIC(19,4),
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN price SET NOT NULL,
    ALTER COLUMN item_category_id SET NOT NULL,
    ADD CONSTRAINT chk_items_price_nonnegative CHECK (price >= 0);

ALTER TABLE sales
    ALTER COLUMN subtotal_amount TYPE NUMERIC(19,4)
        USING subtotal_amount::NUMERIC(19,4),
    ALTER COLUMN discount_amount TYPE NUMERIC(19,4)
        USING discount_amount::NUMERIC(19,4),
    ALTER COLUMN total_amount TYPE NUMERIC(19,4)
        USING total_amount::NUMERIC(19,4),
    ALTER COLUMN paid_amount TYPE NUMERIC(19,4)
        USING paid_amount::NUMERIC(19,4),
    ALTER COLUMN change_amount TYPE NUMERIC(19,4)
        USING change_amount::NUMERIC(19,4),
    ALTER COLUMN subtotal_amount SET NOT NULL,
    ALTER COLUMN discount_amount SET NOT NULL,
    ALTER COLUMN total_amount SET NOT NULL,
    ALTER COLUMN paid_amount SET NOT NULL,
    ADD CONSTRAINT chk_sales_amounts
        CHECK (
            subtotal_amount > 0
            AND discount_amount >= 0
            AND total_amount > 0
            AND paid_amount >= 0
            AND total_amount = subtotal_amount - discount_amount
        );

ALTER TABLE sale_items
    ALTER COLUMN quantity TYPE NUMERIC(19,4) USING quantity::NUMERIC(19,4),
    ALTER COLUMN unit_price TYPE NUMERIC(19,4) USING unit_price::NUMERIC(19,4),
    ALTER COLUMN subtotal TYPE NUMERIC(19,4) USING subtotal::NUMERIC(19,4),
    ALTER COLUMN unit_price SET NOT NULL,
    ALTER COLUMN subtotal SET NOT NULL,
    ADD CONSTRAINT chk_sale_items_location
        CHECK (stock_location IN ('STORE', 'WAREHOUSE')),
    ADD CONSTRAINT chk_sale_items_money
        CHECK (
            unit_price >= 0
            AND subtotal >= 0
            AND subtotal = ROUND(quantity * unit_price, 4)
        );

ALTER TABLE stock_adjustments
    ALTER COLUMN reason SET NOT NULL,
    ADD CONSTRAINT chk_stock_adjustments_reason_not_blank
        CHECK (LENGTH(BTRIM(reason)) > 0);

ALTER TABLE stock_adjustment_items
    ALTER COLUMN change_quantity TYPE NUMERIC(19,4)
        USING change_quantity::NUMERIC(19,4),
    ALTER COLUMN previous_stock TYPE NUMERIC(19,4)
        USING previous_stock::NUMERIC(19,4),
    ALTER COLUMN new_stock TYPE NUMERIC(19,4)
        USING new_stock::NUMERIC(19,4),
    ADD CONSTRAINT chk_stock_adjustment_items_location
        CHECK (stock_location IN ('STORE', 'WAREHOUSE')),
    ADD CONSTRAINT chk_stock_adjustment_items_action
        CHECK (action_type IN ('ADD', 'REMOVE', 'CORRECTION')),
    ADD CONSTRAINT chk_stock_adjustment_items_quantity_semantics
        CHECK (
            (action_type IN ('ADD', 'REMOVE') AND change_quantity > 0)
            OR (action_type = 'CORRECTION' AND change_quantity >= 0)
        ),
    ADD CONSTRAINT chk_stock_adjustment_items_balance_equation
        CHECK (
            (action_type = 'ADD' AND new_stock = previous_stock + change_quantity)
            OR (action_type = 'REMOVE' AND new_stock = previous_stock - change_quantity)
            OR (action_type = 'CORRECTION' AND new_stock = change_quantity)
        );

ALTER TABLE goods_receipt_items
    ALTER COLUMN quantity TYPE NUMERIC(19,4) USING quantity::NUMERIC(19,4),
    ALTER COLUMN purchase_price TYPE NUMERIC(19,4)
        USING purchase_price::NUMERIC(19,4),
    ALTER COLUMN line_total TYPE NUMERIC(19,4) USING line_total::NUMERIC(19,4),
    ADD CONSTRAINT chk_goods_receipt_items_location
        CHECK (stock_location IN ('STORE', 'WAREHOUSE'));

ALTER TABLE stock_movements
    ALTER COLUMN quantity TYPE NUMERIC(19,4) USING quantity::NUMERIC(19,4),
    ALTER COLUMN qty_before TYPE NUMERIC(19,4) USING qty_before::NUMERIC(19,4),
    ALTER COLUMN qty_after TYPE NUMERIC(19,4) USING qty_after::NUMERIC(19,4);

ALTER TABLE stock_transfer_lines
    ALTER COLUMN quantity TYPE NUMERIC(19,4) USING quantity::NUMERIC(19,4);

ALTER TABLE goods_receipts
    ALTER COLUMN total_amount TYPE NUMERIC(19,4)
        USING total_amount::NUMERIC(19,4),
    ADD CONSTRAINT chk_goods_receipts_supplier_snapshot_not_blank
        CHECK (LENGTH(BTRIM(supplier_name_snapshot)) > 0),
    ADD CONSTRAINT chk_goods_receipts_create_idempotency_key_not_blank
        CHECK (LENGTH(BTRIM(create_idempotency_key)) > 0),
    ADD CONSTRAINT chk_goods_receipts_create_request_hash
        CHECK (create_request_hash ~ '^[0-9a-f]{64}$');

ALTER TABLE supplier_payments
    ALTER COLUMN amount TYPE NUMERIC(19,4) USING amount::NUMERIC(19,4);

ALTER TABLE cash_sessions
    ALTER COLUMN opening_cash TYPE NUMERIC(19,4) USING opening_cash::NUMERIC(19,4),
    ALTER COLUMN expected_closing_cash TYPE NUMERIC(19,4)
        USING expected_closing_cash::NUMERIC(19,4),
    ALTER COLUMN actual_closing_cash TYPE NUMERIC(19,4)
        USING actual_closing_cash::NUMERIC(19,4),
    ALTER COLUMN difference TYPE NUMERIC(19,4) USING difference::NUMERIC(19,4);

ALTER TABLE cash_movements
    ALTER COLUMN amount TYPE NUMERIC(19,4) USING amount::NUMERIC(19,4),
    ADD CONSTRAINT chk_cash_movements_idempotency_key_not_blank
        CHECK (LENGTH(BTRIM(idempotency_key)) > 0);

ALTER TABLE expenses
    ALTER COLUMN amount TYPE NUMERIC(19,4) USING amount::NUMERIC(19,4);

ALTER TABLE item_category_counters
    ADD CONSTRAINT chk_item_category_counters_sequence_nonnegative
        CHECK (current_sequence >= 0);

ALTER TABLE document_counters
    ADD CONSTRAINT chk_document_counters_sequence_nonnegative
        CHECK (current_sequence >= 0);

-- History-bearing aggregates and their lines must not be physically erased by
-- deleting a parent. Master-data references are restricted for the same reason.
ALTER TABLE item_category_counters
    DROP CONSTRAINT fk_item_category_counters_item_category,
    ADD CONSTRAINT fk_item_category_counters_item_category
        FOREIGN KEY (item_category_id) REFERENCES item_categories(id) ON DELETE RESTRICT;

ALTER TABLE items
    DROP CONSTRAINT fk_item_category,
    ADD CONSTRAINT fk_item_category
        FOREIGN KEY (item_category_id) REFERENCES item_categories(id) ON DELETE RESTRICT;

ALTER TABLE sale_items
    DROP CONSTRAINT fk_sale_items_sale,
    ADD CONSTRAINT fk_sale_items_sale
        FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE RESTRICT;

ALTER TABLE stock_adjustment_items
    DROP CONSTRAINT fk_stock_adjustment_items_stock_adjustment,
    ADD CONSTRAINT fk_stock_adjustment_items_stock_adjustment
        FOREIGN KEY (stock_adjustment_id)
        REFERENCES stock_adjustments(id) ON DELETE RESTRICT;

ALTER TABLE goods_receipt_items
    DROP CONSTRAINT fk_goods_receipt_items_receipt,
    ADD CONSTRAINT fk_goods_receipt_items_receipt
        FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(id) ON DELETE RESTRICT;

ALTER TABLE stock_transfer_lines
    DROP CONSTRAINT fk_stock_transfer_lines_transfer,
    ADD CONSTRAINT fk_stock_transfer_lines_transfer
        FOREIGN KEY (stock_transfer_id) REFERENCES stock_transfers(id) ON DELETE RESTRICT;
