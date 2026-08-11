ALTER TABLE stock_movements
    ADD COLUMN adjustment_action_type VARCHAR(50);

-- Preserve legacy audit IDs without changing either historical table.
CREATE TABLE stock_movement_legacy_audit_links (
    stock_movement_id BIGINT PRIMARY KEY
        REFERENCES stock_movements(id),
    item_audit_log_id BIGINT NOT NULL UNIQUE
        REFERENCES item_audit_logs(id)
);

WITH movement_ranked AS (
    SELECT
        sm.id,
        sm.product_id,
        sm.quantity,
        sm.qty_before,
        sm.qty_after,
        sm.source_type,
        ROW_NUMBER() OVER (
            PARTITION BY sm.product_id, sm.quantity, sm.qty_before, sm.qty_after, sm.source_type
            ORDER BY sm.created_at, sm.id
        ) AS occurrence
    FROM stock_movements sm
),
audit_ranked AS (
    SELECT
        al.id,
        al.item_id,
        al.qty,
        al.qty_before,
        al.qty_after,
        al.source,
        ROW_NUMBER() OVER (
            PARTITION BY al.item_id, al.qty, al.qty_before, al.qty_after, al.source
            ORDER BY al.created_date, al.id
        ) AS occurrence
    FROM item_audit_logs al
)
INSERT INTO stock_movement_legacy_audit_links (stock_movement_id, item_audit_log_id)
SELECT mr.id, ar.id
FROM movement_ranked mr
JOIN audit_ranked ar
  ON ar.item_id = mr.product_id
 AND ar.qty = mr.quantity
 AND ar.qty_before = mr.qty_before
 AND ar.qty_after = mr.qty_after
 AND ar.source = mr.source_type
 AND ar.occurrence = mr.occurrence;

DO $$
DECLARE
    movement_count BIGINT;
    audit_count BIGINT;
    link_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO movement_count FROM stock_movements;
    SELECT COUNT(*) INTO audit_count FROM item_audit_logs;
    SELECT COUNT(*) INTO link_count FROM stock_movement_legacy_audit_links;

    IF movement_count <> link_count OR audit_count <> link_count THEN
        RAISE EXCEPTION
            'Cannot cut audit reads over to StockMovement: historical ledger/audit coverage is not one-to-one';
    END IF;
END
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM stock_movement_legacy_audit_links link
        JOIN stock_movements sm ON sm.id = link.stock_movement_id
        JOIN item_audit_logs al ON al.id = link.item_audit_log_id
        WHERE al.reference_no IS DISTINCT FROM
            CASE sm.source_type
                WHEN 'OPENING_BALANCE' THEN (
                    SELECT i.sku FROM items i WHERE i.id = sm.product_id)
                WHEN 'SALE' THEN (
                    SELECT s.code FROM sales s WHERE s.id = sm.source_id)
                WHEN 'STOCK_ADJUSTMENT' THEN (
                    SELECT sa.stock_adjustment_code
                    FROM stock_adjustments sa WHERE sa.id = sm.source_id)
                WHEN 'GOODS_RECEIPT' THEN (
                    SELECT gr.code FROM goods_receipts gr WHERE gr.id = sm.source_id)
                WHEN 'TRANSFER' THEN (
                    SELECT st.code FROM stock_transfers st WHERE st.id = sm.source_id)
                ELSE NULL
            END
    ) THEN
        RAISE EXCEPTION
            'Cannot cut audit reads over to StockMovement: a historical reference cannot be reconstructed';
    END IF;
END
$$;

CREATE INDEX idx_stock_movements_history_order
    ON stock_movements (created_at DESC, id DESC);

CREATE INDEX idx_stock_movements_product_history
    ON stock_movements (product_id, created_at DESC, id DESC);
