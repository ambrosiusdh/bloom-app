# V21 Release 1 contract-cleanup runbook

V21 removes obsolete schema and tightens existing Release 1 facts. Run these
checks against a V20 production snapshot before deploying it. V21 is shared
migration history and must remain checksum-stable; later corrections belong in a
new migration.

## Legacy-history preflight

First check whether either optional legacy table exists:

```sql
SELECT
    to_regclass('public.item_audit_logs') AS item_audit_logs,
    to_regclass('public.stock_movement_legacy_audit_links') AS movement_links;
```

For every table reported as present, run its matching count:

```sql
SELECT COUNT(*) AS remaining_item_audit_rows FROM item_audit_logs;
SELECT COUNT(*) AS remaining_movement_link_rows FROM stock_movement_legacy_audit_links;
```

Both counts must be zero. A non-zero result is an intentional deployment blocker:
stop and agree an evidence-preserving migration with the business owner. Do not
delete, summarize, or fabricate audit history merely to make V21 pass.

## Constraint preflight

The following query must return no rows. Its labels identify the data rule that
would block V21:

```sql
SELECT 'items required/price' AS violation, COUNT(*) AS affected
FROM items
WHERE name IS NULL OR price IS NULL OR item_category_id IS NULL OR price < 0
HAVING COUNT(*) > 0
UNION ALL
SELECT 'sale arithmetic', COUNT(*)
FROM sales
WHERE subtotal_amount IS NULL OR discount_amount IS NULL OR total_amount IS NULL
   OR paid_amount IS NULL OR subtotal_amount <= 0 OR discount_amount < 0
   OR total_amount <= 0 OR paid_amount < 0
   OR total_amount <> subtotal_amount - discount_amount
HAVING COUNT(*) > 0
UNION ALL
SELECT 'sale line money/location', COUNT(*)
FROM sale_items
WHERE unit_price IS NULL OR subtotal IS NULL OR unit_price < 0 OR subtotal < 0
   OR stock_location NOT IN ('STORE', 'WAREHOUSE')
   OR subtotal <> ROUND(quantity * unit_price, 4)
HAVING COUNT(*) > 0
UNION ALL
SELECT 'adjustment reason', COUNT(*)
FROM stock_adjustments
WHERE reason IS NULL OR LENGTH(BTRIM(reason)) = 0
HAVING COUNT(*) > 0
UNION ALL
SELECT 'negative category counter', COUNT(*)
FROM item_category_counters
WHERE current_sequence < 0
HAVING COUNT(*) > 0
UNION ALL
SELECT 'negative document counter', COUNT(*)
FROM document_counters
WHERE current_sequence < 0
HAVING COUNT(*) > 0;
```

Also rehearse the migration on a recent production snapshot. Constraint failures
must be reconciled from source evidence; do not invent transaction values.

## Decimal-shape verification

The V20 lineage already stores the historical numeric columns that V21 touches at
scale four. Verify that assumption with metadata instead of scanning for values
that the V20 column types cannot represent:

```sql
SELECT table_name, column_name, numeric_precision, numeric_scale
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (table_name, column_name) IN (
      ('items', 'price'),
      ('sales', 'subtotal_amount'), ('sales', 'discount_amount'),
      ('sales', 'total_amount'), ('sales', 'paid_amount'),
      ('sales', 'change_amount'),
      ('sale_items', 'quantity'), ('sale_items', 'unit_price'),
      ('sale_items', 'subtotal'),
      ('goods_receipt_items', 'quantity'),
      ('goods_receipt_items', 'purchase_price'),
      ('goods_receipt_items', 'line_total'),
      ('stock_movements', 'quantity'), ('stock_movements', 'qty_before'),
      ('stock_movements', 'qty_after')
  )
  AND (numeric_precision <> 19 OR numeric_scale <> 4);
```

The query must return no rows. If it does, the database is not on the supported
V20 lineage; stop and investigate rather than allowing an unreviewed conversion.

## Post-deployment verification

Confirm that Flyway applied V21 (and any later migration in the same release),
that `items.stock_quantity` and both legacy audit tables are absent, and that
`goods_receipts.supplier_name_snapshot` exists. Application startup must complete
with Hibernate validation enabled. Migration V22 then rejects updates and deletes
of recorded sales and sale lines until an auditable correction workflow exists.
