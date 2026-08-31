-- Development-only Release 1 demonstration data for frontend testing.
-- Run manually after Flyway has migrated the database through V12.
-- This script is intentionally outside the production Flyway location.
-- Demo login: kasir.demo / password
-- The transaction-level guard makes accidental retries fail before any data changes.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users WHERE username = 'kasir.demo')
        OR EXISTS (SELECT 1 FROM items WHERE sku LIKE 'DEMO-%')
        OR EXISTS (SELECT 1 FROM sales WHERE code LIKE 'SALE/DEMO/%')
        OR EXISTS (SELECT 1 FROM goods_receipts WHERE code LIKE 'GR/DEMO/%')
        OR EXISTS (SELECT 1 FROM stock_transfers WHERE code LIKE 'ST/DEMO/%') THEN
        RAISE EXCEPTION
            'Release 1 demo data already exists; refusing to create duplicate business history';
    END IF;
END
$$;

INSERT INTO users (
    username, password, role, name,
    created_at, updated_at, created_by, updated_by
)
VALUES (
    'kasir.demo',
    '$2y$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.',
    'cashier', 'Kasir Demo',
    CURRENT_TIMESTAMP - INTERVAL '30 days',
    CURRENT_TIMESTAMP - INTERVAL '30 days',
    'SYSTEM', 'SYSTEM'
);

INSERT INTO items (
    name, sku, description, price, stock_store, stock_warehouse,
    base_unit_of_measure, fractional_quantity_allowed,
    active, item_category_id,
    created_at, updated_at, created_by, updated_by, version
)
VALUES
    (
        'Semen Portland 50kg', 'DEMO-BB-001', 'Semen untuk konstruksi umum',
        75000.0000, 42.0000, 15.0000,
        'PIECE', FALSE, TRUE,
        (SELECT id FROM item_categories WHERE code = 'BB'),
        CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '2 hours',
        'admin', 'admin', 0
    ),
    (
        'Cat Tembok Putih', 'DEMO-BB-002', 'Cat tembok yang dijual per liter',
        120000.0000, 19.2500, 19.7500,
        'LITER', TRUE, TRUE,
        (SELECT id FROM item_categories WHERE code = 'BB'),
        CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '2 hours',
        'admin', 'admin', 0
    ),
    (
        'Paku 5cm Curah', 'DEMO-PRT-001', 'Paku curah yang dijual per kilogram',
        18000.0000, 103.0000, 0.0000,
        'KILOGRAM', TRUE, TRUE,
        (SELECT id FROM item_categories WHERE code = 'PRT'),
        CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '4 hours',
        'admin', 'admin', 0
    ),
    (
        'Palu Besi', 'DEMO-PRT-002', 'Palu besi untuk pekerjaan rumah',
        85000.0000, 6.0000, 0.0000,
        'PIECE', FALSE, TRUE,
        (SELECT id FROM item_categories WHERE code = 'PRT'),
        CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '4 hours',
        'admin', 'admin', 0
    ),
    (
        'Kabel NYM 2x1.5', 'DEMO-LNN-001', 'Kabel listrik yang dijual per meter',
        12000.0000, 32.7500, 35.0000,
        'METER', TRUE, TRUE,
        (SELECT id FROM item_categories WHERE code = 'LNN'),
        CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '1 day',
        'admin', 'admin', 0
    ),
    (
        'Ember Bangunan', 'DEMO-LNN-002', 'Contoh item stok rendah',
        35000.0000, 3.0000, 0.0000,
        'PIECE', FALSE, TRUE,
        (SELECT id FROM item_categories WHERE code = 'LNN'),
        CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days',
        'admin', 'admin', 0
    );

INSERT INTO suppliers (
    name, code, contact_number, address,
    active, created_at, updated_at, created_by, updated_by, version
)
VALUES
    (
        'PT Sumber Bangunan Demo', 'SUP-DEMO-001', '021-555-0101',
        'Jl. Industri No. 10, Jakarta', TRUE,
        CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days',
        'admin', 'admin', 0
    ),
    (
        'CV Perkakas Keluarga', 'SUP-DEMO-002', '021-555-0102',
        'Jl. Pasar Baru No. 25, Jakarta', TRUE,
        CURRENT_TIMESTAMP - INTERVAL '30 days', CURRENT_TIMESTAMP - INTERVAL '30 days',
        'admin', 'admin', 0
    );

