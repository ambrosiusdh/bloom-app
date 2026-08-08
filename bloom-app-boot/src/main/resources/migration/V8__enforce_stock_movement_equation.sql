ALTER TABLE stock_movements
    ADD CONSTRAINT chk_stock_movements_location
        CHECK (stock_location IN ('STORE', 'WAREHOUSE')),
    ADD CONSTRAINT chk_stock_movements_direction
        CHECK (movement_type IN ('IN', 'OUT')),
    ADD CONSTRAINT chk_stock_movements_balance_equation
        CHECK (
            (movement_type = 'IN' AND qty_after = qty_before + quantity)
            OR
            (movement_type = 'OUT' AND qty_after = qty_before - quantity)
        );
