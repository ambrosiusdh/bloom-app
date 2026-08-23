# Bloom POS Release 1 Domain Contract

**Status:** Approved Release 1 decisions, with implementation recommendations and unresolved decisions called out separately  
**Scope:** Bloom POS backend and its existing React web client  
**Repository reviewed:** the Maven modules `bloom-app-api`, `bloom-app-boot`, `bloom-app-domain`, `bloom-app-persistence`, `bloom-app-service`, `bloom-app-validation`, and `bloom-app-web`

## Purpose

This document is the Release 1 contract for schema ownership, numeric precision, inventory, supplier accounts payable, cash sessions, sales, expenses, and client scope.

The words **must**, **must not**, **only**, and **cannot** in a **Confirmed decision** or **Required consequence** are normative.

- **Confirmed decision** means an approved Release 1 decision.
- **Required consequence** means behavior necessary to make the confirmed decisions internally consistent. It does not introduce a new product feature.
- **Recommendation** means implementation guidance that is not itself an approved product decision.
- **Unresolved** means implementation must not assume an answer without a follow-up decision.

Repository names are used where they already exist: `Item`, `Sale`, `SaleItem`, `GoodsReceipt`, `GoodsReceiptItem`, `Supplier`, `StockMovement`, `StockMovementService`, `CashSession`, `Expense`, `PaymentType`, `StockLocation`, `MovementType`, `MovementSourceType`, and `ExpenseCategory`.

## Confirmed Release 1 decision register

| ID | Confirmed decision |
|---|---|
| R1-01 | PostgreSQL schema is managed exclusively by Flyway. |
| R1-02 | After schema alignment, Hibernate uses `ddl-auto=validate`. Hibernate must never generate DDL. |
| R1-03 | Money and inventory quantities use Java `BigDecimal` and PostgreSQL `NUMERIC(19,4)`. |
| R1-04 | Quantity input may have at most four decimal places and must not be silently rounded. |
| R1-05 | Money uses `RoundingMode.HALF_UP` at the calculation boundaries documented below. |
| R1-06 | `Item` has `baseUnitOfMeasure`, `fractionalQuantityAllowed`, `stockStore`, and `stockWarehouse`. |
| R1-07 | Package conversion, including ROLL-to-METER conversion, is outside Release 1. |
| R1-08 | An item's `baseUnitOfMeasure` and `fractionalQuantityAllowed` cannot change after that item has any stock movement. |
| R1-09 | Every stock mutation goes through `StockMovementService`. |
| R1-10 | Opening inventory creates `OPENING_BALANCE` stock movements. |
| R1-11 | Stock cannot become negative. |
| R1-12 | `StockLocation` contains only `STORE` and `WAREHOUSE` in Release 1. |
| R1-13 | Supplier debt is accounts payable only. It is not customer credit or accounts receivable. |
| R1-14 | Supplier payments support only `CASH`, `BANK_TRANSFER`, and `QRIS`. |
| R1-15 | Only `CASH` supplier payments affect physical drawer cash. |
| R1-16 | At most one `CashSession` may be globally `OPEN`. |
| R1-17 | Every cashier `Sale`, including a non-cash sale, belongs to the globally open `CashSession`. |
| R1-18 | `CASH` sales increase drawer cash; `QRIS` sales do not affect drawer cash. |
| R1-19 | An unexpected `Expense` uses store drawer money and therefore requires an open `CashSession`. |
| R1-20 | A `CLOSED` cash session rejects new cash expenses and every other new operation that would change that session's drawer cash. |
| R1-21 | Expense mistakes are voided or reversed and retained for audit; they are never deleted. |
| R1-22 | Release 1 targets the existing React web app. React Native is future scope. |

## Schema ownership and runtime validation

### Confirmed decisions

Flyway is the only component allowed to create, alter, or drop PostgreSQL schema objects. Every implementation change to a mapped table, column, constraint, index, or data migration must be represented by an ordered Flyway migration.

