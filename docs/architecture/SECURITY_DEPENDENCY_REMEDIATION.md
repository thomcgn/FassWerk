# Dependency security remediation

Date: 2026-09-28. Follow-up to the Phase-12 scanner findings.

## Assessment

Scanner matches establish affected package versions, not automatically a reachable
exploit in this application. No advisory suppression or lower severity threshold
is used in this remediation.

- The critical Spring Boot default-chain advisory requires an application without
  its own security configuration and without the Health dependency. FassWerk has
  its own `SecurityFilterChain` with a deny-all fallback in `SecurityConfig` and
  exposes Health. Those stated conditions are not met. The vulnerable dependency
  is nevertheless replaced together with the other Boot-managed security fixes.
  [Vendor advisory](https://spring.io/security/cve-2026-40976).
- Several critical Tomcat findings concern container-managed FORM/DIGEST
  authentication, while this application uses Spring Security/JWT. That limits
  those particular paths, but does not rule out independent HTTP parsing and
  request-handling findings. Upgrade embedded Tomcat rather than exempting it.
  [Tomcat FORM advisory](https://github.com/advisories/GHSA-h3x4-894j-xpx5).
- Next.js is a runtime dependency and `bookings-client.tsx` imports `next/image`.
  The image-optimization advisories therefore deserve priority; actual exploit
  prerequisites depend on image formats and input reachability. No exploit was
  attempted or claimed. Next.js is updated to the security-patched version.
  [Image optimization advisory](https://github.com/advisories/GHSA-2xp9-vwfh-vxw4).
- Jackson, Spring MVC/Security/Data, Micrometer and PostgreSQL are runtime
  dependencies. The Boot BOM supplies compatible patched versions. Build-tool
  findings in the npm lockfile also affect developer/CI environments and are
  updated even if they are absent from the final standalone runtime.

## Changes

- Spring Boot parent: 4.0.4 → 4.0.8, staying within the existing minor line.
- Tomcat: explicit 11.0.25 override; Boot 4.0.8 manages 11.0.24, below the required
  fix for some critical matches. Remove the override when the Boot BOM catches up.
- The updated BOM includes Jackson 2.21.5/3.1.5, Spring Framework 7.0.9,
  Spring Security 7.0.7, Micrometer 1.16.7 and PostgreSQL JDBC 42.7.13.
- Next.js and eslint-config-next: 16.2.1 → 16.3.6, pinned consistently.
- Removed npm/Yarn/corepack and apk-tools/libapk/system-zlib from the final
  frontend runtime. Package installation remains in the build stages. `ldd` shows
  that Node and the musl libvips runtime do not link the removed system library;
  smoke coverage additionally exercises gzip and AVIF-to-PNG conversion.
- Compatible transitive npm fixes applied to the lockfile without `--force`,
  blanket overrides or changing the security gate.

## Verification

- Backend clean verify: 201 tests passed, zero failures/errors/skips.
- npm audit: zero vulnerabilities (previously 19).
- Packaged Java scan: zero matches at all severities (previously 72).
- Frontend API types, 7 unit tests, 3 security tests, lint, TypeScript and production
  build: passed. Critical E2E suite: 18/18 passed.
- Both application image builds passed; backend image has zero high/critical
  findings, with 118 medium and 6 low OS matches.
- Fresh prod-profile OpenAPI exports on two ports: byte-identical and unchanged
  from the committed contract. First attempt exceeded the 40-second startup limit
  during concurrent builds; the isolated rerun passed.
- Initial Compose smoke with both updated applications: passed.
- Final frontend image: zero high/critical findings; 2 medium BusyBox matches
  for CVE-2025-60876, no fixed version listed by the scanner.
- Final Compose smoke: passed, including effective non-root/read-only checks,
  absence of npm/apk, native AVIF-to-PNG conversion, gzip round-trip, HTTP/health
  and graceful shutdown. Disposable resources removed.
- Workflow actionlint, shell syntax and `git diff --check`: passed.

Scanned local image IDs:

- Backend: `sha256:009997e14b8382407ae8ebb505e6a129c611c78802bf8cb0f612d53bbe7f6d9e`
- Frontend: `sha256:ae441543f95628a2af1afd1fcdaa76e6f2a31181cbfa0c7f68d1180118da72b7`

Grype v0.119.0, database built 2026-09-28. Gates remain high/critical with no
exclusions. The initial blockers are resolved; Phase 12 passes local verification.
A GitHub CI run against the committed revision is still required.

## Remaining operating-system findings

Backend image findings are Ubuntu packages, not remaining Java matches. Most are
reported `not-fixed`/`wont-fix`; these remain visible to normal scans. The reported
`coreutils` fix needs special interpretation: installed package metadata describes
`coreutils` 9.5-1ubuntu2+0.0.0~ubuntu25 as a 10-KiB meta package from `coreutils-from`.
Running `cp --version` in the actual image confirms GNU coreutils 9.7, packaged as
9.7-3ubuntu2.1, matching the scanner's fixed version. No vulnerable GNU binary was
established by the meta-package match. No blanket suppression was introduced.
Other medium/low findings remain upstream tracking work; zero high/critical is the
configured release policy, not a claim of zero operating-system advisories.

The current registry digests for `node:22-alpine` and `eclipse-temurin:21-jre`
matched the existing pins at verification time. The original Alpine frontend
runtime nevertheless had vulnerable globally bundled npm packages and a high
zlib finding with no listed fixed Alpine package. An alternative Alpine 3.22
image and a Debian slim candidate were evaluated but also had blocking findings
and were not selected. The existing Alpine digest is retained; its unneeded
package managers and their system-zlib dependency are removed from runtime. GitHub-hosted CI and production deployment remain separate
checks; no production environment was changed.