INSERT INTO goods_receipts (
    code, received_date, supplier_name_snapshot, supplier_id,
    total_amount, description, created_at, created_by,
    create_idempotency_key, create_request_hash, status, version
)
VALUES
    (
        'GR/DEMO/0001', CURRENT_TIMESTAMP - INTERVAL '12 days',
        'CV Perkakas Keluarga',
        (SELECT id FROM suppliers WHERE code = 'SUP-DEMO-002'),
        1606000.0000,
        'Penerimaan stok paku dan kabel demo',
        CURRENT_TIMESTAMP - INTERVAL '12 days', 'admin',
        'demo-goods-receipt-0001', REPEAT('1', 64), 'POSTED', 0
    ),
    (
        'GR/DEMO/0002', CURRENT_TIMESTAMP - INTERVAL '9 days',
        'PT Sumber Bangunan Demo',
        (SELECT id FROM suppliers WHERE code = 'SUP-DEMO-001'),
        3771250.0000,
        'Penerimaan stok gudang semen dan cat demo',
        CURRENT_TIMESTAMP - INTERVAL '9 days', 'admin',
        'demo-goods-receipt-0002', REPEAT('2', 64), 'POSTED', 0
    );

INSERT INTO goods_receipt_items (
    goods_receipt_id, item_id, quantity, purchase_price, stock_location,
    base_unit_of_measure, line_total
)
VALUES
    (
        (SELECT id FROM goods_receipts WHERE code = 'GR/DEMO/0001'),
        (SELECT id FROM items WHERE sku = 'DEMO-PRT-001'),
        100.5000, 12000.0000, 'STORE', 'KILOGRAM', 1206000.0000
    ),
    (
        (SELECT id FROM goods_receipts WHERE code = 'GR/DEMO/0001'),
        (SELECT id FROM items WHERE sku = 'DEMO-LNN-001'),
        50.0000, 8000.0000, 'WAREHOUSE', 'METER', 400000.0000
    ),
    (
        (SELECT id FROM goods_receipts WHERE code = 'GR/DEMO/0002'),
        (SELECT id FROM items WHERE sku = 'DEMO-BB-001'),
        20.0000, 60000.0000, 'WAREHOUSE', 'PIECE', 1200000.0000
    ),
    (
        (SELECT id FROM goods_receipts WHERE code = 'GR/DEMO/0002'),
        (SELECT id FROM items WHERE sku = 'DEMO-BB-002'),
        30.2500, 85000.0000, 'WAREHOUSE', 'LITER', 2571250.0000
    );

INSERT INTO supplier_payments (
    goods_receipt_id, supplier_id, cash_session_id, amount, payment_method,
    paid_at, reference, note, actor, is_voided,
    idempotency_key, request_hash, created_at, version
)
VALUES
    (
        (SELECT id FROM goods_receipts WHERE code = 'GR/DEMO/0001'),
        (SELECT id FROM suppliers WHERE code = 'SUP-DEMO-002'),
        NULL, 1000000.0000, 'BANK_TRANSFER',
        CURRENT_TIMESTAMP - INTERVAL '11 days', 'DEMO-BANK-0001',
        'Pembayaran sebagian demo', 'admin', FALSE,
        'demo-supplier-payment-0001', REPEAT('3', 64),
        CURRENT_TIMESTAMP - INTERVAL '11 days', 0
    ),
    (
        (SELECT id FROM goods_receipts WHERE code = 'GR/DEMO/0002'),
        (SELECT id FROM suppliers WHERE code = 'SUP-DEMO-001'),
        NULL, 3771250.0000, 'QRIS',
        CURRENT_TIMESTAMP - INTERVAL '8 days', 'DEMO-QRIS-0002',
        'Pelunasan demo', 'admin', FALSE,
        'demo-supplier-payment-0002', REPEAT('4', 64),
        CURRENT_TIMESTAMP - INTERVAL '8 days', 0
    );

INSERT INTO stock_transfers (
    code, request_key, request_hash,
    source_location, destination_location,
    description, created_by, created_at
)
VALUES
    (
        'ST/DEMO/0001', 'demo-transfer-request-0001', REPEAT('1', 64),
        'WAREHOUSE', 'STORE', 'Pengisian stok toko dari gudang',
        'admin', CURRENT_TIMESTAMP - INTERVAL '6 days'
    ),
    (
        'ST/DEMO/0002', 'demo-transfer-request-0002', REPEAT('2', 64),
        'WAREHOUSE', 'STORE', 'Pengisian stok kabel di toko',
        'admin', CURRENT_TIMESTAMP - INTERVAL '4 days'
    );

