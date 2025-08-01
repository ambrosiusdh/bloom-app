CREATE TABLE item_categories (
     id BIGSERIAL PRIMARY KEY,
     name VARCHAR(255),
     code VARCHAR(100) UNIQUE,
     description TEXT,
     active BOOLEAN NOT NULL DEFAULT TRUE,
     created_at TIMESTAMP,
     updated_at TIMESTAMP,
     created_by VARCHAR(255),
     updated_by VARCHAR(255)
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
      CONSTRAINT fk_item_category FOREIGN KEY (item_category_id) REFERENCES item_categories(id) ON DELETE SET NULL
);

CREATE INDEX idx_item_category_id ON items(item_category_id);

CREATE TABLE sales (
      id BIGSERIAL PRIMARY KEY,
      code VARCHAR(100) UNIQUE,
      total_amount NUMERIC(19, 2),
      discount_amount NUMERIC(19, 2),
      description TEXT,
      created_at TIMESTAMP,
      updated_at TIMESTAMP,
      created_by VARCHAR(255),
      updated_by VARCHAR(255)
);

CREATE TABLE sale_items (
       id BIGSERIAL PRIMARY KEY,
       sale_id BIGINT REFERENCES sales(id) ON DELETE CASCADE,
       item_id BIGINT REFERENCES items(id),
       quantity INTEGER,
       unit_price NUMERIC(19, 2),
       subtotal NUMERIC(19, 2)
);

CREATE TABLE stock_adjustments (
      id BIGSERIAL PRIMARY KEY,
      item_id BIGINT REFERENCES items(id),
      code VARCHAR(100) UNIQUE,
      quantity_change INTEGER,
      reason TEXT,
      created_at TIMESTAMP,
      updated_at TIMESTAMP,
      created_by VARCHAR(255),
      updated_by VARCHAR(255)
);

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
