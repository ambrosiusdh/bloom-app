CREATE TABLE item (
      id BIGSERIAL PRIMARY KEY,
      name VARCHAR(255),
      sku VARCHAR(100),
      description TEXT,
      price DOUBLE PRECISION,
      stock_quantity INTEGER,
      active BOOLEAN NOT NULL DEFAULT TRUE,
      created_at TIMESTAMP,
      updated_at TIMESTAMP,
      created_by VARCHAR(255),
      updated_by VARCHAR(255)
);

CREATE TABLE sale (
      id BIGSERIAL PRIMARY KEY,
      code VARCHAR(100),
      total_amount NUMERIC(19, 2),
      discount_amount NUMERIC(19, 2),
      description TEXT,
      created_at TIMESTAMP,
      updated_at TIMESTAMP,
      created_by VARCHAR(255),
      updated_by VARCHAR(255)
);

CREATE TABLE sale_item (
       id BIGSERIAL PRIMARY KEY,
       sale_id BIGINT REFERENCES sale(id) ON DELETE CASCADE,
       item_id BIGINT REFERENCES item(id),
       quantity INTEGER,
       unit_price NUMERIC(19, 2),
       subtotal NUMERIC(19, 2)
);

CREATE TABLE stock_adjustment (
      id BIGSERIAL PRIMARY KEY,
      item_id BIGINT REFERENCES item(id),
      code VARCHAR(100),
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
