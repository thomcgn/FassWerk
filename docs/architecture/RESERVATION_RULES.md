# Reservation Rules

Updated after Phase 7: check-in opens table bills; payment OR explicitly archiving
an unpaid deckel ends table occupancy. Debt and physical occupancy are separate.
The original 180-minute stay model is superseded.

## Arrival and Capacity

- PENDING reservations hold configured, active, physically free tables.
- Groups may span multiple tables within one area. Unknown seat counts are never
  guessed; configure actual capacity first.
- There is no planned maximum stay and no automatic release after 180 minutes.
- Conservative planning: one unresolved reservation per table and business date.
  No second turnover on that business date is promised before payment/archiving/cancellation/
  no-show. Different future business dates can be reserved, but currently occupied
  tables are excluded from new allocations regardless of date.
- CHECKED_IN table holds are per-table and end when its bill is paid or explicitly archived.
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
1. Revalidate all assigned tables against physical status and all OPEN bills.
2. Open one OPEN, unpaid table bill per assigned table, linked to the reservation.
3. Mark all tables OCCUPIED and the reservation CHECKED_IN.

Any conflict rolls the entire operation back. Repeated/concurrent check-ins do not
duplicate bills or reopen already paid/archived tables. Drinks use the existing inventory-
validated order-item endpoints; no separate reservation-only billing model exists.

Partial payment on an OPEN bill keeps the table occupied. Full payment or a
split payment consuming the remaining items closes the bill with paid=true.
Explicit unpaid archiving (leaving a deckel) closes it with paid=false, preserves
the entire remaining debt/items and frees the table. Only OPEN bills hold tables.

After every assigned table bill is CLOSED, the reservation becomes COMPLETED in
the same transaction. This means the visit has ended, not that all debts are paid.
Individual paid/archived tables are reusable while other group tables remain OPEN.
The manual completion endpoint still refuses OPEN or missing bills.

Selecting a free table starts a separate new bill rather than reloading an old debt.
Archived debts remain explicitly accessible in the archive. Paying a new group's
bill does not pay, merge or delete an earlier deckel. Reopening an old deckel
requires an active table without a current OPEN bill or a conflicting reservation
hold; it occupies that table again. It cannot displace a new group.

Walk-in opening respects reservation holds for the current business date.
Passing reservationId to manual bill opening is rejected: use group check-in.
Capacity/availability/status edits cannot release tables held by an OPEN bill.

## Transactions

BookingMutationLock (PostgreSQL transaction advisory lock 7100701) covers reservation,
capacity and all table-bill mutations. Check-in, add/remove, split/close/reopen and
table release cannot race each other. V24 adds a unique partial index allowing only
one OPEN bill per table. Synchronous TableVisitEnded events update the reservation
inside the payment/archiving transaction; rollback restores bill, occupancy and lifecycle.

This is a coarse single-venue lock, not a throughput optimization. Phase 8 now
serializes independent inventory adjustments with row locks; global billing-lock
scalability remains documented in `BILLING_INVENTORY_RULES.md`.

## Mail and QR

Mail uses immutable AFTER_COMMIT events; rollback sends no notification. SMTP
failure cannot undo a booking, but delivery remains best effort, not a durable outbox.
QR_SCAN_BASE_URL is the frontend origin. Opening a QR page never checks in;
ADMIN/BARCHEF/STAFF explicitly authorize the POST actions. QR tokens are not login
credentials. Old frontend API-path QR links redirect to the scan page.

## Upgrade and Operations

V24 is schema-only: it permits NULL planned end and adds billing indexes. It does
not bulk-rewrite, delete or mark paid any historical reservation/bill. OPEN bills block allocation even when a legacy stored table status says FREE.
CLOSED unpaid bills do not block allocation.

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
will never displace a currently seated group. The new code prevents creating more
such turnover promises but deliberately does not rewrite existing bookings.

Physical adjacency, automatic replacement-table selection, public self-cancellation
and durable mail retries remain out of scope.


### Previously Archived Deckels

This correction requires no new schema migration and does not bulk-rewrite
historical data. A table stored as OCCUPIED by the previous archive behavior
requires explicit reconciliation: after confirming no current group/OPEN bill
uses it, staff can reopen and archive that deckel again. This applies the new
release transaction while retaining the debt and completing its old visit.