Once the schema and mappings are aligned, all environments must use:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Hibernate schema-generation modes such as `create`, `create-drop`, and `update` are prohibited. `ddl-auto=none` is not the Release 1 target because it does not detect mapping drift.

### Required consequence

Deployment must run Flyway migrations before Hibernate validation. A validation failure is a deployment failure; it must not be worked around by allowing Hibernate to modify the schema.

## Numeric contract

### Storage and Java types

Every monetary value and every inventory quantity uses:

- Java: `BigDecimal`
- PostgreSQL: `NUMERIC(19,4)`

This includes, at minimum:

- `Item.price`, `Item.stockStore`, and `Item.stockWarehouse`
- quantities and before/after balances in `SaleItem`, `GoodsReceiptItem`, `StockAdjustmentItem`, and `StockMovement`
- purchase price, sale unit price, line subtotals, document subtotals, discounts, totals, payment amounts, expense amounts, and cash-session amounts

Binary floating-point types (`float`, `double`, `REAL`, and `DOUBLE PRECISION`) must not represent money or inventory quantity.

### Quantity validation

Quantity input must be validated before calculation or persistence:

1. It must be present where the operation requires a quantity.
2. It must be positive for a new movement request. Direction is represented by the operation or existing `MovementType`, not by accepting an arbitrary negative input.
3. Its input scale must be no greater than four. Even an additional trailing zero is a fifth decimal place and is rejected.
4. When `Item.fractionalQuantityAllowed` is `false`, the normalized quantity must be a whole number.
5. When `Item.fractionalQuantityAllowed` is `true`, a fractional quantity is permitted, but still at no more than four decimal places.
6. A value with more than four significant decimal places must be rejected. Calling `setScale(4, ...)` to make such a quantity fit is prohibited.

Examples:

| Input | Fractional allowed | Result |
|---|---:|---|
| `2`, `2.0`, or `2.0000` | false | Accept |
| `2.5000` | false | Reject |
| `2.5000` | true | Accept |
| `2.50000` | true | Reject; the input has five decimal places |
| `2.50001` | true | Reject; never silently round |

### Money calculation boundaries

`RoundingMode.HALF_UP` is used only when a calculated monetary result crosses one of these boundaries:

1. **Line boundary:** `quantity × unitPrice` becomes a persisted sale or receipt line subtotal.
2. **Document boundary:** line subtotals and an entered discount become a persisted sale or goods-receipt subtotal/total.
3. **Payment boundary:** a calculated amount to apply to accounts payable becomes a persisted supplier-payment amount.
4. **Cash reconciliation boundary:** aggregated cash activity becomes expected closing cash or session variance.

At each boundary, the result is normalized to scale four with `HALF_UP`. Intermediate operations inside one boundary retain their available decimal precision. Monetary inputs and outputs must be represented as decimal values, never converted through `double`.

## Item and stock contract

### Item fields

`Item` has the following inventory fields:

| Field | Meaning |
|---|---|
| `baseUnitOfMeasure` | The one unit in which Release 1 stores, moves, sells, and receives this item's quantity. |
| `fractionalQuantityAllowed` | Whether this item accepts a non-whole quantity in its base UOM. It does not enable unit conversion. |
| `stockStore` | Current derived balance at `StockLocation.STORE`. |
| `stockWarehouse` | Current derived balance at `StockLocation.WAREHOUSE`. |

Release 1 does not convert between units. If an item's base UOM is METER, all quantities for that item are expressed directly in METER; a ROLL purchase cannot be converted to METER by Release 1.

The exact allowed UOM vocabulary and its Java/database representation remain unresolved. No conversion ratio, package definition, alternate UOM, or UOM hierarchy is implied by `baseUnitOfMeasure`.

### Item inventory read response

The public item list and detail responses expose the same inventory state: `active`, `baseUnitOfMeasure`, `fractionalQuantityAllowed`, `stockStore`, `stockWarehouse`, `hasStockMovements`, `baseUnitOfMeasureLocked`, and `fractionalQuantityAllowedLocked`.

