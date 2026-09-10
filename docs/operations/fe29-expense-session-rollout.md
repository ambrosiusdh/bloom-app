# FE-29 expense session confirmation rollout

The backend now requires `expectedCashSessionId` on every `POST /api/expenses`, including retries.
The React client lives outside this repository and must be updated as part of deployment.
The complete [backend contract](../architecture/release-1-domain-contract.md#expense-creation-and-session-confirmation-fe-29)
defines validation, HTTP responses, locking, and persisted-key compatibility.

## Frontend confirmation and retries

1. During confirmation, obtain the open session from `GET /api/cash-sessions/current` and show the
   backend-confirmed session to the user. HTTP 200 with `data: null` means there is no open session
   and creation cannot proceed.
2. Capture its `data.id` as `expectedCashSessionId` together with amount, category, description,
   and one idempotency key. Retain this complete confirmed attempt across pending requests,
   timeouts, connection failures, and page reloads as needed for recovery.
3. Submit the captured payload. Disable duplicate submission while it is pending. Do not replace
   the session ID with a newly fetched current-session ID immediately before sending or retrying.
4. After an ambiguous outcome, retry the same key and captured payload. A completed expense in A
   replays successfully even after A closes and B opens. Use the response's actual `cashSessionId`
   and backend financial fields for display.
5. On HTTP 409 `CashSessionConflictException`, show that the confirmed session is no longer eligible
   and refresh session state. Posting into another session requires fresh user confirmation and a
   new attempt/key; never silently retarget or automatically resubmit into B.
6. On HTTP 409 `ExpenseIdempotencyConflictException`, preserve the original attempt for recovery.
   A key already identifies different content or a different recorded session. Do not automatically
   generate another key to bypass this response, since that can duplicate a committed expense.

The frontend collects input and displays backend results. It does not calculate drawer balances,
expense cash effects, or reconciliation totals.

## Deployment and existing attempts

- Coordinate the frontend release with backend enforcement and ensure all backend instances serving
  expense POSTs run the new code. An older backend can still ignore session intent, and an older
  frontend omits the now-required field. Do not expose the new confirmation flow to a mixed backend
  fleet that still permits implicit session selection.
- Use a coordinated cutover or temporarily gate new expense creation while upgrading the backend
  fleet and refreshing old frontend clients. Do not weaken validation to accommodate old clients.
- Existing expenses, idempotency keys, hashes, and movements stay unchanged. No Flyway migration is
  required. A replay of an existing key supplies the original content and the actual session from
  its previously returned or otherwise verified expense record; this remains valid after close.
- Resolve pre-upgrade ambiguous attempts before cutover where possible. If the old client did not
  retain a verified session and the outcome remains unknown, reconcile the attempt against persisted
  expense/audit records before retrying or starting another attempt. The current open session is not
  evidence of which session an old request used. This change does not add an expense-status endpoint
  or reconstruct historical confirmation intent.

## Acceptance checks

Verify a normal confirmed creation, missing/invalid session validation, and A closing/B opening
between confirmation and submission. Verify both a committed and a rolled-back attempt in A followed
by retry after rollover. Confirm changed-session/content conflicts, exactly one posting for concurrent
duplicates, and consistent closing totals when creation races close. Backend service, HTTP, and real
PostgreSQL integration tests cover these behaviors.
