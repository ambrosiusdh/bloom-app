-- Canonicalize any pre-existing supplier codes before enforcing the application
-- policy. Case-only duplicates deliberately fail this migration instead of leaving
-- ambiguous historical identities.
UPDATE suppliers
SET code = UPPER(BTRIM(code));

-- Supplier codes remain unique for the entire supplier lifecycle. Suppliers with
-- financial history are retained and deactivated rather than removed.
ALTER TABLE suppliers
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT chk_suppliers_code_canonical
        CHECK (code = UPPER(BTRIM(code)));

ALTER TABLE suppliers
    ALTER COLUMN active DROP DEFAULT;
