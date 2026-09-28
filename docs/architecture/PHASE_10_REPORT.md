# PHASE 10 REPORT

## Result

**PASS.** The backend is now the single authored API-contract source. A canonical
OpenAPI 3.0.1 snapshot is exported from the running prod-profile application,
frontend DTOs are generated reproducibly from it, and CI rejects stale OpenAPI or
TypeScript artifacts. The gate is met: breaking transport changes become visible
in backend tests, the committed contract diff, generation checks or TypeScript
compilation. Phase 11 was not started. No commit, push or deployment was performed.

## Initial findings

Springdoc already exposed the live API and CI uploaded a versioned artifact, but no
specification was committed and the frontend maintained 299 lines of handwritten
DTO fields. Browser mocks repeated those shapes. This had already drifted: Java
`BigDecimal` is serialized as a JSON number while money and inventory amounts were
typed as strings in the frontend. Response requiredness was absent from the export,
nullable semantics were ambiguous under OpenAPI 3.1, several operations lacked
summaries, and reservation settings exposed application-internal records.

## Implementation

- Documented all 53 request/response DTO source files with Springdoc schemas and
  made response requiredness and known nullable fields explicit.
- Added dedicated `OpeningHoursResponse` and `ReservationSettingsResponse` API DTOs;
  the latter preserves the Phase-7 decision: no stay limit and a fixed 30-minute
  no-show grace period.
- Completed operation metadata for the table and report controllers and configured
  OpenAPI 3.0 so the current generator receives deterministic nullable metadata.
- Added `docs/api/openapi.yaml`, currently containing 61 paths, 78 operations and
  52 schemas. All 29 response schemas expose every property as required, with
  nullable properties represented explicitly.
- Added lockfile-pinned `openapi-typescript` generation. `frontend/types/api.ts` now
  provides aliases only; generated fields live in `frontend/types/generated/api.ts`.
- Adapted frontend consumers, runtime parsers and Playwright mocks to the real JSON
  number, requiredness, nullability and enum contracts.
- Extended `OpenApiIntegrationTest` to verify JSON/YAML availability, summaries,
  unique operation IDs, schema descriptions, complete response requiredness,
  numeric totals and selected nullable fields.
- Extended `backend-openapi` CI to export to the canonical path, reject its Git
  drift and run `npm run api:check` after a clean lockfile install.
- Documented ownership and the change workflow in
  [API_CONTRACT.md](API_CONTRACT.md) and [the API README](../api/README.md).

## Tests

- `cd backend && ./mvnw -B clean verify`: **200 tests passed**, including real
  PostgreSQL/Testcontainers and the OpenAPI HTTP contract test.
- Prod-profile OpenAPI export against isolated PostgreSQL: passed; generated
  OpenAPI 3.0.1 has **61 paths, 78 operations and 52 schemas**.
- `npm run api:check`: passed.
- `npx tsc --noEmit`: passed.
- `npm run test:unit`: **7/7 passed**.
- `npm run test:security`: **3/3 passed**.
- `npm run lint`: **0 errors, 0 warnings**.
- `npm run build`: passed; all App Router pages and BFF routes compiled.
- Critical Playwright suite: **18/18 passed** on Chromium.
- `git diff --check`: passed.

The native Node tests retain the documented package-type warning. Playwright also
reports the existing `NO_COLOR`/`FORCE_COLOR` and smooth-scroll notices; no test was
skipped or weakened.

## Remaining risks

- OpenAPI protects transport structure, not authorization, transactionality or all
  business semantics; the dedicated backend suites remain mandatory.
- Runtime parsers cover critical Phase-9 features, while some secondary clients
  still use unchecked JSON assertions. Their compile-time types are now generated,
  but runtime trust-boundary hardening remains useful.
- TD-020 remains open: sales-configuration request fields are documented but some
  are still ignored by backend persistence. Generation makes the shape consistent;
  it does not claim the semantic bug is fixed.
- `skipLibCheck` remains enabled.
- `npm audit` currently reports 19 packages: 1 critical, 10 high, 5 moderate and
  3 low. No unsafe `npm audit fix --force` was applied.
- Operation IDs are verified as present and unique, but Springdoc still derives
  many from Java method names. Renames will correctly appear as contract diffs.

## Next step

Proceed with Phase 11 (Docker Hardening) only after explicit user approval.
