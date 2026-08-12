-- Align the V1 schema with the location-aware inventory and currently scanned entities.

-- V2 contains disposable test inventory in one stock bucket. Treat that bucket as
-- immediately saleable STORE stock and start WAREHOUSE at zero. Keeping stock_quantity
-- makes the backfill auditable and avoids coupling this release to legacy cleanup.
ALTER TABLE items
    ALTER COLUMN price TYPE NUMERIC(19, 4) USING price::NUMERIC(19, 4),
    ADD COLUMN stock_store INTEGER,
    ADD COLUMN stock_warehouse INTEGER;

UPDATE items
SET stock_store = COALESCE(stock_quantity, 0),
    stock_warehouse = 0;

ALTER TABLE items
    ALTER COLUMN stock_store SET DEFAULT 0,
    ALTER COLUMN stock_store SET NOT NULL,
    ALTER COLUMN stock_warehouse SET DEFAULT 0,
    ALTER COLUMN stock_warehouse SET NOT NULL;

ALTER INDEX idx_item_category_id RENAME TO idx_items_category_id;

-- Historical transaction locations cannot be reconstructed safely. The committed
-- V1/V2 lineage has no rows in these tables, so fail rather than invent locations.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM sale_items)
        OR EXISTS (SELECT 1 FROM stock_adjustment_items) THEN
        RAISE EXCEPTION
            'Cannot align legacy sale or adjustment items without an approved stock-location backfill';
    END IF;
END
$$;

ALTER TABLE sale_items
    ALTER COLUMN unit_price TYPE NUMERIC(19, 4)
        USING unit_price::NUMERIC(19, 4),
    ALTER COLUMN subtotal TYPE NUMERIC(19, 4)
        USING subtotal::NUMERIC(19, 4),
    ADD COLUMN stock_location VARCHAR(50) NOT NULL;

ALTER TABLE stock_adjustment_items
    ADD COLUMN stock_location VARCHAR(50) NOT NULL;

ALTER TABLE sales
    ALTER COLUMN subtotal_amount TYPE NUMERIC(19, 4)
        USING subtotal_amount::NUMERIC(19, 4),
    ALTER COLUMN discount_amount TYPE NUMERIC(19, 4)
        USING discount_amount::NUMERIC(19, 4),
    ALTER COLUMN total_amount TYPE NUMERIC(19, 4)
        USING total_amount::NUMERIC(19, 4),
    ALTER COLUMN paid_amount TYPE NUMERIC(19, 4)
        USING paid_amount::NUMERIC(19, 4);

-- A movement's before/after balances cannot be reconstructed safely from V1 data.
-- The committed V1/V2 lineage has no rows here; fail clearly instead of fabricating movement history.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM stock_movements) THEN
        RAISE EXCEPTION
            'Cannot add stock movement balances: pre-existing stock_movements require an approved reconstruction rule';
    END IF;
END
$$;

ALTER TABLE stock_movements
    ADD COLUMN stock_location VARCHAR(50) NOT NULL,
    ADD COLUMN qty_before INTEGER NOT NULL,
    ADD COLUMN qty_after INTEGER NOT NULL;

CREATE INDEX idx_stock_movements_created_at ON stock_movements(created_at);

ALTER TABLE item_category_counters
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uq_item_category_counters_category UNIQUE (item_category_id),
    ADD CONSTRAINT uq_item_category_counters_category_sequence
        UNIQUE (item_category_id, current_sequence);

ALTER TABLE item_category_counters
    ALTER COLUMN version DROP DEFAULT;

-- Legacy receipts cannot acquire reliable supplier, monetary, price, or location data
-- without a business rule. V1/V2 create these tables empty, so reject ambiguous upgrades.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM goods_receipts)
        OR EXISTS (SELECT 1 FROM goods_receipt_items) THEN
        RAISE EXCEPTION
            'Cannot align legacy goods receipts without approved supplier, totals, price, and location backfills';
    END IF;
END
$$;

CREATE TABLE suppliers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(255) NOT NULL UNIQUE,
    contact_number VARCHAR(255),
    address VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT NOT NULL
);

ALTER TABLE goods_receipts
    ADD COLUMN supplier_id BIGINT NOT NULL,
    ADD COLUMN total_amount NUMERIC(19, 4) NOT NULL,
    ADD COLUMN paid_amount NUMERIC(19, 4) NOT NULL,
    ADD CONSTRAINT fk_goods_receipts_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers(id);

CREATE INDEX idx_goods_receipts_supplier_id ON goods_receipts(supplier_id);

ALTER TABLE goods_receipt_items
    ADD COLUMN purchase_price NUMERIC(19, 4) NOT NULL,
    ADD COLUMN stock_location VARCHAR(50) NOT NULL;

CREATE TABLE cash_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    opening_cash NUMERIC(19, 4) NOT NULL,
    closing_cash NUMERIC(19, 4),
    status VARCHAR(50) NOT NULL,
    opened_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP,
    CONSTRAINT fk_cash_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_cash_sessions_user_id ON cash_sessions(user_id);
CREATE UNIQUE INDEX uq_cash_sessions_single_open
    ON cash_sessions(status)
    WHERE status = 'OPEN';

CREATE TABLE expenses (
    id BIGSERIAL PRIMARY KEY,
    cash_session_id BIGINT NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    is_voided BOOLEAN NOT NULL DEFAULT FALSE,
    voided_reason VARCHAR(255),
    created_at TIMESTAMP,
    created_by VARCHAR(255),
    CONSTRAINT fk_expenses_cash_session
        FOREIGN KEY (cash_session_id) REFERENCES cash_sessions(id)
);

CREATE INDEX idx_expenses_cash_session_id ON expenses(cash_session_id);