`hasStockMovements` is true when at least one `StockMovement` exists for the item. Both lock fields are true exactly when `hasStockMovements` is true, because both measurement rules become immutable at the first movement. The separate lock fields are retained so clients consume explicit server policy rather than infer it from balances or assume both policies will always evolve together.

`stockStore` and `stockWarehouse` are independent quantities. Clients must not merge them. The legacy `stockQuantity` response field is retained temporarily only for compatibility and is deprecated in the API schema. New and migrated clients must use `stockStore` and `stockWarehouse`; `stockQuantity` is not authoritative for the item inventory read model and will be removed in a later compatibility cleanup.

### Stock authority

`StockMovement` is the authoritative stock ledger. `Item.stockStore` and `Item.stockWarehouse` are derived, transactionally maintained balance fields for their respective locations.

For an item and location:

```text
current stock
  = sum(IN movement quantities)
  - sum(OUT movement quantities)
```

An opening quantity is represented by an `IN` movement whose source semantics are `OPENING_BALANCE`. In the repository's current vocabulary, this means retaining `MovementType.IN` and introducing `OPENING_BALANCE` as the movement source value rather than treating it as a third direction.

### Stock invariants

1. All operations that alter stock call `StockMovementService`; controllers, mappers, repositories, item master-data updates, imports, and other services must not write `stockStore` or `stockWarehouse` directly.
2. Item creation with non-zero opening stock must create the item and corresponding `OPENING_BALANCE` movement or movements in one transaction.
3. Each movement addresses exactly one `Item` and one `StockLocation`.
4. Release 1 accepts only `STORE` and `WAREHOUSE`; an unknown or null stock location is rejected.
5. Movement quantity is positive and satisfies the item's fractional-quantity rule. `MovementType.IN` or `MovementType.OUT` supplies the direction.
6. The movement's before balance equals the applicable item-location balance immediately before mutation.
7. The movement's after balance equals before plus an `IN` quantity or before minus an `OUT` quantity.
8. The after balance must be greater than or equal to zero. The movement and balance update fail atomically if it would be negative.
9. The saved movement and the applicable `Item` balance must commit in the same transaction.
10. The two location balances are independent. Availability in `WAREHOUSE` cannot satisfy a `STORE` sale without a separately approved stock transfer operation.
11. After the first `StockMovement` exists for an item, both `baseUnitOfMeasure` and `fractionalQuantityAllowed` are immutable. A request that leaves either value unchanged remains valid.
12. Historical movements are retained. A stock correction is another movement through `StockMovementService`, not an edit to a prior movement or a direct overwrite of an item balance.

## Supplier receipt, accounts-payable, and payment contract

### Domain boundary

The repository calls an inbound supplier receipt `GoodsReceipt` with `GoodsReceiptItem` lines and relates it to `Supplier`. Release 1 keeps that terminology.

Supplier debt means only the amount Bloom owes a supplier for recorded goods receipts. It must not be reused for customer balances, customer credit, store credit, or accounts receivable.

### Authoritative versus derived financial fields

| Context | Authoritative input or recorded fact | Derived field |
|---|---|---|
| Item master | `Item.price` | None in this contract |
| Sale line | quantity; the persisted unit-price snapshot selected by backend sale processing | `SaleItem.subtotal = quantity × unitPrice`, rounded at the line boundary |
| Sale | discount amount; payment method; linked `CashSession` | subtotal from line subtotals; total from subtotal less discount |
| Goods-receipt line | quantity; persisted purchase-price snapshot | line subtotal = quantity × purchase price, rounded at the line boundary |
| Goods receipt | supplier; received date; receipt lines | receipt total from line subtotals |
| Supplier payment | payment amount and one of `CASH`, `BANK_TRANSFER`, or `QRIS` | total paid and outstanding payable aggregated from recorded payments |
| Expense | recorded amount, category, description, session, and void metadata | drawer effect: amount when active, zero when voided |
| Cash session | opening cash; actual counted closing cash | cash sales, cash supplier payments, active expenses, expected closing cash, and variance |
| Item stock | `StockMovement` facts | `Item.stockStore` and `Item.stockWarehouse` balances |

