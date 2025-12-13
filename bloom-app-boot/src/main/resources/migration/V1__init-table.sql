CREATE TABLE item_categories (
     id BIGSERIAL PRIMARY KEY,
     name VARCHAR(255),
     code VARCHAR(100) UNIQUE,
     description TEXT,
     active BOOLEAN NOT NULL DEFAULT TRUE,
     created_at TIMESTAMP,
     updated_at TIMESTAMP,
     created_by VARCHAR(255),
     updated_by VARCHAR(255),
     version BIGINT
);

CREATE TABLE item_category_counters (
    id BIGSERIAL PRIMARY KEY,
    item_category_id BIGINT NOT NULL,
    current_sequence BIGINT NOT NULL,
    CONSTRAINT fk_item_category_counters_item_category
        FOREIGN KEY (item_category_id)
            REFERENCES item_categories(id)
            ON DELETE CASCADE
);

CREATE TABLE items (
      id BIGSERIAL PRIMARY KEY,
      name VARCHAR(255),
      sku VARCHAR(100) UNIQUE,
      description TEXT,
      price DOUBLE PRECISION,
      stock_quantity INTEGER,
      active BOOLEAN NOT NULL DEFAULT TRUE,
      item_category_id BIGINT,
      created_at TIMESTAMP,
      updated_at TIMESTAMP,
      created_by VARCHAR(255),
      updated_by VARCHAR(255),
      version BIGINT,
      CONSTRAINT fk_item_category FOREIGN KEY (item_category_id) REFERENCES item_categories(id) ON DELETE SET NULL
);

CREATE INDEX idx_item_category_id ON items(item_category_id);

CREATE TABLE sales (
      id BIGSERIAL PRIMARY KEY,
      code VARCHAR(100) UNIQUE,
      subtotal_amount NUMERIC(19, 2),
      discount_amount NUMERIC(19, 2),
      total_amount NUMERIC(19, 2),
      paid_amount NUMERIC(19, 2),
      description TEXT,
      payment_type VARCHAR(50) NOT NULL,
      created_at TIMESTAMP,
      updated_at TIMESTAMP,
      created_by VARCHAR(255),
      updated_by VARCHAR(255)
);

CREATE INDEX idx_sales_code ON sales(code);
CREATE INDEX idx_sales_created_at ON sales(created_at);

CREATE TABLE sale_items (
       id BIGSERIAL PRIMARY KEY,
       sale_id BIGINT NOT NULL,
       item_id BIGINT NOT NULL,
       quantity INTEGER NOT NULL,
       unit_price NUMERIC(19, 2) NOT NULL,
       subtotal NUMERIC(19, 2),
       CONSTRAINT fk_sale_items_sale FOREIGN KEY (sale_id) REFERENCES sales(id) ON DELETE CASCADE,
       CONSTRAINT fk_sale_items_item FOREIGN KEY (item_id) REFERENCES items(id)
);

CREATE INDEX idx_sale_items_sale_id ON sale_items(sale_id);
CREATE INDEX idx_sale_items_item_id ON sale_items(item_id);

CREATE TABLE stock_adjustments (
      id BIGSERIAL PRIMARY KEY,
      stock_adjustment_code VARCHAR(100) NOT NULL UNIQUE,
      reason TEXT,
      created_by VARCHAR(255),
      created_at TIMESTAMP NOT NULL
);

CREATE TABLE stock_adjustment_items (
    id BIGSERIAL PRIMARY KEY,
    stock_adjustment_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    change_quantity INTEGER NOT NULL,
    previous_stock INTEGER NOT NULL,
    new_stock INTEGER NOT NULL,
    CONSTRAINT fk_stock_adjustment_items_stock_adjustment FOREIGN KEY (stock_adjustment_id) REFERENCES stock_adjustments(id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_adjustment_items_item FOREIGN KEY (item_id) REFERENCES items(id)
);

CREATE INDEX idx_stock_adjustment_items_stock_adjustment_id ON stock_adjustment_items(stock_adjustment_id);
CREATE INDEX idx_stock_adjustment_items_item_id ON stock_adjustment_items(item_id);

CREATE TABLE item_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL,
    qty INTEGER NOT NULL,
    qty_before INTEGER NOT NULL,
    qty_after INTEGER NOT NULL,
    source VARCHAR(50) NOT NULL,
    reference_no VARCHAR(100),
    created_by VARCHAR(255),
    created_date TIMESTAMP NOT NULL,
    CONSTRAINT fk_item_audit_logs_item FOREIGN KEY (item_id) REFERENCES items(id)
);

CREATE INDEX idx_item_audit_logs_item_id ON item_audit_logs(item_id);

CREATE TABLE users (
       id BIGSERIAL PRIMARY KEY,
       username VARCHAR(255) NOT NULL UNIQUE,
       password VARCHAR(255) NOT NULL,
       role VARCHAR(100),
       name VARCHAR(255),
       created_at TIMESTAMP,
       updated_at TIMESTAMP,
       created_by VARCHAR(255),
       updated_by VARCHAR(255)
);

CREATE TABLE stock_movements (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    movement_type VARCHAR(50) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    CONSTRAINT fk_stock_movements_product FOREIGN KEY (product_id) REFERENCES items(id)
);

CREATE INDEX idx_stock_movements_product_id ON stock_movements(product_id);
CREATE INDEX idx_stock_movements_source ON stock_movements(source_type, source_id);

CREATE TABLE document_counters (
    id BIGSERIAL PRIMARY KEY,
    document_type VARCHAR(50) NOT NULL,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL,
    current_sequence BIGINT NOT NULL,
    UNIQUE(document_type, year, month)
);

CREATE TABLE goods_receipts (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) UNIQUE NOT NULL,
    received_date TIMESTAMP NOT NULL,
    supplier_name VARCHAR(255),
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255)
);

CREATE TABLE goods_receipt_items (
    id BIGSERIAL PRIMARY KEY,
    goods_receipt_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT fk_goods_receipt_items_receipt
        FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(id) ON DELETE CASCADE,
    CONSTRAINT fk_goods_receipt_items_item FOREIGN KEY (item_id) REFERENCES items(id)
);

CREATE INDEX idx_goods_receipt_items_receipt_id ON goods_receipt_items(goods_receipt_id);
