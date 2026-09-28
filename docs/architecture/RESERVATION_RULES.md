# Reservation Rules

Updated after Phase 7 by the user's explicit payment-bound occupancy requirement.
The original 180-minute stay model is superseded.

## Arrival and Capacity

- PENDING reservations hold configured, active, physically free tables.
- Groups may span multiple tables within one area. Unknown seat counts are never
  guessed; configure actual capacity first.
- There is no planned maximum stay and no automatic release after 180 minutes.
- Conservative planning: one unresolved reservation per table and business date.
  No second turnover on that business date is promised before payment/cancellation/
  no-show. Different future business dates can be reserved, but currently occupied
  tables are excluded from new allocations regardless of date.
- CHECKED_IN table holds are per-table and end only after that table is paid.
- Arrival, not a fictitious whole stay, must fit an opening window. Closed breaks
  reject arrivals; FIXED slots remain relative to opening time.
- Overnight arrivals belong to the opening day's business date. Ambiguous/nonexistent
  DST arrival times are rejected.

## No-shows

The deadline is arrival instant plus exactly 30 elapsed minutes. Before that
deadline, confirmed arrivals may check in (also up to 30 minutes early).
At the deadline, check-in is rejected. PENDING/CONFIRMED become NO_SHOW in the
minute scheduler; allocation already ignores expired holds without waiting for it.
CHECKED_IN reservations never expire because of time.

Legacy 15-minute grace snapshots and maxReservationDurationMinutes no longer
control this policy. Existing DB values are preserved for history; public settings
return graceMinutes=30 and durationMinutes=null. DTO expiry display, mail and
validation share the same instant-based deadline, including DST transitions.

## Check-in and Billing

Check-in is atomic:
1. Revalidate all assigned tables against physical status and all open/unpaid bills.
2. Open one OPEN, unpaid table bill per assigned table, linked to the reservation.
3. Mark all tables OCCUPIED and the reservation CHECKED_IN.

Any conflict rolls the entire operation back. Repeated/concurrent check-ins do not
duplicate bills or reopen already paid tables. Drinks use the existing inventory-
validated order-item endpoints; no separate reservation-only billing model exists.

Partial payment keeps the parent bill open and the table occupied.
A full close/payment, or split payment consuming every remaining item, closes the
parent bill as paid. The table becomes FREE only if no other open/unpaid bill exists.
Unpaid archiving is not payment: the table remains occupied. Reopen the archived
bill and settle it; opening a replacement bill is rejected.

After every assigned table is settled, the reservation automatically becomes
COMPLETED in the same payment transaction. A paid table is reusable even while
other tables in the group remain unpaid. The manual completion UI is removed;
the compatibility endpoint refuses unpaid or missing table bills.

Walk-in opening respects reservation holds for the current business date.
Passing reservationId to manual bill opening is rejected: use check-in so the
whole group is handled together. Capacity/availability/status edits cannot release
held or unpaid tables. The table UI shows free tables for reuse and exposes archived
unpaid bills rather than silently creating replacement bills.

## Transactions

BookingMutationLock (PostgreSQL transaction advisory lock 7100701) covers reservation,
capacity and all table-bill mutations. Check-in, add/remove, split/close/reopen and
table release cannot race each other. V24 adds a unique partial index allowing only
one OPEN bill per table. Synchronous TableOrderPaid events update the reservation
inside the payment transaction; rollback restores payment, occupancy and lifecycle.

This is a coarse single-venue lock, not a throughput optimization. Independent
inventory adjustments still have their pre-existing concurrency debt (Phase 8).

## Mail and QR

Mail uses immutable AFTER_COMMIT events; rollback sends no notification. SMTP
failure cannot undo a booking, but delivery remains best effort, not a durable outbox.
QR_SCAN_BASE_URL is the frontend origin. Opening a QR page never checks in;
ADMIN/BARCHEF/STAFF explicitly authorize the POST actions. QR tokens are not login
credentials. Old frontend API-path QR links redirect to the scan page.

## Upgrade and Operations

V24 is schema-only: it permits NULL planned end and adds billing indexes. It does
not bulk-rewrite, delete or mark paid any historical reservation/bill. Existing
unpaid bills block allocation even when a legacy stored table status says FREE.

Before deploying, check for pre-existing duplicate OPEN bills:

```sql
select table_id, count(*)
from table_orders
where status = 'OPEN'
group by table_id
having count(*) > 1;
```

Duplicates deliberately stop migration for financial review; no automatic deletion.
No production migration is run by this coding task; migration tests use disposable
PostgreSQL databases.

Configure seat counts and revalidate future pre-V23 reservations without time/table
snapshots. Old CHECKED_IN reservations without linked bills remain blocked; staff
may repeat check-in to create missing bills if the assigned tables are free.
Conflicting legacy bills require explicit reconciliation, never silent adoption.

Existing future bookings made under the former turnover assumption may already
share a table on one business date. Review/reassign them before rollout; check-in
will never displace a current unpaid group. The new code prevents creating more
such turnover promises but deliberately does not rewrite existing bookings.

Physical adjacency, automatic replacement-table selection, public self-cancellation
and durable mail retries remain out of scope.
