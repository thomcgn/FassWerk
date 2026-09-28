# Reservation Payment Follow-up

Historical checkpoint: the later deckel correction allows unpaid archiving to
release a table while preserving debt. See RESERVATION_RULES.md and BILLING_INVENTORY_RULES.md.

## Scope

Explicit correction to Phase 7 requested by the user, not a full Phase 8 rollout.
Existing uncommitted work from earlier phases was preserved.

- No fixed 180-minute stay limit.
- No-show after 30 minutes without check-in.
- Group check-in opens one directly usable table bill per assigned table.
- Full payment, not elapsed time or manual completion, releases each table.
- A group is completed automatically once every assigned table is settled.

## Changes

ReservationRules now validates arrival windows and provides the authoritative
30-minute instant deadline. Unknown capacities still require operator configuration.
Before arrival, conservative same-business-date holds avoid promising an unknown
second turnover. Currently occupied tables are excluded from new allocations.

ReservationBilling bridges into billing without invoking payment after commit.
Check-in and automatic bill creation share one transaction; conflicting tables
roll back the whole group. Repeated/concurrent check-ins do not duplicate bills.
Billing mutations share the reservation advisory lock, including item edits and
split payments. Full settlement emits an in-transaction TableOrderPaid event.
Partial or unpaid bills remain blockers. Paid tables can be reused before the rest
of the group is settled; manual completion cannot bypass unpaid bills.

The frontend removes fixed-duration copy and manual completion. Free tables remain
visible for reuse. Selecting an archived unpaid table loads its existing bill;
staff can reopen/pay it rather than opening a replacement.

## Migration

V24 is schema-only: allow an unknown planned end and add a unique partial OPEN-bill
index plus reservation lookup index. No historical records or configuration values
are bulk-rewritten. Old duration/grace values no longer drive current policy.

An initial proposal to normalize existing data was rejected by the safety review.
The implemented alternative preserves data and applies the new rules in application
code. No production migration, deployment, commit or push was performed.
See RESERVATION_RULES.md for duplicate-bill prechecks and legacy booking review.

## Tests and Validation

The initial 29 targeted tests passed. The first complete backend run passed 182
tests. A final run additionally includes a DST deadline-display regression.
**PASS for this follow-up.**

- Backend `./mvnw -B clean verify`: 183 tests, 0 failures/errors/skips, packaged
  artifact and matching JaCoCo report generated.
- Flyway fresh PostgreSQL (17 migrations), upgrade preservation and duplicate-bill
  failure safety passed. OpenAPI JSON/YAML export passed.
- Frontend production build/typecheck passed.
- Lint: 0 errors, 5 existing warnings.
- Native security tests: 3 passed.
- Critical Playwright: 18 passed (13 mocked business UI cases, 5 real BFF security
  cases). The new unpaid-archive test initially targeted the background action
  during dialog opening; adding an accessible dialog label and scoping the locator
  corrected the test without forcing clicks. Final full suite passed.
- `git diff --check`: passed after preserving and restoring generated reports.

Evidence: `/tmp/fasswerk-payment-verify-final.log`,
`/tmp/fasswerk-payment-build-final.log`, `/tmp/fasswerk-payment-lint-final.log`,
`/tmp/fasswerk-payment-security.log`, `/tmp/fasswerk-payment-e2e-final.log`.
Browser artifacts: `/tmp/fasswerk-payment-playwright-first` (failed attempt),
`/tmp/fasswerk-payment-playwright-final` (successful final run).
Tracked test artifacts were restored only after verifying their phase-start hash.

## Remaining Risks

- Existing duplicate OPEN bills deliberately block migration for financial review.
- Legacy future bookings may already promise multiple turnovers on the same table.
  Review/reassign those bookings; existing occupancy is never displaced on arrival.
- Existing CHECKED_IN groups without linked bills remain blocked until staff repeat
  check-in with free tables or reconcile conflicting legacy records.
- Same-area grouping is not an adjacency guarantee; seat counts must be configured.
- Global locking limits throughput; independent inventory adjustments retain their
  known lost-update issue. This change is not a claim that Phase 8 is complete.
- After-commit mail remains best effort; existing dependency vulnerabilities and
  five unrelated frontend lint warnings remain.
- Business browser flows are mocked; database/HTTP behavior is tested separately.

## Changed Files

- `backend/src/main/java/org/thomcgn/backend/billing/application/ReservationBillingAdapter.java`
- `backend/src/main/java/org/thomcgn/backend/billing/application/TableOrderPaid.java`
- `backend/src/main/java/org/thomcgn/backend/billing/repository/TableOrderRepository.java`
- `backend/src/main/java/org/thomcgn/backend/billing/service/TableOrderService.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/application/ReservationBilling.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/application/ReservationRules.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/application/ReservationTableUsage.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/service/ReservationMapper.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/service/ReservationService.java`
- `backend/src/main/java/org/thomcgn/backend/table/service/TableService.java`
- `backend/src/main/resources/db/migration/V24__reservation_payment_occupancy.sql`
- `backend/src/test/java/org/thomcgn/backend/auth/LegacySeedMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/BarchefRoleMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/ConcurrentWritesCharacterizationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/LegacyTableTextMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/PostgresSchemaIntegrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/ReservationCapacityMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/ReservationPaymentMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/reservation/ReservationLifecycleIntegrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/reservation/ReservationRulesTest.java`
- `docs/architecture/DOMAIN_MAP.md`
- `docs/architecture/PHASE_7_REPORT.md`
- `docs/architecture/RESERVATION_PAYMENT_REPORT.md`
- `docs/architecture/RESERVATION_RULES.md`
- `docs/architecture/TECH_DEBT.md`
- `docs/configuration.md`
- `docs/testing.md`
- `frontend/app/bookings/bookings-client.tsx`
- `frontend/app/table-billing/table-billing-client.tsx`
- `frontend/components/full-calendar-bookings.tsx`
- `frontend/components/table-detail-modal.tsx`
- `frontend/e2e/reservation-lifecycle.spec.ts`
- `frontend/e2e/support/mock-api.ts`
- `frontend/e2e/table-billing-split-payment.spec.ts`
- `frontend/types/api.ts`
