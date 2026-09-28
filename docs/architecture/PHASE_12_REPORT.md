# PHASE 12 REPORT — CI/CD

## Status

**PASS for local Phase-12 validation after dependency remediation.** The initial
security gate correctly failed; the follow-up updates now pass npm, packaged-Java
and both runtime-image high/critical gates. See
[SECURITY_DEPENDENCY_REMEDIATION.md](SECURITY_DEPENDENCY_REMEDIATION.md). Phase 13 has not started. No commit, push, publish, deployment or GitHub
branch-protection change was performed.

## Initial state and changes

Backend/OpenAPI verification, frontend build and mocked critical browser tests,
SHA-tagged Docker builds and a hardened runtime smoke test were already present.
Missing pieces were complete test/coverage reports, vulnerability scans, an update
policy and a single required gate. Browser tests reused a dev server and the tab
test depended on sleeps; generated test reports were tracked.

- Added Maven caching, timeouts, read-only workflow permissions, concurrency
  cancellation and manual dispatch.
- Added Surefire/JaCoCo and Playwright HTML/JUnit/trace artifacts (14-day retention).
  Coverage can be generated after a failed test run. Test outputs are ignored and
  the two previously tracked generated report files are removed.
- Added strict npm, packaged Java and runtime image scans. High/critical findings
  fail, with no blanket ignore list or ignore-unfixed policy. Anchore scan-action
  is SHA-pinned and Grype is version-pinned; scan reports remain available on failure.
- Added a final `release-gate` that rejects failed, cancelled and skipped predecessor
  jobs. Future publishing must depend on it; no publication pipeline was invented.
- Added weekly Dependabot configuration for Maven, npm, Dockerfiles, Compose and
  Actions, without auto-merge.
- Playwright owns its server and uses the production build in CI. Browser timezone
  and authenticated mock clock are explicit. The category-tab test uses retrying
  UI assertions instead of sleeps and fallback selectors.
- Documented release policy and external branch-protection requirements in
  [CI_RELEASE_GATES.md](CI_RELEASE_GATES.md).

## Validation

- `./mvnw -B clean verify`: **201 passed**, zero failures/errors/skips.
- Frontend API check, 7 unit tests and 3 security tests: passed.
- Frontend lint, TypeScript and production build: passed.
- Critical Chromium suite against production server: **18/18 passed**.
- `actionlint` v1.7.12: passed.
- Release gate expression: accepts all-success; rejects failure, skipped and cancelled.
- `git diff --check`: passed.
- `npm audit --audit-level=high`: passed, **zero vulnerabilities** after updates
  (initially 19 affected packages).
- Packaged Java scan: passed, **zero matches** after Boot/Tomcat updates
  (initially 72 matches).
- Both final runtime-image scans: passed, **zero high/critical** findings. Remaining
  OS matches: backend 118 medium/6 low; frontend 2 medium. No blanket suppressions.
- Both image builds and final Compose smoke: passed, including native image
  conversion and gzip after removing runtime package managers/system-zlib.
- Fresh prod OpenAPI export: unchanged, byte-identical on two ports.
- Full GitHub-hosted workflow has not been executed in this session.

## Roadmap review

Read the new production roadmap (phases 0–23) and documented the overlap and added
scope in [PRODUCTION_ROADMAP_PREVIEW.md](PRODUCTION_ROADMAP_PREVIEW.md). This is not
its full phase-0 audit. The user's roadmap file was left unchanged.

## Remaining risks and next step

The initial dependency blockers were assessed and resolved; remaining OS
medium/low findings are documented for upstream tracking. Phase 13 has not
started automatically. GitHub must run the final
workflow and require `release-gate` in branch protection. Current browser business
flows mock backend responses; a full real-backend recovery/acceptance suite belongs
to the production-readiness assessment. Coverage is reported, not inflated by an
arbitrary percentage gate.
