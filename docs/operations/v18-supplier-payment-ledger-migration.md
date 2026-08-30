# V18 supplier-payment ledger migration runbook

V18 is already shared migration history and must remain checksum-stable. Follow-up
ledger hardening is applied by `V19__harden_supplier_payment_consistency.sql` and
`V20__enforce_supplier_payment_effective_time.sql`.

V18 intentionally refuses to discard or fabricate audit history for a non-zero
legacy `goods_receipts.paid_amount`. Run this preflight against the target database
before deploying the application version that contains V18:

```sql
SELECT
    COUNT(*) AS affected_receipts,
    COALESCE(SUM(paid_amount), 0) AS legacy_paid_amount
FROM goods_receipts
WHERE paid_amount <> 0;
```

## Expected result

`affected_receipts = 0` means V18 can perform the ledger cut-over normally.

## Non-zero result

Stop the deployment and reconcile each affected receipt with the business owner.
Do not assign an invented payment method, actor, payment timestamp, or reference.
The approved remediation must preserve the source evidence for each historical
payment and be rehearsed on a production snapshot before V18 is retried.

After migration, verify that `goods_receipts.paid_amount` no longer exists and that
receipt balances are derived from non-voided rows in `supplier_payments`.

## Release 1 correction boundary

A CASH supplier payment can be voided only while its original cash session remains
open. Once that session is closed, Release 1 rejects the void so reconciled drawer
history cannot change. Do not edit the payment or drawer rows manually. A future
post-close correction workflow requires an explicitly approved accounting and
physical-cash policy.

`supplier_payments.paid_at` is the business-effective time supplied for the payment.
`cash_movements.recorded_at` is the server time at which the immutable drawer fact
was recorded. They may differ intentionally when an operator records a payment
later within the same open session.
