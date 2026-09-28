# PHASE 9 REPORT

## Result

**PASS.** The App Router boundary was reviewed, nontrivial UI calculations were
moved into feature-owned pure models, critical JSON responses are validated at
runtime, and global loading/error fallbacks now exist. The gate is met for the
reviewed reservation, billing, inventory and catalog flows: their components no
longer own the extracted business calculations. Phase 10 was not started. No
commit, push or deployment was performed.

## Initial findings

Three Client Components contained 674-1338 lines and mixed rendering with stock
availability, package conversion, batch validation and price calculation. Fetch
error parsing and unchecked JSON assertions were duplicated. There was no App
Router error boundary or route loading fallback. Five no-unused-vars warnings were
present. DTOs were already centralized in `frontend/types/api.ts`; creating a
second generated or handwritten schema in Phase 9 would have pre-empted Phase 10.

## Implementation

- Added adapted feature boundaries under `frontend/features` for reservation,
  billing, inventory and catalog.
- Extracted sellable catalog selection, archive validation/sorting, stock and crate
  calculations, package/unit narrowing, reservation status partitioning, drink
  batch validation and cent-rounded price adjustment into pure functions.
- Added `frontend/lib/api-client.ts` for consistent backend error messages and
  parser-backed JSON responses. Reservation, billing, inventory and bar-admin load
  paths no longer cast untrusted response bodies to DTOs.
- Added `app/error.tsx` and `app/loading.tsx` with retry/accessibility behavior.
- Removed dead Inventory/Reorder UI state/imports and all five pre-existing lint
  warnings without ignore rules.
- Added seven native Node tests and `npm run test:unit` without a new test framework.
- Reviewed every current `"use client"` boundary. Existing authenticated route
  wrappers remain Server Components; all retained directives require hooks,
  browser APIs, event handlers or browser-only libraries.

Detailed conventions and audit: [FRONTEND_ARCHITECTURE.md](FRONTEND_ARCHITECTURE.md).

## Tests

- `npm run test:unit`: **7/7 passed**.
- `npm run test:security`: **3/3 passed**.
- `npm run lint`: **0 errors, 0 warnings**.
- `npx tsc --noEmit`: passed.
- `npm run build`: passed; all App Router pages and API routes compiled.
- Critical Playwright suite: **18/18 passed** on Chromium.
- `git diff --check`: passed.

Node prints the already known `MODULE_TYPELESS_PACKAGE_JSON` warning for native TS
imports; no warning was suppressed and both native suites pass.

## Remaining risks

- The large screen composers still contain extensive JSX and orchestration. Further
  splitting should follow stable panels/hooks and is not required for the business-
  calculation gate.
- Secondary frontend areas still contain unchecked JSON assertions: shift
  settlement, sessions, sales configuration, consumption/reorder metadata, the
  public menu, navigation auth status and server auth. Phase 10 must choose one
  contract strategy before duplicating more manual parsers.
- `skipLibCheck` remains enabled.
- TD-020 is unchanged: backend persistence ignores part of the numeric sales
  configuration request. A frontend refactor cannot safely repair that contract.
- The known dependency audit findings from Phase 8 were not changed or force-fixed.
- Business Playwright tests still mock domain APIs; backend HTTP/PostgreSQL tests
  provide the independent contract/persistence evidence.

## Next step

Proceed with Phase 10 (API Contract) only after explicit user approval.