INSERT INTO stock_transfer_lines (
    stock_transfer_id, item_id, item_sku, item_name, unit_of_measure, quantity
)
VALUES
    (
        (SELECT id FROM stock_transfers WHERE code = 'ST/DEMO/0001'),
        (SELECT id FROM items WHERE sku = 'DEMO-BB-001'),
        'DEMO-BB-001', 'Semen Portland 50kg', 'PIECE', 5.0000
    ),
    (
        (SELECT id FROM stock_transfers WHERE code = 'ST/DEMO/0001'),
        (SELECT id FROM items WHERE sku = 'DEMO-BB-002'),
        'DEMO-BB-002', 'Cat Tembok Putih', 'LITER', 10.5000
    ),
    (
        (SELECT id FROM stock_transfers WHERE code = 'ST/DEMO/0002'),
        (SELECT id FROM items WHERE sku = 'DEMO-LNN-001'),
        'DEMO-LNN-001', 'Kabel NYM 2x1.5', 'METER', 12.2500
    );

INSERT INTO stock_adjustments (
    stock_adjustment_code, reason, created_by, created_at
)
VALUES (
    'SA/DEMO/0001', 'Contoh tambah, kurang, dan koreksi stok',
    'admin', CURRENT_TIMESTAMP - INTERVAL '1 day'
);

INSERT INTO stock_adjustment_items (
    stock_adjustment_id, item_id, action_type,
    change_quantity, previous_stock, new_stock, stock_location
)
VALUES
    (
        (SELECT id FROM stock_adjustments WHERE stock_adjustment_code = 'SA/DEMO/0001'),
        (SELECT id FROM items WHERE sku = 'DEMO-PRT-001'),
        'ADD', 10.2500, 95.0000, 105.2500, 'STORE'
    ),
    (
        (SELECT id FROM stock_adjustments WHERE stock_adjustment_code = 'SA/DEMO/0001'),
        (SELECT id FROM items WHERE sku = 'DEMO-PRT-002'),
        'REMOVE', 1.0000, 8.0000, 7.0000, 'STORE'
    ),
    (
        (SELECT id FROM stock_adjustments WHERE stock_adjustment_code = 'SA/DEMO/0001'),
        (SELECT id FROM items WHERE sku = 'DEMO-LNN-001'),
        'CORRECTION', 35.0000, 37.7500, 35.0000, 'WAREHOUSE'
    );

INSERT INTO sales (
    code, subtotal_amount, discount_amount, total_amount, paid_amount,
    description, payment_type,
    created_at, updated_at, created_by, updated_by
)
VALUES
    (
        'SALE/DEMO/0001', 249000.0000, 9000.0000, 240000.0000, 240000.0000,
        'Penjualan tunai demo', 'CASH',
        CURRENT_TIMESTAMP - INTERVAL '5 days', CURRENT_TIMESTAMP - INTERVAL '5 days',
        'kasir.demo', 'kasir.demo'
    ),
    (
        'SALE/DEMO/0002', 204000.0000, 4000.0000, 200000.0000, 200000.0000,
        'Penjualan QRIS demo', 'QRIS',
        CURRENT_TIMESTAMP - INTERVAL '3 days', CURRENT_TIMESTAMP - INTERVAL '3 days',
        'kasir.demo', 'kasir.demo'
    ),
    (
        'SALE/DEMO/0003', 125500.0000, 500.0000, 125000.0000, 125000.0000,
        'Penjualan hari ini', 'CASH',
        CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours',
        'admin', 'admin'
    ),
    (
        'SALE/DEMO/0004', 375000.0000, 0.0000, 375000.0000, 375000.0000,
        'Penjualan terbaru untuk dashboard', 'QRIS',
        CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours',
        'admin', 'admin'
    );

