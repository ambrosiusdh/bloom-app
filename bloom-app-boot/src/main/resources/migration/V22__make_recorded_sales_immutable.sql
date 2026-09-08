-- A Release 1 sale is a committed financial and inventory fact. No sale void,
-- refund, return, edit, or delete operation exists, so recorded headers and
-- lines must not be changed even when SQL bypasses the application service.
CREATE OR REPLACE FUNCTION reject_recorded_sale_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'recorded % rows are immutable in Release 1', TG_TABLE_NAME;
END;
$$;

CREATE TRIGGER trg_sales_immutable
BEFORE UPDATE OR DELETE ON sales
FOR EACH ROW
EXECUTE FUNCTION reject_recorded_sale_mutation();

CREATE TRIGGER trg_sale_items_immutable
BEFORE UPDATE OR DELETE ON sale_items
FOR EACH ROW
EXECUTE FUNCTION reject_recorded_sale_mutation();

COMMENT ON FUNCTION reject_recorded_sale_mutation() IS
    'Rejects sale and sale-line mutation until an auditable correction workflow exists';