Client-supplied sale subtotals/totals and goods-receipt subtotals/totals are not authoritative. If they are accepted for compatibility, the backend must recompute them and reject a mismatch rather than persist an inconsistent value.

The current `Sale.paidAmount` meaning is unresolved. Drawer reconciliation must use the recorded `Sale.totalAmount` for a `CASH` sale, not an ambiguous tendered or client-provided `paidAmount`.

### Receipt and payment lifecycle

Release 1 does not require a new receipt status enum. Its minimum lifecycle is:

1. Validate a `GoodsReceipt`, all `GoodsReceiptItem` lines, the supplier, money, quantities, and stock locations.
2. Persist the receipt and its lines, calculate its financial totals, and create its stock movements atomically.
3. Once recorded, the receipt increases supplier accounts payable by its derived total.
4. Record zero or more supplier payments against accounts payable using only `CASH`, `BANK_TRANSFER`, or `QRIS`.
5. Each recorded supplier payment reduces the outstanding payable by its applied amount.
6. A receipt is financially settled when its outstanding amount is zero; this is a derived condition and does not require a stored status.

```text
goods receipt total
  = sum(rounded goods-receipt line subtotals)

supplier amount paid
  = sum(recorded supplier payments applied to the receipt)

supplier amount outstanding
  = goods receipt total - supplier amount paid
```

Only a `CASH` supplier payment reduces drawer cash. It must belong to the currently open `CashSession` and must be rejected when no session is open. `BANK_TRANSFER` and `QRIS` supplier payments have no physical drawer effect.

The allocation rules for payments that cover more than one receipt, overpayments, and supplier credits are unresolved. Release 1 implementation must not invent those behaviors.

## Cash-session, sale, and expense contract

### Cash session

`CashSessionStatus` remains `OPEN` or `CLOSED`.

1. There may be zero or one globally open cash session, never more than one.
2. The invariant is global, not one open session per user.
3. Opening a session records its cashier/user, `openingCash`, and `openedAt`.
4. Closing records the actual counted `closingCash`, `closedAt`, and changes status to `CLOSED`.
5. A closed session cannot accept a new operation whose effect would alter that session's drawer cash.

### Sales

Every cashier sale belongs to the open `CashSession`. A sale must be rejected when there is no open session, including a `QRIS` sale.

- A `CASH` sale increases expected drawer cash by `Sale.totalAmount`.
- A `QRIS` sale has zero drawer effect.

This contract does not add `BANK_TRANSFER` as a sale payment method. The current sale `PaymentType` values `CASH` and `QRIS` remain the Release 1 sales scope.

### Expenses

An `Expense` in Release 1 is an unexpected outflow of store drawer cash.

- It requires and belongs to the currently open `CashSession`.
- Recording it decreases expected drawer cash by its amount.
- It must be rejected if no session is open or the target session is closed.
- A mistake is retained and voided/reversed, never hard-deleted.
- The existing `Expense.isVoided` and `voidedReason` vocabulary is the canonical minimum: a void requires a reason, leaves the original record present, and makes its net drawer effect zero.

An expense from a closed session cannot be voided in Release 1 because the compensating movement would mutate reconciled drawer history. A post-close correction workflow remains outside Release 1.

### Allowed expense categories

Release 1 allows exactly these `ExpenseCategory` values:

| Value | Release 1 meaning |
|---|---|
| `STORE_OPERATIONAL` | Unexpected store-operational spending paid from the drawer. |
| `FOOD_AND_DRINK` | Food or drink paid from the drawer. |
| `CHARITY` | A charitable outflow paid from the drawer. |
| `EMERGENCY_PURCHASE` | An unplanned urgent purchase paid from the drawer. |
| `OWNER_WITHDRAWAL` | An owner draw retained separately from ordinary operational-expense reporting. |
| `OTHER` | Another unexpected drawer outflow; a nonblank description is mandatory. |

Adding a category is a domain and reporting change; it is not free-form input.

### Drawer reconciliation