INSERT INTO sale_items (
    sale_id, item_id, quantity, unit_price, subtotal, stock_location
)
VALUES
    (
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0001'),
        (SELECT id FROM items WHERE sku = 'DEMO-BB-001'),
        2.0000, 75000.0000, 150000.0000, 'STORE'
    ),
    (
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0001'),
        (SELECT id FROM items WHERE sku = 'DEMO-PRT-001'),
        5.5000, 18000.0000, 99000.0000, 'STORE'
    ),
    (
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0002'),
        (SELECT id FROM items WHERE sku = 'DEMO-BB-002'),
        1.2500, 120000.0000, 150000.0000, 'STORE'
    ),
    (
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0002'),
        (SELECT id FROM items WHERE sku = 'DEMO-LNN-001'),
        4.5000, 12000.0000, 54000.0000, 'STORE'
    ),
    (
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0003'),
        (SELECT id FROM items WHERE sku = 'DEMO-PRT-002'),
        1.0000, 85000.0000, 85000.0000, 'STORE'
    ),
    (
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0003'),
        (SELECT id FROM items WHERE sku = 'DEMO-PRT-001'),
        2.2500, 18000.0000, 40500.0000, 'STORE'
    ),
    (
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0004'),
        (SELECT id FROM items WHERE sku = 'DEMO-BB-001'),
        1.0000, 75000.0000, 75000.0000, 'STORE'
    ),
    (
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0004'),
        (SELECT id FROM items WHERE sku = 'DEMO-BB-002'),
        2.5000, 120000.0000, 300000.0000, 'STORE'
    );

-- Opening balances for demo items.
INSERT INTO stock_movements (
    product_id, movement_type, source_type, source_id, reference_no,
    quantity, stock_location, qty_before, qty_after,
    adjustment_action_type, created_at, created_by
)
VALUES
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-001'), 'IN', 'OPENING_BALANCE',
        (SELECT id FROM items WHERE sku = 'DEMO-BB-001'), 'DEMO-BB-001',
        40.0000, 'STORE', 0.0000, 40.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '30 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-002'), 'IN', 'OPENING_BALANCE',
        (SELECT id FROM items WHERE sku = 'DEMO-BB-002'), 'DEMO-BB-002',
        12.5000, 'STORE', 0.0000, 12.5000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '30 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-PRT-002'), 'IN', 'OPENING_BALANCE',
        (SELECT id FROM items WHERE sku = 'DEMO-PRT-002'), 'DEMO-PRT-002',
        8.0000, 'STORE', 0.0000, 8.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '30 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-LNN-001'), 'IN', 'OPENING_BALANCE',
        (SELECT id FROM items WHERE sku = 'DEMO-LNN-001'), 'DEMO-LNN-001',
        25.0000, 'STORE', 0.0000, 25.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '30 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-LNN-002'), 'IN', 'OPENING_BALANCE',
        (SELECT id FROM items WHERE sku = 'DEMO-LNN-002'), 'DEMO-LNN-002',
        3.0000, 'STORE', 0.0000, 3.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '30 days', 'admin');

-- Goods receipt movements.
INSERT INTO stock_movements (
    product_id, movement_type, source_type, source_id, reference_no,
    quantity, stock_location, qty_before, qty_after,
    adjustment_action_type, created_at, created_by
)
VALUES
    ((SELECT id FROM items WHERE sku = 'DEMO-PRT-001'), 'IN', 'GOODS_RECEIPT',
        (SELECT id FROM goods_receipts WHERE code = 'GR/DEMO/0001'), 'GR/DEMO/0001',
        100.5000, 'STORE', 0.0000, 100.5000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '12 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-LNN-001'), 'IN', 'GOODS_RECEIPT',
        (SELECT id FROM goods_receipts WHERE code = 'GR/DEMO/0001'), 'GR/DEMO/0001',
        50.0000, 'WAREHOUSE', 0.0000, 50.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '12 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-001'), 'IN', 'GOODS_RECEIPT',
        (SELECT id FROM goods_receipts WHERE code = 'GR/DEMO/0002'), 'GR/DEMO/0002',
        20.0000, 'WAREHOUSE', 0.0000, 20.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '9 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-002'), 'IN', 'GOODS_RECEIPT',
        (SELECT id FROM goods_receipts WHERE code = 'GR/DEMO/0002'), 'GR/DEMO/0002',
        30.2500, 'WAREHOUSE', 0.0000, 30.2500, NULL,
        CURRENT_TIMESTAMP - INTERVAL '9 days', 'admin');

