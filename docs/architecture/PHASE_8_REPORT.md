# PHASE 8 REPORT

## Result

**PASS.** Financial and stock mutations are deterministic, serialized where needed,
idempotent for retry-prone commands, and transactionally atomic. Phase 9 was not
started. No commit, push, deployment, or production migration was performed.

## Initial findings

The phase began with one reproduced concurrency defect: two committed manual stock
adjustments could overwrite each other. Add and split commands had no stable request
identity; inventory quantities lost one-millilitre precision; tracking/reorder errors
were swallowed after stock mutation. The debt review also confirmed incorrect sales
quantity for multi-item adds, non-idempotent weekly aggregation, mixed daily-ml and
weekly-inventory units, and destructive catalog deletion of historical bill lines.

## Implementation

- Added canonical two-decimal `Money` arithmetic with `HALF_UP` and removed remaining
  `Double` conversions from inventory consumption/safety-factor calculations.
- Added persisted billing operation keys and movement operation keys. Add-item,
  split-payment, and manual adjustment now replay identical requests and reject key
  reuse with changed payload.
- Added pessimistic inventory row locks, nonnegative checks, synchronized package
  counts, and four-decimal quantity/volume persistence.
- Kept split receipt creation, source reduction, table release, stock, journal,
  sales, reorder calculation, and idempotency storage inside their owning transaction.
  Tracking/reorder failures are no longer swallowed.
- Corrected real sales quantities, cancellation counterbooking, retry-safe weekly
  recomputation, and reorder unit/period conversion including minimum stock.
- Replaced destructive drink/variant deletion with retirement so paid bill positions
  and revenue survive catalog changes.
- Preserved the user-approved deckel rule: explicit unpaid archive frees the table,
  keeps debt, and does not let a later visit mutate the old bill.

Detailed invariant and rollout documentation: [BILLING_INVENTORY_RULES.md](BILLING_INVENTORY_RULES.md).

## Migration

V25 adds the `billing_operations` table, optional unique movement operation keys,
quantity precision changes, and checks for positive bill quantities/nonnegative
money, deducted volume, and stock. A V24-to-latest PostgreSQL upgrade test verifies
precision and enforcement. Existing invalid rows are not silently rewritten because
the checks are introduced as `NOT VALID`.

## Tests

- Backend `./mvnw -B clean verify`: **199 tests**, 0 failures/errors/skips; JAR and
  matching JaCoCo report generated.
- Phase-8 PostgreSQL tests cover multiple positions, cent rounding, repeated add and
  split, exact zero, insufficient stock, manual adjustment replay, 1 ml precision,
  concurrent stock decrement, complete rollback, real multi-quantity sales, storno,
  scheduler retry, reorder units, and paid-history preservation.
- Fresh schema and V24 -> V25 migration tests passed on PostgreSQL 16.
- Production-profile OpenAPI export passed against a fresh PostgreSQL 16 instance.
- Frontend `npm ci`, production build, and 3 security tests passed.
- ESLint: 0 errors and 5 pre-existing warnings.
- Critical Playwright suite: **18/18 passed** on Chromium.
- `git diff --check`: passed.

Evidence: `/tmp/fasswerk-phase8-backend-verify-final.log`,
`/tmp/fasswerk-phase8-frontend-build.log`, `/tmp/fasswerk-phase8-lint.log`,
`/tmp/fasswerk-phase8-security.log`, `/tmp/fasswerk-phase8-e2e.log`, and
`/tmp/fasswerk-phase8-openapi.log`.

## Remaining risks

- The global billing advisory lock is correct but coarse.
- Cross-business-day cancellation needs immutable per-position sales attribution;
  same-business-day cancellation is covered now.
- Legacy null-variant aggregate uniqueness remains a documented data-cleanup issue.
- Sales configuration request fields for lookback/safety/lead time are still not all
  persisted (TD-020); this belongs with the Phase-9 configuration UI/contract work.
- Existing frontend dependency audit reports 20 findings (1 critical, 11 high,
  5 moderate, 3 low); no unreviewed `npm audit fix --force` was applied.
- Five existing frontend lint warnings remain. Business E2E uses mocks while backend
  persistence, migration, concurrency, and HTTP contracts are tested separately.

## Next step

Proceed with Phase 9 (Frontend Architecture) only after explicit user approval.