For one cash session:

```text
cash sales
  = sum(Sale.totalAmount where Sale.paymentType = CASH)

cash supplier payments
  = sum(supplier payment amount where payment method = CASH)

active cash expenses
  = sum(Expense.amount where Expense.isVoided = false)

expected closing cash
  = openingCash
  + cash sales
  - cash supplier payments
  - active cash expenses

cash variance
  = actual counted closingCash
  - expected closing cash
```

All terms are restricted to records linked to that cash session. The calculated expected closing cash and variance are rounded to scale four using `HALF_UP` at the reconciliation boundary.

`QRIS` sales, `QRIS` supplier payments, and `BANK_TRANSFER` supplier payments are excluded from drawer reconciliation. A voided expense contributes zero. Release 1 has no approved miscellaneous drawer adjustment type; a correction must use an approved auditable source and must not be hidden by editing `openingCash`, `closingCash`, a sale, a payment, or an expense amount.

## Entity mutability matrix

This matrix defines only mutability needed by the approved contract. Fields not mentioned here are not implicitly approved as mutable.

| Entity or record | Allowed mutation | Immutable boundary | Correction path |
|---|---|---|---|
| `Item` | Ordinary master-data maintenance is outside this contract's mutability rules. `baseUnitOfMeasure` and `fractionalQuantityAllowed` may change only while the item has no movements. | Both measurement fields become immutable when the first `StockMovement` exists. `stockStore` and `stockWarehouse` are never directly maintained as master data. | Stock changes use `StockMovementService`. UOM or fractional-policy conversion is not a correction path in Release 1. |
| `StockMovement` | Created once as part of a stock transaction. | Item, location, direction, quantity, source, and before/after balances are immutable after recording. | Record a compensating/correction movement through `StockMovementService`; retain the original. |
| `Sale` / `SaleItem` | Created as one sale transaction and linked to the open `CashSession`. | The session link, payment method, lines, quantities, unit-price snapshots, and calculated amounts are transaction facts after recording. | Sale void/refund behavior is unresolved; do not edit a recorded sale to simulate it. |
| `GoodsReceipt` / `GoodsReceiptItem` | Created as one receipt transaction with stock movements and payable impact. | Supplier, receipt lines, quantities, purchase-price snapshots, totals, and resulting movements are transaction facts after recording. | Receipt cancellation/return behavior is unresolved; do not edit a recorded receipt to simulate it. |
| Supplier payment record | Recorded with amount, method, payable application, and cash-session link when it is a drawer-cash payment. | Those financial facts are immutable after recording. | Payment reversal/void behavior is unresolved; do not delete or overwrite a recorded payment. |
| `Supplier` | Supplier master data may be maintained independently of transaction facts. | Historical receipts and payments retain their transaction identity; the supplier's payable is not a directly editable balance. | Correct source receipts/payments through an approved audit path, not by overwriting debt. |
| `CashSession` | An `OPEN` session may receive eligible sales and drawer-affecting records. Closing sets actual `closingCash`, `closedAt`, and `CLOSED`. | `openingCash` and `openedAt` are opening facts. A `CLOSED` session rejects new drawer-affecting records. | Reopen and post-close correction behavior is unresolved. |
| `Expense` | Created in an open session. It may gain void metadata through the approved void/reversal path. | Original amount, category, description, session, and audit data remain present. It is never deleted. | Mark voided with a reason so its net drawer effect is zero; post-close handling is unresolved. |

## Explicit Release 1 exclusions

The following are outside Release 1:

- Package, pack-size, or alternate-UOM conversion, including ROLL-to-METER.
- Conversion ratios, UOM hierarchies, and automatic quantity conversion.
- Stock locations other than `STORE` and `WAREHOUSE`.
- Negative inventory, back-ordering against unavailable stock, or using one location's balance to cover another location.
- Customer credit, customer debt, accounts receivable, or reusing supplier payable concepts for customers.
- Supplier payment methods other than `CASH`, `BANK_TRANSFER`, and `QRIS`.
- Sale payment methods other than the existing `CASH` and `QRIS` scope.
- More than one concurrently open drawer/cash session.
- React Native. Release 1 integrates with the existing React web app.
- Hibernate-generated schema or any non-Flyway schema mutation.