-- Paired transfer movements.
INSERT INTO stock_movements (
    product_id, movement_type, source_type, source_id, reference_no,
    quantity, stock_location, qty_before, qty_after,
    adjustment_action_type, created_at, created_by
)
VALUES
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-001'), 'OUT', 'TRANSFER',
        (SELECT id FROM stock_transfers WHERE code = 'ST/DEMO/0001'), 'ST/DEMO/0001',
        5.0000, 'WAREHOUSE', 20.0000, 15.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '6 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-001'), 'IN', 'TRANSFER',
        (SELECT id FROM stock_transfers WHERE code = 'ST/DEMO/0001'), 'ST/DEMO/0001',
        5.0000, 'STORE', 40.0000, 45.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '6 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-002'), 'OUT', 'TRANSFER',
        (SELECT id FROM stock_transfers WHERE code = 'ST/DEMO/0001'), 'ST/DEMO/0001',
        10.5000, 'WAREHOUSE', 30.2500, 19.7500, NULL,
        CURRENT_TIMESTAMP - INTERVAL '6 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-002'), 'IN', 'TRANSFER',
        (SELECT id FROM stock_transfers WHERE code = 'ST/DEMO/0001'), 'ST/DEMO/0001',
        10.5000, 'STORE', 12.5000, 23.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '6 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-LNN-001'), 'OUT', 'TRANSFER',
        (SELECT id FROM stock_transfers WHERE code = 'ST/DEMO/0002'), 'ST/DEMO/0002',
        12.2500, 'WAREHOUSE', 50.0000, 37.7500, NULL,
        CURRENT_TIMESTAMP - INTERVAL '4 days', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-LNN-001'), 'IN', 'TRANSFER',
        (SELECT id FROM stock_transfers WHERE code = 'ST/DEMO/0002'), 'ST/DEMO/0002',
        12.2500, 'STORE', 25.0000, 37.2500, NULL,
        CURRENT_TIMESTAMP - INTERVAL '4 days', 'admin');

-- Sale movements, ordered chronologically.
INSERT INTO stock_movements (
    product_id, movement_type, source_type, source_id, reference_no,
    quantity, stock_location, qty_before, qty_after,
    adjustment_action_type, created_at, created_by
)
VALUES
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-001'), 'OUT', 'SALE',
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0001'), 'SALE/DEMO/0001',
        2.0000, 'STORE', 45.0000, 43.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '5 days', 'kasir.demo'),
    ((SELECT id FROM items WHERE sku = 'DEMO-PRT-001'), 'OUT', 'SALE',
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0001'), 'SALE/DEMO/0001',
        5.5000, 'STORE', 100.5000, 95.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '5 days', 'kasir.demo'),
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-002'), 'OUT', 'SALE',
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0002'), 'SALE/DEMO/0002',
        1.2500, 'STORE', 23.0000, 21.7500, NULL,
        CURRENT_TIMESTAMP - INTERVAL '3 days', 'kasir.demo'),
    ((SELECT id FROM items WHERE sku = 'DEMO-LNN-001'), 'OUT', 'SALE',
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0002'), 'SALE/DEMO/0002',
        4.5000, 'STORE', 37.2500, 32.7500, NULL,
        CURRENT_TIMESTAMP - INTERVAL '3 days', 'kasir.demo');

-- Adjustment movements.
INSERT INTO stock_movements (
    product_id, movement_type, source_type, source_id, reference_no,
    quantity, stock_location, qty_before, qty_after,
    adjustment_action_type, created_at, created_by
)
VALUES
    ((SELECT id FROM items WHERE sku = 'DEMO-PRT-001'), 'IN', 'STOCK_ADJUSTMENT',
        (SELECT id FROM stock_adjustments WHERE stock_adjustment_code = 'SA/DEMO/0001'),
        'SA/DEMO/0001', 10.2500, 'STORE', 95.0000, 105.2500, 'ADD',
        CURRENT_TIMESTAMP - INTERVAL '1 day', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-PRT-002'), 'OUT', 'STOCK_ADJUSTMENT',
        (SELECT id FROM stock_adjustments WHERE stock_adjustment_code = 'SA/DEMO/0001'),
        'SA/DEMO/0001', 1.0000, 'STORE', 8.0000, 7.0000, 'REMOVE',
        CURRENT_TIMESTAMP - INTERVAL '1 day', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-LNN-001'), 'OUT', 'STOCK_ADJUSTMENT',
        (SELECT id FROM stock_adjustments WHERE stock_adjustment_code = 'SA/DEMO/0001'),
        'SA/DEMO/0001', 2.7500, 'WAREHOUSE', 37.7500, 35.0000, 'CORRECTION',
        CURRENT_TIMESTAMP - INTERVAL '1 day', 'admin');

