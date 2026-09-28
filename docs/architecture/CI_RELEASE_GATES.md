# CI and release gates

The `CI` workflow runs for pull requests, pushes to `main` and manual dispatch.
Its token has read-only repository permissions; duplicate runs for the same ref
are cancelled and every job has a timeout. No publishing/deployment job exists.

## Required results

Configure the repository ruleset/branch protection to require **release-gate**.
This repository change cannot configure GitHub's external branch-protection rules.
The gate explicitly requires successful results from all four predecessor jobs;
failure, cancellation and skipped jobs fail the gate. Any future publish job must
use `needs: release-gate`, publish the verified commit/artifact, and must not use
`always()` or `continue-on-error` to bypass it.

- `backend-openapi`: clean PostgreSQL-backed verification, JaCoCo report, canonical
  OpenAPI drift check, generated TypeScript check, packaged Java dependency scan.
- `frontend-dependencies`: `npm audit --audit-level=high` against all locked
  dependencies, including development tools. Registry/scan errors fail the job.
- `frontend-e2e-critical`: API types, unit/security tests, lint, TypeScript,
  production build and critical Chromium tests against that build.
- `container-images`: depends on backend and frontend functional gates, builds
  SHA-tagged images, checks hardened runtime and scans both runtime images.

The Java and image scans use SHA-pinned Anchore scan-action with pinned Grype
v0.119.0. High/critical vulnerabilities fail the scan, including findings without
an available fix. No ignore list or blanket suppression is configured. The npm
job runs independently so its findings are available even when backend tests fail.

## Reports and reproducibility

Surefire XML and JaCoCo HTML/XML, Playwright HTML/JUnit/traces and vulnerability
JSON reports are uploaded even when their producing checks fail, where output
exists. Reports are retained for 14 days; inspect the Actions log if a scanner
fails before producing output. OpenAPI remains available on contract drift.

Maven and npm download caches are enabled; tests and builds still run. Playwright
owns its server (no reuse), uses one worker and the production server in CI, and
has an explicit local origin/timezone. Authenticated browser mocks freeze Date
via `page.clock.setFixedTime`; timers continue to run. Critical tab tests wait for
observable UI state instead of fixed sleeps. Browser flows mock backend APIs;
these are not full real-backend acceptance tests.

Local reproduction after `npm ci`, from `frontend`:

```bash
npm run api:check
npm run test:unit
npm run test:security
npm run lint
npx tsc --noEmit
npm run build
E2E_PRODUCTION=true E2E_FIXED_DATE=2026-03-20 \
E2E_FIXED_TIMESTAMP=2026-03-20T18:00:00.000Z npm run test:e2e:critical
npm audit --audit-level=high
```

## Update policy and security status

Dependabot checks Maven, npm, Dockerfiles, Compose and Actions weekly. Updates
require normal review and all gates; no auto-merge is configured. Scanner binary
versions also require deliberate review, since Dependabot does not update action
input values. Mutable vulnerability databases intentionally surface new findings
without a code change. Pinned base-image digests need regular security refreshes.

The initial audit on 2026-09-28 blocked 19 npm packages and 72 Java advisory
matches. The subsequent [dependency remediation](SECURITY_DEPENDENCY_REMEDIATION.md)
reduced both counts to zero; both runtime images pass the high/critical gate.
Remaining medium/low OS findings stay visible. No severity threshold or exclusion
was used to conceal the original failures. Rescan on every change because new
advisories may invalidate earlier results.

GitHub runner execution, branch protection and registry publication have not been
performed locally. JaCoCo is a report rather than a blanket coverage threshold;
the production roadmap's phase 18 calls for a separate assessment of meaningful
coverage targets for critical domains.

## References

- [Anchore scan-action inputs and failure policy](https://github.com/anchore/scan-action)
- [Playwright server lifecycle](https://playwright.dev/docs/test-webserver)
- [Playwright fixed browser time](https://playwright.dev/docs/clock)
- [Dependabot version updates](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/secure-your-dependencies/configure-version-updates)
