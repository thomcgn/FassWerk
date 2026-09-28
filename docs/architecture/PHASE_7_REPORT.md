# PHASE 7 REPORT

Historical completion report. The user subsequently replaced the 180-minute model
with payment-bound occupancy; see RESERVATION_PAYMENT_REPORT.md and the current
RESERVATION_RULES.md. Test totals below describe the original Phase 7 checkpoint.

## Scope and Decisions

Reservation domain only; Phase 8 has not started. Existing uncommitted Phase 6
changes were retained. The file list below is relative to the SHA256 snapshot
taken at Phase 7 start, not relative to HEAD.

User decisions:
- A group may use multiple tables.
- Real table capacities must be configured first; no assumed legacy default.

Implementation assumptions: group tables within one area, use configured maximum
duration as the default stay, and use Europe/Berlin unless explicitly configured.

## Initial Findings and Plan

Creation counted reservations at an identical start time rather than reserving
concrete capacity for the entire stay. Tables lacked seat counts; edits had no
application workflow. No-show processing missed previous dates and cancellation
could change terminal states. Time zone/business date and duration were not saved.
QR URLs pointed at a POST API rather than a navigable staff workflow.

Plan: reproduce defects, centralize temporal/allocation rules, add nullable capacity
and immutable time snapshots through Flyway, serialize booking mutations, expose
thin lifecycle/BFF/UI commands, then test migration, concurrency and browser flows.

## Fixed Problems

- Whole-stay multi-table allocation with explicit capacities and same-area grouping.
- Atomic reallocation on edit, cancellation releasing holds and guarded transitions.
- Cross-instance PostgreSQL serialization preventing duplicate table allocation.
- Overnight business dates, DST validation, opening breaks and persisted deadlines.
- Missed no-shows across dates; check-in restricted to its persisted grace window.
- After-commit mail and usable authenticated QR workflow with explicit confirmation.
- UI displays backend configuration instead of hardcoded hours/guest limit.
- Optional seat fields preserve compatibility with older table-edit clients.

## Tests

Added ReservationRulesTest, ReservationRegressionTest,
ReservationLifecycleIntegrationTest and ReservationCapacityMigrationTest.
Updated migration counts, fresh-schema assertions and the prior concurrent
reservation knownGap into a correctness assertion. Extended the endpoint role
matrix and browser lifecycle suite, including QR denial and capacity entry.

The pre-fix targeted regression run had 3 failures among 4 tests (past creation,
missed previous-day no-shows, terminal cancellation). After fixes, all 20 initial
targeted tests passed. An initial integration context failure caused by mocking
the mail interface was corrected to the concrete sender type. A frontend
TypeScript narrowing error was corrected before the final build.

## Checks and Result

**PASS (Phase 7 scope).**

- Backend `./mvnw -B clean verify`: 173 tests, 0 failures/errors/skips;
  packaged artifact and JaCoCo report generated.
- Flyway: 16 migrations on fresh PostgreSQL, V22 -> V23 legacy upgrade passed.
- OpenAPI JSON and YAML anonymous exports passed.
- Frontend `npm ci`: passed; existing 20 audit findings remain.
- `npm run lint`: 0 errors, 5 existing warnings.
- `npm run build`: production build and TypeScript checks passed.
- `npm run test:security`: 3/3 passed.
- `npm run test:e2e:critical`: 17/17 passed (12 mocked business UI flows,
  5 real BFF security checks).
- `git diff --check`: passed.

Local evidence: `/tmp/fasswerk-phase7-verify-final.log`,
`/tmp/fasswerk-phase7-build-final.log`, `/tmp/fasswerk-phase7-lint-final.log`,
`/tmp/fasswerk-phase7-security-final.log`, `/tmp/fasswerk-phase7-e2e-final.log`.
Browser artifacts were preserved under `/tmp/fasswerk-phase7-playwright-final`;
tracked generated reports were restored only after verifying their phase-start
hash matched HEAD. No commit, push or deployment was performed.

## Open Risks

- Configure real seat counts and revalidate future legacy reservations before rollout.
- Same-area grouping does not guarantee physical adjacency.
- Coarse global lock and unbounded active-reservation queries limit scalability.
- Direct SQL writers must honor the booking lock; no database exclusion constraint.
- Walk-in/billing occupancy coordination remains Phase 8 work.
- Mail is best effort after commit, not durable delivery.
- Old backend-origin printed QR URLs need proxy redirects or reissue.
- npm reports 20 vulnerabilities (1 critical, 11 high, 5 moderate, 3 low);
  five existing unrelated lint warnings remain.
- Browser business tests use mocks; real backend HTTP contracts are tested separately.

## Recommended Next Step

PHASE 8 -- Billing / Ordering / Inventory, only after explicit approval and a
successful Phase 7 gate.

## Changed Files

- `.env.example`
- `backend/src/main/java/org/thomcgn/backend/common/persistence/BookingMutationLock.java`
- `backend/src/main/java/org/thomcgn/backend/config/SecurityConfig.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/api/ReservationController.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/api/dto/CreateReservationRequest.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/api/dto/ReservationResponse.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/application/ReservationRules.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/application/ReservationTableUsage.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/application/ReservationTimeConfiguration.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/domain/Reservation.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/repository/ReservationRepository.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/service/ReservationMailEvent.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/service/ReservationMailService.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/service/ReservationMapper.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/service/ReservationService.java`
- `backend/src/main/java/org/thomcgn/backend/table/api/dto/TableRequest.java`
- `backend/src/main/java/org/thomcgn/backend/table/api/dto/TableResponse.java`
- `backend/src/main/java/org/thomcgn/backend/table/domain/TableEntity.java`
- `backend/src/main/java/org/thomcgn/backend/table/service/TableService.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/db/migration/V23__reservation_intervals_and_table_capacity.sql`
- `backend/src/test/java/org/thomcgn/backend/auth/LegacySeedMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/auth/SecurityHardeningIntegrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/BarchefRoleMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/ConcurrentWritesCharacterizationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/LegacyTableTextMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/PostgresSchemaIntegrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/ReservationCapacityMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/reservation/ReservationLifecycleIntegrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/reservation/ReservationRegressionTest.java`
- `backend/src/test/java/org/thomcgn/backend/reservation/ReservationRulesTest.java`
- `backend/src/test/resources/application-test.yml`
- `docker-compose.yml`
- `docs/architecture/DOMAIN_MAP.md`
- `docs/architecture/PHASE_7_REPORT.md`
- `docs/architecture/RESERVATION_RULES.md`
- `docs/architecture/TECH_DEBT.md`
- `docs/configuration.md`
- `docs/testing.md`
- `frontend/app/api/reservations/[id]/complete/route.ts`
- `frontend/app/api/reservations/[id]/route.ts`
- `frontend/app/api/reservations/scan/[token]/route.ts`
- `frontend/app/api/reservations/settings/route.ts`
- `frontend/app/api/tables/[id]/route.ts`
- `frontend/app/bookings/bookings-client.tsx`
- `frontend/app/bookings/scan/[token]/page.tsx`
- `frontend/app/bookings/scan/[token]/scan-client.tsx`
- `frontend/app/table-billing/table-billing-client.tsx`
- `frontend/components/full-calendar-bookings.tsx`
- `frontend/components/table-capacity-editor.tsx`
- `frontend/e2e/reservation-lifecycle.spec.ts`
- `frontend/e2e/support/mock-api.ts`
- `frontend/e2e/table-billing-prerequisite-flow.spec.ts`
- `frontend/types/api.ts`
