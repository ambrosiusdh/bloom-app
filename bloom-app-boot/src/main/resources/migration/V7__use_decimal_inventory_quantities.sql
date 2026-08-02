-- Convert every persisted physical quantity from integer to the Release 1
-- NUMERIC(19,4) contract. PostgreSQL's cast preserves each integer exactly as x.0000.
ALTER TABLE items
    ALTER COLUMN stock_quantity TYPE NUMERIC(19,4)
        USING stock_quantity::NUMERIC(19,4),
    ALTER COLUMN stock_store TYPE NUMERIC(19,4)
        USING stock_store::NUMERIC(19,4),
    ALTER COLUMN stock_store SET DEFAULT 0.0000,
    ALTER COLUMN stock_warehouse TYPE NUMERIC(19,4)
        USING stock_warehouse::NUMERIC(19,4),
    ALTER COLUMN stock_warehouse SET DEFAULT 0.0000,
    ADD CONSTRAINT chk_items_stock_quantity_nonnegative
        CHECK (stock_quantity IS NULL OR stock_quantity >= 0),
    ADD CONSTRAINT chk_items_stock_store_nonnegative CHECK (stock_store >= 0),
    ADD CONSTRAINT chk_items_stock_warehouse_nonnegative CHECK (stock_warehouse >= 0);

ALTER TABLE sale_items
    ALTER COLUMN quantity TYPE NUMERIC(19,4)
        USING quantity::NUMERIC(19,4),
    ADD CONSTRAINT chk_sale_items_quantity_positive CHECK (quantity > 0);

ALTER TABLE goods_receipt_items
    ALTER COLUMN quantity TYPE NUMERIC(19,4)
        USING quantity::NUMERIC(19,4),
    ADD CONSTRAINT chk_goods_receipt_items_quantity_positive CHECK (quantity > 0);

ALTER TABLE stock_adjustment_items
    ALTER COLUMN change_quantity TYPE NUMERIC(19,4)
        USING change_quantity::NUMERIC(19,4),
    ALTER COLUMN previous_stock TYPE NUMERIC(19,4)
        USING previous_stock::NUMERIC(19,4),
    ALTER COLUMN new_stock TYPE NUMERIC(19,4)
        USING new_stock::NUMERIC(19,4),
    ADD CONSTRAINT chk_stock_adjustment_previous_stock_nonnegative CHECK (previous_stock >= 0),
    ADD CONSTRAINT chk_stock_adjustment_new_stock_nonnegative CHECK (new_stock >= 0);

-- A correction stores a signed delta in change_quantity, so that column cannot
-- have a nonnegative check without rejecting valid correction history.
ALTER TABLE stock_movements
    ALTER COLUMN quantity TYPE NUMERIC(19,4)
        USING quantity::NUMERIC(19,4),
    ALTER COLUMN qty_before TYPE NUMERIC(19,4)
        USING qty_before::NUMERIC(19,4),
    ALTER COLUMN qty_after TYPE NUMERIC(19,4)
        USING qty_after::NUMERIC(19,4),
    ADD CONSTRAINT chk_stock_movements_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT chk_stock_movements_qty_before_nonnegative CHECK (qty_before >= 0),
    ADD CONSTRAINT chk_stock_movements_qty_after_nonnegative CHECK (qty_after >= 0);

ALTER TABLE item_audit_logs
    ALTER COLUMN qty TYPE NUMERIC(19,4)
        USING qty::NUMERIC(19,4),
    ALTER COLUMN qty_before TYPE NUMERIC(19,4)
        USING qty_before::NUMERIC(19,4),
    ALTER COLUMN qty_after TYPE NUMERIC(19,4)
        USING qty_after::NUMERIC(19,4),
    ADD CONSTRAINT chk_item_audit_logs_qty_positive CHECK (qty > 0),
    ADD CONSTRAINT chk_item_audit_logs_qty_before_nonnegative CHECK (qty_before >= 0),
    ADD CONSTRAINT chk_item_audit_logs_qty_after_nonnegative CHECK (qty_after >= 0);
