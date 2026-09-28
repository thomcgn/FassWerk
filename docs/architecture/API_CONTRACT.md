# API Contract

Stand: Phase 10, 2026-09-28.

## Source of truth

Spring MVC DTOs plus Springdoc annotations are the only authored API contract.
`docs/api/openapi.yaml` is a reproducible snapshot exported from a running prod-
profile backend with PostgreSQL. `frontend/types/generated/api.ts` is generated
from that snapshot by the lockfile-pinned `openapi-typescript` dependency.
The explicit relative server URL `/` keeps exports independent of the local or CI
host and port; it also lets Swagger UI use the serving origin.
`frontend/types/api.ts` contains compatibility aliases, not independently authored
DTO shapes.

The contract intentionally uses OpenAPI 3.0. This preserves explicit `nullable`
metadata in the current Springdoc/export/generator chain. Java `BigDecimal` values
are JSON numbers; the former handwritten frontend string declarations were drift
and have been removed.

## Schema rules

- Every request and response DTO has a schema description.
- Jakarta validation constraints define required request input where applicable.
- Every response record component is explicitly required; nullable values remain
  required properties with `nullable: true`.
- Enums, number formats and validation bounds are emitted by Springdoc.
- Reservation settings use dedicated API DTOs instead of exposing application-
  internal records.
- Runtime parsers from Phase 9 remain trust-boundary validation. They do not define
  a second compile-time schema.

## Change workflow

1. Change the backend DTO/controller and its tests.
2. Run the backend and export `docs/api/openapi.yaml` with
   `backend/scripts/export-openapi.sh`.
3. Run `npm run api:generate` in `frontend`.
4. Adapt consumers and runtime parsers, then run all backend/frontend gates.
5. Review the OpenAPI and generated-type diff as part of the same change.

CI repeats the export against PostgreSQL and rejects a stale committed snapshot.
It then runs `npm run api:check`, which rejects stale generated TypeScript. The
backend OpenAPI integration test additionally checks operation summaries, unique
operation IDs, documented schemas, complete response requiredness, numeric money
and selected nullable fields.

## Scope and limits

OpenAPI describes transport shape, not all business invariants. Authorization,
transactionality, reservation allocation, billing occupancy and inventory rules
remain covered by their dedicated tests and architecture documents. Generated
TypeScript is compile-time protection; untrusted response bodies still require
runtime validation where a feature parser exists.