Unresolved correction and allocation behaviors listed in this document are not approved features merely because they are named.

## Current repository alignment observations

These observations describe the repository at review time; they are not additional product decisions.

- `bloom-app-boot/src/main/resources/application.properties` currently uses `spring.jpa.hibernate.ddl-auto=none`, not the Release 1 target `validate`.
- Flyway is enabled and migrations are located under `bloom-app-boot/src/main/resources/migration`.
- `V1__init-table.sql` currently mixes `DOUBLE PRECISION`, `NUMERIC(19,2)`, and integer quantities; it also defines a single `items.stock_quantity`.
- The Java model already uses `stockStore` and `stockWarehouse` in `Item`, and `StockLocation` already contains exactly `STORE` and `WAREHOUSE`.
- Current stock fields, movement quantities, sale quantities, goods-receipt quantities, and adjustment quantities are Java integer types and require later alignment to `BigDecimal`.
- `StockMovementService` already centralizes sale, goods-receipt, and adjustment movements and prevents a negative result, but its interface and implementation currently use integer quantities.
- Current item create/update DTOs allow stock balances to enter through item master-data requests. That conflicts with opening-balance movements and the rule that all stock mutation goes through `StockMovementService`.
- The current Flyway baseline does not yet align with mapped columns such as the two stock locations and movement before/after/location fields, and does not define all mapped supplier, cash-session, and expense structures.
- `GoodsReceipt`, `GoodsReceiptItem`, and `Supplier` exist in the domain. Supplier-payment behavior does not yet have a repository-visible domain model or service.
- `CashSession`, `CashSessionStatus`, `CashSessionRepository`, `Expense`, and `ExpenseCategory` exist, but no repository-visible cash-session or expense application service/controller was found.
- The current sale `PaymentType` contains `CASH` and `QRIS`. `BANK_TRANSFER` is approved for supplier payments, not automatically for sales.
- No React source tree or `package.json` was found in this Maven repository, so the existing React web client's request/response contract could not be verified here.

These gaps require implementation PRs with Flyway migrations, code, validation, and tests. This planning document intentionally changes none of those artifacts.

## Implementation recommendations

The following are recommendations, not approved product features:

1. Enforce the globally open `CashSession` invariant in PostgreSQL as well as in the service layer so concurrent requests cannot open two sessions.
2. Serialize or lock stock updates per item/location, or use an equivalent atomic persistence strategy, so concurrent valid-looking transactions cannot cause lost updates or negative stock.
3. Add database `CHECK` constraints for non-negative cached stock balances and positive movement quantities, while keeping application validation for useful errors.
4. Make `StockMovementService` depend on abstractions rather than injecting `StockMovementServiceImpl` directly from callers.
5. Use separate payment-method types for sales and supplier payments if doing so prevents `BANK_TRANSFER` from leaking into the approved sale methods.
6. Add reconciliation tests that prove QRIS and bank-transfer records have zero drawer effect and that voided expenses are counted exactly once as zero.
7. Add schema-validation startup tests after the alignment migration so CI detects future JPA/Flyway drift.

## Unresolved implementation blockers

The approved contract is sufficient for planning, but these decisions are required before the affected implementation can be completed:

1. The allowed `baseUnitOfMeasure` values and whether they are stored as an enum, controlled code, or another representation.
2. The precise meaning of `Sale.paidAmount` (for example, sale value versus cash tender) and whether a separate change-due value is needed.
3. Supplier-payment allocation across receipts, overpayment handling, and the supplier-payment void/reversal policy.
4. Goods-receipt cancellation/return and sale void/refund lifecycles.
5. A future post-close expense-correction workflow that does not mutate a reconciled session.
6. Cash-session reopen policy and authorization, if reopening is to exist at all.
7. The React web client API contract, because that client is not present in this repository.
