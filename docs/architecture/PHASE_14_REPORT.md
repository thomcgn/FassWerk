# PHASE 14 REPORT — Cleanup

## Status

**PASS for local Phase-14 validation.** The required local gates passed. This
phase's GitHub run remains pending; Phase 15 has not started.

## Scope and evidence

The user reported CI #36 passed and authorized the next phase of
`FASSWERK_CODEX_REFACTOR.md`. The working tree was clean at the start.

| Change | Evidence / preserved behavior |
| --- | --- |
| Remove MapStruct dependency, version, processor, Lombok binding and compiler option | No MapStruct imports, annotations, factory calls or generated sources. `ReservationMapper` is a handwritten Spring component. Lombok processing remains enabled. |
| Remove eight Java imports | Each imported symbol occurred only in its import: seven in `Supplier`, one in `TableOrderService`. |
| Remove five redundant authorization rules | Reservation QR GET and inventory GET already matched earlier rules with the same roles. Three table method rules are covered by the existing all-method table rule with identical roles; intervening rules target other paths. No role/method access expansion. |
| Replace deprecated Spring `Nullable` with JSpecify `Nullable` | JSpecify is already provided by the Spring stack. Optional `BuildProperties` injection and the fallback version remain unchanged. |
| Reuse three array parsers in inventory | The wrappers were byte-for-byte equivalent calls to the billing parsers already imported by inventory. Re-export the existing functions under the same names; no new module dependency. |
| Mark the required positional Next route argument unused | TypeScript's `noUnusedParameters` identified the first argument of the reservation completion handler. Rename to `_request`; retain the framework signature and params position. |
| Correct scheduler comment | The actual cron runs at 05:30 Europe/Berlin, not 02:00 UTC. Scheduling behavior is unchanged. |

No DTO or route had sufficient evidence for removal. Spring/JPA/framework
entrypoints were not treated as dead merely because no direct call appeared.
`shadcn` remains required by `app/globals.css`; QR/PDF dependencies have concrete
runtime imports. Existing migrations, API paths and generated contracts are
preserved. This is bounded cleanup, not the Phase-15 final audit.

The nullability replacement follows the
[Spring migration guidance](https://docs.spring.io/spring-framework/reference/7.0-SNAPSHOT/core/null-safety.html).

## Validation

- Frontend API-type check, 7 unit tests and 3 security tests: passed.
- Frontend lint, TypeScript with `noUnusedLocals` / `noUnusedParameters`, production
  build: passed.
- Backend `clean verify`: **206 passed**, zero failures/errors/skips; JAR and
  JaCoCo report generated. MapStruct processor-option and OpenApiConfig deprecation
  warnings are absent without suppressions.
- Critical Chromium E2E against production server, retries disabled: **18/18 passed**.
- Packaged JAR inspection: MapStruct absent; JSpecify available.
- Fresh production OpenAPI exports on two ports: byte-identical and unchanged
  against the committed contract; generated frontend types unchanged.
- `git diff --check`: passed.

 No new tests mirror trivial
cleanup; existing integration, role matrix, contract and E2E coverage is used.

## Next step

Phase 15: final audit. No commit, push or deployment is part of this phase.
