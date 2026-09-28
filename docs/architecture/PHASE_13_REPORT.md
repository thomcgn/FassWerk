# PHASE 13 REPORT — Observability

## Status

**PASS for local Phase-13 validation.** All required local gates passed. No
commit, push or deployment was performed; this phase's GitHub run remains pending.
The next phase is Phase 14 (cleanup).

## Scope

Phase 12 was accepted after the user reported CI #35 passed. This phase follows
`FASSWERK_CODEX_REFACTOR.md`; it does not start the separate production-readiness
roadmap or Phase 14 cleanup.

## Findings and changes

- Actuator exposed Prometheus in configuration, but its registry was missing.
  Added `micrometer-registry-prometheus` using Spring Boot dependency management.
- Added native ECS JSON stdout logging for `prod`. Existing validated request IDs
  remain in response headers/error envelopes and now appear in structured logs.
- Failed requests record status and the matched route template; unmatched routes
  use a constant. Query strings and QR-token path values are never fallback labels.
- Previously silent unhandled API errors, framework 5xx and authentication-store
  failures now log bounded cause types and application code locations. Database
  conflicts log at WARN, server errors at ERROR. Exception messages, SQL/payloads
  and raw Throwable dumps are excluded from these diagnostics.
- Hibernate 7's JDBC-error logger can expose rejected database values. In `prod`
  it is replaced by the safe advice diagnostic and HTTP status metrics; other
  framework logging is not globally silenced. Removed raw Reorder exceptions and
  supplier names from application logs.
- Mail outcome counters start at zero and use only `sent`, `failed`, `skipped`.
  They expose failures after reservation commit independently of HTTP success.
- Explicitly keep public health component/details hidden; existing ADMIN-only
  access to metrics and Prometheus is retained. Standard HTTP timers/histograms,
  JVM and Hikari meters cover latency, HTTP/reservation error rates and DB pools.
- Replaced the aspirational monitoring baseline with verified endpoint behavior,
  PromQL examples, scrape authentication, safe diagnostics and clear limitations.

## Validation

- Frontend API-type check, 7 unit tests, 3 security tests: passed.
- Frontend lint, TypeScript and production build: passed.
- Critical Chromium E2E against production server, retries disabled: **18/18 passed**.
- Backend `clean verify`: **206 passed**, zero failures/errors/skips; JAR and
  JaCoCo report generated.
- New production-profile HTTP/PostgreSQL coverage: actual Prometheus scrape,
  JVM/Hikari/histogram output, public minimal health, ADMIN-only access,
  correlated safe 500 logs, reservation route-template metrics and real database
  conflict without rejected values. Mail tests cover all three outcomes and
  sensitive SMTP-message exclusion.
- Fresh production-JAR OpenAPI exports on two ports: byte-identical and unchanged
  against the committed contract; generated frontend types unchanged.
- Grype v0.119.0 scan of `backend/target` (including the new registry and its
  transitive dependencies): **zero matches**, high/critical gate passed.
- `git diff --check`: passed.

The initial privacy test detected a local `DEBUG=release` environment variable
which enabled Spring debug logs and exposed raw URLs/exception messages. The prod
integration test explicitly disables debug/trace; validation commands remove the
local variables. The runbook documents that such production overrides are unsafe.

## Operational boundaries

This provides application signals and regression coverage, not a deployed
Prometheus/Grafana/collector or tested alert delivery. Scraping currently requires
an expiring ADMIN access token; a scoped service identity remains future work.
Scheduler heartbeat/missed-run alerting is not claimed. Mail has no retry/outbox
and `sent` is not proof of final delivery. Logs retain code locations/cause types,
not sensitive exception messages. Production logging overrides and infrastructure
logging require their own deployment review.

See [Monitoring and alerting](../operations/monitoring-alerting.md).
