CREATE UNIQUE INDEX uq_stock_movements_sale_item_location
    ON stock_movements (source_id, product_id, stock_location)
    WHERE source_type = 'SALE';

COMMENT ON COLUMN stock_adjustment_items.change_quantity IS
    'ADD/REMOVE movement magnitude; CORRECTION absolute target balance';
