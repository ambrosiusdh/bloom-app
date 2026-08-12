# StockMovement audit cutover

`StockMovement` is the only authoritative stock ledger and the only audit fact written by the new application.
`ItemAuditLog` remains solely for temporary API/schema compatibility.

## Migration safeguards

Flyway V13 creates an immutable link between every historical `StockMovement` and `ItemAuditLog` row. It fails when
historical coverage is not one-to-one or when a legacy human-readable reference cannot be reconstructed. V14 then
reconciles the captured pre-cutover sets again with the reference included in candidate identity, fails missing or
ambiguous matches, and snapshots the verified reference and adjustment action on the compatibility link. Neither
migration updates either historical ledger.

Historical reads use the V14 compatibility snapshots, so later SKU or source-record changes cannot rewrite displayed
history. Adjustment actions are matched by source, item, location, balances, magnitude, and direction before being
snapshotted. Legacy audit responses use the linked `ItemAuditLog.id`; new records use `StockMovement.id`.

## Local demo data

Demo data is not a Flyway migration. Run `scripts/dev/seed-release-1-demo.sql` manually only against a development
database after V14. The script is transactional, rejects retries, and gives `kasir.demo` a password hash independent
from `admin`.

If the former demo V14 already ran locally, stop the application and run
`scripts/dev/reset-flyway-to-v13.sql` once. It verifies the deleted demo migration is the latest entry, removes only
that Flyway history row, and retains all seeded business data; the next application start applies the new V14.

## Deployment and rollback constraint

This cutover intentionally stops all new `ItemAuditLog` writes. Consequently, it must not use a mixed-version rolling
deployment: an old application instance cannot see movements written by the new version. Deploy by stopping old
instances, running Flyway, and starting only the new version (or use blue/green traffic switching with no mixed-write
window).

After the first new movement is committed, rolling the application binary back to the old ItemAuditLog-reading version
would produce incomplete audit history. Treat this schema/application cutover as forward-only; recover by rolling
forward. A rollback that must restore the old binary also requires restoring the database to its pre-cutover backup.
