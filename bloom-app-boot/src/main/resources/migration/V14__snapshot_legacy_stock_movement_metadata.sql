ALTER TABLE stock_movement_legacy_audit_links
    ADD COLUMN reference_no VARCHAR(100),
    ADD COLUMN adjustment_action_type VARCHAR(50),
    ADD CONSTRAINT chk_legacy_adjustment_action_type
        CHECK (adjustment_action_type IS NULL
            OR adjustment_action_type IN ('ADD', 'REMOVE', 'CORRECTION'));

-- Reconcile only the pre-cutover movement/audit sets captured by V13. Movements
-- written after the cutover intentionally have no legacy audit counterpart.
CREATE TEMPORARY TABLE stock_movement_reconciliation_candidates
ON COMMIT DROP AS
SELECT
    sm.id AS stock_movement_id,
    al.id AS item_audit_log_id,
    al.reference_no
FROM stock_movement_legacy_audit_links movement_scope
JOIN stock_movements sm
  ON sm.id = movement_scope.stock_movement_id
JOIN item_audit_logs al
  ON al.item_id = sm.product_id
 AND al.qty = sm.quantity
 AND al.qty_before = sm.qty_before
 AND al.qty_after = sm.qty_after
 AND al.source = sm.source_type
JOIN stock_movement_legacy_audit_links audit_scope
  ON audit_scope.item_audit_log_id = al.id
CROSS JOIN LATERAL (
    SELECT CASE sm.source_type
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
    END AS reference_no
) reconstructed
WHERE al.reference_no IS NOT DISTINCT FROM reconstructed.reference_no;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM stock_movement_legacy_audit_links scope
        LEFT JOIN stock_movement_reconciliation_candidates candidate
          ON candidate.stock_movement_id = scope.stock_movement_id
        GROUP BY scope.stock_movement_id
        HAVING COUNT(candidate.item_audit_log_id) <> 1
    ) OR EXISTS (
        SELECT 1
        FROM stock_movement_legacy_audit_links scope
        LEFT JOIN stock_movement_reconciliation_candidates candidate
          ON candidate.item_audit_log_id = scope.item_audit_log_id
        GROUP BY scope.item_audit_log_id
        HAVING COUNT(candidate.stock_movement_id) <> 1
    ) THEN
        RAISE EXCEPTION
            'Cannot harden legacy StockMovement history: ledger/audit match is missing or ambiguous';
    END IF;
END
$$;

CREATE TEMPORARY TABLE stock_movement_adjustment_candidates
ON COMMIT DROP AS
SELECT
    sm.id AS stock_movement_id,
    sai.id AS stock_adjustment_item_id,
    sai.action_type
FROM stock_movement_legacy_audit_links scope
JOIN stock_movements sm
  ON sm.id = scope.stock_movement_id
JOIN stock_adjustment_items sai
  ON sai.stock_adjustment_id = sm.source_id
 AND sai.item_id = sm.product_id
 AND sai.stock_location = sm.stock_location
 AND sai.previous_stock = sm.qty_before
 AND sai.new_stock = sm.qty_after
WHERE sm.source_type = 'STOCK_ADJUSTMENT'
  AND (
      (sai.action_type = 'ADD'
          AND sm.movement_type = 'IN'
          AND sai.change_quantity = sm.quantity)
      OR (sai.action_type = 'REMOVE'
          AND sm.movement_type = 'OUT'
          AND sai.change_quantity = sm.quantity)
      OR (sai.action_type = 'CORRECTION'
          AND sai.change_quantity = sai.new_stock
          AND ABS(sai.new_stock - sai.previous_stock) = sm.quantity
          AND sm.movement_type = CASE
              WHEN sai.new_stock >= sai.previous_stock THEN 'IN'
              ELSE 'OUT'
          END)
  );

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM stock_movement_legacy_audit_links scope
        JOIN stock_movements sm
          ON sm.id = scope.stock_movement_id
        LEFT JOIN stock_movement_adjustment_candidates candidate
          ON candidate.stock_movement_id = sm.id
        WHERE sm.source_type = 'STOCK_ADJUSTMENT'
        GROUP BY sm.id
        HAVING COUNT(candidate.stock_adjustment_item_id) <> 1
    ) THEN
        RAISE EXCEPTION
            'Cannot harden legacy StockMovement history: adjustment action is missing or ambiguous';
    END IF;
END
$$;

DELETE FROM stock_movement_legacy_audit_links;

INSERT INTO stock_movement_legacy_audit_links (
    stock_movement_id,
    item_audit_log_id,
    reference_no,
    adjustment_action_type
)
SELECT
    candidate.stock_movement_id,
    candidate.item_audit_log_id,
    candidate.reference_no,
    adjustment.action_type
FROM stock_movement_reconciliation_candidates candidate
LEFT JOIN stock_movement_adjustment_candidates adjustment
  ON adjustment.stock_movement_id = candidate.stock_movement_id;
