CREATE TABLE stock_transfers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    source_location VARCHAR(50) NOT NULL,
    destination_location VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_stock_transfers_code UNIQUE (code),
    CONSTRAINT uq_stock_transfers_request_key UNIQUE (request_key),
    CONSTRAINT chk_stock_transfers_request_key_not_blank
        CHECK (LENGTH(BTRIM(request_key)) > 0),
    CONSTRAINT chk_stock_transfers_request_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_stock_transfers_source_location
        CHECK (source_location IN ('STORE', 'WAREHOUSE')),
    CONSTRAINT chk_stock_transfers_destination_location
        CHECK (destination_location IN ('STORE', 'WAREHOUSE')),
    CONSTRAINT chk_stock_transfers_distinct_locations
        CHECK (source_location <> destination_location)
);

CREATE INDEX idx_stock_transfers_created_at ON stock_transfers(created_at);

CREATE TABLE stock_transfer_lines (
    id BIGSERIAL PRIMARY KEY,
    stock_transfer_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    item_sku VARCHAR(255) NOT NULL,
    unit_of_measure VARCHAR(30) NOT NULL,
    quantity NUMERIC(19,4) NOT NULL,
    CONSTRAINT fk_stock_transfer_lines_transfer
        FOREIGN KEY (stock_transfer_id) REFERENCES stock_transfers(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_transfer_lines_item
        FOREIGN KEY (item_id) REFERENCES items(id),
    CONSTRAINT uq_stock_transfer_lines_transfer_item
        UNIQUE (stock_transfer_id, item_id),
    CONSTRAINT chk_stock_transfer_lines_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_stock_transfer_lines_item_id ON stock_transfer_lines(item_id);

-- A transfer has at most one movement for each item/location. Because the header
-- enforces two different locations, this also prevents a duplicate OUT or IN side.
CREATE UNIQUE INDEX uq_stock_movements_transfer_item_location
    ON stock_movements (source_id, product_id, stock_location)
    WHERE source_type = 'TRANSFER';

COMMENT ON COLUMN stock_transfers.request_hash IS
    'SHA-256 of the canonical semantic request, used to detect request-key reuse';
COMMENT ON COLUMN stock_transfer_lines.quantity IS
    'Quantity expressed directly in the item base unit of measure; no conversion';
COMMENT ON COLUMN stock_transfer_lines.item_sku IS
    'Immutable item SKU snapshot used by historical and idempotent responses';
COMMENT ON COLUMN stock_transfer_lines.unit_of_measure IS
    'Immutable base-unit snapshot for the transferred quantity';
