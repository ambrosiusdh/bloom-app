ALTER TABLE stock_transfer_lines
    ADD COLUMN item_name VARCHAR(255);

UPDATE stock_transfer_lines transfer_line
SET item_name = COALESCE(NULLIF(BTRIM(item.name), ''), transfer_line.item_sku)
FROM items item
WHERE item.id = transfer_line.item_id;

ALTER TABLE stock_transfer_lines
    ALTER COLUMN item_name SET NOT NULL;

DROP INDEX IF EXISTS idx_stock_transfers_created_at;
CREATE INDEX idx_stock_transfers_history_order
    ON stock_transfers (created_at DESC, id DESC);

DROP INDEX IF EXISTS idx_stock_transfer_lines_item_id;
CREATE INDEX idx_stock_transfer_lines_item_transfer
    ON stock_transfer_lines (item_id, stock_transfer_id);

COMMENT ON COLUMN stock_transfer_lines.item_name IS
    'Immutable item-name snapshot used for user-friendly historical display';
