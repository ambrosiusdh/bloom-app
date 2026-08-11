-- Historical stock movements remain immutable. Their reference is intentionally nullable.
ALTER TABLE stock_movements
    ADD COLUMN reference_no VARCHAR(100);
