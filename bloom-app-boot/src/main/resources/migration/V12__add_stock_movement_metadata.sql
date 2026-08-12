ALTER TABLE stock_movements
    ADD COLUMN reference_no VARCHAR(100) NOT NULL,
    ADD COLUMN adjustment_action_type VARCHAR(50),
    ADD CONSTRAINT chk_stock_movements_adjustment_action
        CHECK (
            (source_type = 'STOCK_ADJUSTMENT'
                AND adjustment_action_type IN ('ADD', 'REMOVE', 'CORRECTION'))
            OR
            (source_type <> 'STOCK_ADJUSTMENT'
                AND adjustment_action_type IS NULL)
        );

CREATE INDEX idx_stock_movements_history_order
    ON stock_movements (created_at DESC, id DESC);

CREATE INDEX idx_stock_movements_product_history
    ON stock_movements (product_id, created_at DESC, id DESC);
