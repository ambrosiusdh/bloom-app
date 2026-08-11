# StockMovement audit cutover

`StockMovement` is the only authoritative stock ledger and the only audit fact written by the new application.
`ItemAuditLog` remains solely for temporary API/schema compatibility.

## Migration safeguards

Flyway V13 creates an immutable link between every historical `StockMovement` and `ItemAuditLog` row. The migration
fails when historical coverage is not one-to-one or when a legacy human-readable reference cannot be reconstructed
from the authoritative source record. It does not update either historical table.

Historical references are derived at read time from the source document (`Sale`, `StockAdjustment`, `GoodsReceipt`,
`StockTransfer`, or the opening-balance item). Historical adjustment actions are derived from the referenced
`StockAdjustmentItem`. Legacy audit responses use the linked `ItemAuditLog.id`; new records use `StockMovement.id`.

## Deployment and rollback constraint

This cutover intentionally stops all new `ItemAuditLog` writes. Consequently, it must not use a mixed-version rolling
deployment: an old application instance cannot see movements written by the new version. Deploy by stopping old
instances, running Flyway, and starting only the new version (or use blue/green traffic switching with no mixed-write
window).

After the first new movement is committed, rolling the application binary back to the old ItemAuditLog-reading version
would produce incomplete audit history. Treat this schema/application cutover as forward-only; recover by rolling
forward. A rollback that must restore the old binary also requires restoring the database to its pre-cutover backup.
