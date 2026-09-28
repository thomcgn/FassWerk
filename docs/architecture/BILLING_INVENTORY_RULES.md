# Billing and Inventory Rules

Stand: Phase 8, 2026-09-28.

## Money and bills

- Persisted EUR values use `BigDecimal` with scale 2 and `HALF_UP`; no financial
  calculation uses `double` or `float`.
- A line total is the normalized unit price multiplied by its integer quantity.
  A bill total is the database sum of its persisted line totals.
- `OPEN` means mutable and physically occupies the table. `CLOSED + paid=true`
  is settled. `CLOSED + paid=false` is an archived deckel: debt and positions stay,
  while the table is free for a separate visit.
- A split payment creates one immutable paid receipt and reduces the source bill
  in the same transaction. If no source positions remain, the source bill is closed
  and the table is released.
- Drink and variant deletion is retirement (`active=false`), never deletion of
  historical bill positions. Existing prices and revenue therefore remain stable.

## Idempotency

Clients should send `Idempotency-Key` for:

- `POST /api/table-orders/{id}/items`
- `POST /api/table-orders/{id}/split-payment`
- `POST /api/inventory/{id}/adjust`

Keys contain 8-80 characters from letters, digits, `.`, `_`, `:`, and `-`. Repeating
an identical add or split returns the stored result without a second position,
receipt, stock deduction, or revenue. Reusing the key for another command, bill, or
payload returns `409 Conflict`. Manual adjustments use the same rule through the
movement journal. Close, unpaid archive, and reopen are additionally idempotent by
state. Persist keys until the referenced financial history is retired; there is no
time-based cleanup job.

## Inventory and sales

- `total_stock_amount` is authoritative, nonnegative, and stored with four decimal
  places. `content_per_package`, movement amounts, and deducted volume also preserve
  four decimals; one millilitre represented as `0.0010` litre is not rounded away.
- Every deduction, restock, manual adjustment, update, and delete locks the affected
  inventory row. Concurrent decrements cannot lose an update or cross below zero.
- `packages_in_stock` is synchronized from total stock after quantity mutations.
- Position mutation, stock mutation, movement journal, daily sales projection,
  reorder calculation, and idempotency record share one transaction. Any failure
  rolls all of them back.
- A multi-quantity add records the real serving count and volume. Removing a serving
  reverses one serving and its volume for the current business day.
- Weekly aggregation recomputes the complete week from daily rows. Retrying the
  scheduler therefore does not add the same day twice.
- Reorder input is average daily millilitres. It is converted to a weekly amount in
  the inventory item's unit before lead-time, safety-stock, minimum-stock, and
  stockout calculations are applied.

## Deployment and migration

V25 adds `billing_operations`, movement operation keys, four-decimal inventory
columns, and database checks for new negative stock or invalid bill lines. Constraints
are `NOT VALID`: existing historical violations are preserved for explicit review,
while new writes are protected. Before rollout inspect:

```sql
select id, total_stock_amount from inventory_items where total_stock_amount < 0;
select id, quantity, unit_price, total_price, deducted_volume_ml
from table_order_items
where quantity <= 0 or unit_price < 0 or total_price < 0 or deducted_volume_ml < 0;
```

Do not edit older Flyway files and do not auto-delete duplicate financial history.
The V24 duplicate-open-bill precheck and the reservation/deckel rules in
`RESERVATION_RULES.md` continue to apply.

## Known limits

- Billing uses a database-wide advisory mutation lock. It favors correctness over
  throughput and should be scoped before multi-venue/high-volume operation.
- A cancellation is counterbooked against the active business day. Moving individual
  open positions across a manually advanced business day needs a dedicated immutable
  sales ledger before such cross-day edits are allowed operationally.
- Nullable legacy sales aggregates can still exploit PostgreSQL's NULL uniqueness
  semantics. Application-generated sales always carry a variant; legacy null rows
  require explicit cleanup rather than guessed reassignment.
- Idempotency is implemented for add, split, and manual adjustment. Legacy clients
  that omit the header retain at-most-once behavior only within their single HTTP
  attempt.