-- Today's sale movements populate dashboard summaries and recent activity.
INSERT INTO stock_movements (
    product_id, movement_type, source_type, source_id, reference_no,
    quantity, stock_location, qty_before, qty_after,
    adjustment_action_type, created_at, created_by
)
VALUES
    ((SELECT id FROM items WHERE sku = 'DEMO-PRT-002'), 'OUT', 'SALE',
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0003'), 'SALE/DEMO/0003',
        1.0000, 'STORE', 7.0000, 6.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '4 hours', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-PRT-001'), 'OUT', 'SALE',
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0003'), 'SALE/DEMO/0003',
        2.2500, 'STORE', 105.2500, 103.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '4 hours', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-001'), 'OUT', 'SALE',
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0004'), 'SALE/DEMO/0004',
        1.0000, 'STORE', 43.0000, 42.0000, NULL,
        CURRENT_TIMESTAMP - INTERVAL '2 hours', 'admin'),
    ((SELECT id FROM items WHERE sku = 'DEMO-BB-002'), 'OUT', 'SALE',
        (SELECT id FROM sales WHERE code = 'SALE/DEMO/0004'), 'SALE/DEMO/0004',
        2.5000, 'STORE', 21.7500, 19.2500, NULL,
        CURRENT_TIMESTAMP - INTERVAL '2 hours', 'admin');

INSERT INTO cash_sessions (
    user_id, opening_cash, closing_cash, status, opened_at, closed_at, version
)
VALUES
    (
        (SELECT id FROM users WHERE username = 'kasir.demo'),
        500000.0000, 740000.0000, 'CLOSED',
        CURRENT_TIMESTAMP - INTERVAL '6 days', CURRENT_TIMESTAMP - INTERVAL '5 days', 0
    ),
    (
        (SELECT id FROM users WHERE username = 'admin'),
        500000.0000, NULL, 'OPEN',
        CURRENT_TIMESTAMP - INTERVAL '8 hours', NULL, 0
    );

INSERT INTO expenses (
    cash_session_id, amount, category, description,
    is_voided, voided_reason, created_at, created_by, version
)
VALUES
    (
        (SELECT id FROM cash_sessions WHERE status = 'OPEN'),
        50000.0000, 'STORE_OPERATIONAL', 'Biaya kebersihan toko demo',
        FALSE, NULL, CURRENT_TIMESTAMP - INTERVAL '6 hours', 'admin', 0
    ),
    (
        (SELECT id FROM cash_sessions WHERE status = 'OPEN'),
        25000.0000, 'HOUSEHOLD', 'Contoh pengeluaran yang dibatalkan',
        TRUE, 'Salah memasukkan transaksi',
        CURRENT_TIMESTAMP - INTERVAL '5 hours', 'admin', 0
    ),
    (
        (SELECT id FROM cash_sessions WHERE status = 'OPEN'),
        75000.0000, 'EMERGENCY_BUY', 'Pembelian darurat perlengkapan toko',
        FALSE, NULL, CURRENT_TIMESTAMP - INTERVAL '3 hours', 'admin', 0
    );

INSERT INTO item_category_counters (item_category_id, current_sequence)
SELECT id, 2 FROM item_categories
ON CONFLICT (item_category_id)
DO UPDATE SET current_sequence = GREATEST(
    item_category_counters.current_sequence,
    EXCLUDED.current_sequence
);

INSERT INTO document_counters (
    document_type, year, month, current_sequence
)
VALUES
    ('GOODS_RECEIPT', EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER,
        EXTRACT(MONTH FROM CURRENT_DATE)::INTEGER, 2),
    ('SALE', EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER,
        EXTRACT(MONTH FROM CURRENT_DATE)::INTEGER, 4),
    ('STOCK_ADJUSTMENT', EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER,
        EXTRACT(MONTH FROM CURRENT_DATE)::INTEGER, 1),
    ('STOCK_TRANSFER', EXTRACT(YEAR FROM CURRENT_DATE)::INTEGER,
        EXTRACT(MONTH FROM CURRENT_DATE)::INTEGER, 2)
ON CONFLICT (document_type, year, month)
DO UPDATE SET current_sequence = GREATEST(
    document_counters.current_sequence,
    EXCLUDED.current_sequence
);

COMMIT;
