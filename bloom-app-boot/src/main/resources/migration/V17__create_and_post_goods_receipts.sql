-- Release 1 goods receipts are posted atomically when created. Historical rows
-- are treated as posted because the pre-V17 service already created stock movements
-- during receipt creation.
ALTER TABLE goods_receipts
    ADD COLUMN create_idempotency_key VARCHAR(100),
    ADD COLUMN create_request_hash VARCHAR(64),
    ADD COLUMN status VARCHAR(20),
    ADD COLUMN cancelled_at TIMESTAMP,
    ADD COLUMN cancelled_by VARCHAR(255),
    ADD COLUMN cancellation_reason TEXT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE goods_receipts receipt
SET supplier_name = supplier.name
FROM suppliers supplier
WHERE receipt.supplier_id = supplier.id
  AND (receipt.supplier_name IS NULL OR BTRIM(receipt.supplier_name) = '');

UPDATE goods_receipts
SET create_idempotency_key = 'LEGACY-GOODS-RECEIPT-' || id,
    create_request_hash = REPEAT('0', 64),
    status = 'POSTED';

ALTER TABLE goods_receipts
    ALTER COLUMN supplier_name SET NOT NULL,
    ALTER COLUMN create_idempotency_key SET NOT NULL,
    ALTER COLUMN create_request_hash SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN version DROP DEFAULT,
    ADD CONSTRAINT uq_goods_receipts_create_idempotency_key
        UNIQUE (create_idempotency_key),
    ADD CONSTRAINT chk_goods_receipts_total_positive
        CHECK (total_amount > 0),
    ADD CONSTRAINT chk_goods_receipts_paid_amount
        CHECK (paid_amount >= 0 AND paid_amount <= total_amount),
    ADD CONSTRAINT chk_goods_receipts_status
        CHECK (status IN ('POSTED', 'CANCELLED')),
    ADD CONSTRAINT chk_goods_receipts_cancellation_metadata
        CHECK (
            (status = 'POSTED'
                AND cancelled_at IS NULL
                AND cancelled_by IS NULL
                AND cancellation_reason IS NULL)
            OR
            (status = 'CANCELLED'
                AND cancelled_at IS NOT NULL
                AND cancelled_by IS NOT NULL
                AND cancellation_reason IS NOT NULL
                AND BTRIM(cancellation_reason) <> '')
        );

ALTER TABLE goods_receipt_items
    ADD COLUMN base_unit_of_measure VARCHAR(30),
    ADD COLUMN line_total NUMERIC(19,4);

UPDATE goods_receipt_items receipt_line
SET base_unit_of_measure = item.base_unit_of_measure,
    line_total = ROUND(receipt_line.quantity * receipt_line.purchase_price, 4)
FROM items item
WHERE receipt_line.item_id = item.id;

ALTER TABLE goods_receipt_items
    ALTER COLUMN base_unit_of_measure SET NOT NULL,
    ALTER COLUMN line_total SET NOT NULL,
    ADD CONSTRAINT chk_goods_receipt_items_purchase_price_positive
        CHECK (purchase_price > 0),
    ADD CONSTRAINT chk_goods_receipt_items_line_total_positive
        CHECK (line_total > 0),
    ADD CONSTRAINT chk_goods_receipt_items_line_total_calculation
        CHECK (line_total = ROUND(quantity * purchase_price, 4));

CREATE INDEX idx_goods_receipts_history_order
    ON goods_receipts (created_at DESC, id DESC);

CREATE UNIQUE INDEX uq_stock_movements_goods_receipt_item_location
    ON stock_movements (source_id, product_id, stock_location)
    WHERE source_type = 'GOODS_RECEIPT';

CREATE UNIQUE INDEX uq_stock_movements_goods_receipt_cancel_item_location
    ON stock_movements (source_id, product_id, stock_location)
    WHERE source_type = 'GOODS_RECEIPT_CANCELLATION';

COMMENT ON COLUMN goods_receipts.supplier_name IS
    'Immutable supplier name snapshot captured when the receipt is posted';
COMMENT ON COLUMN goods_receipts.total_amount IS
    'Server-calculated sum of rounded goods receipt line totals';
COMMENT ON COLUMN goods_receipt_items.base_unit_of_measure IS
    'Item base UOM snapshot; Release 1 performs no package conversion';
COMMENT ON COLUMN goods_receipt_items.purchase_price IS
    'Purchase price snapshot copied from the request line';
COMMENT ON COLUMN goods_receipt_items.line_total IS
    'ROUND(quantity * purchase_price, 4) using the application HALF_UP boundary';
