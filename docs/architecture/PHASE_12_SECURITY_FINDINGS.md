# Phase 12 security findings

Original scanner snapshot: 2026-09-28, before remediation.
See [SECURITY_DEPENDENCY_REMEDIATION.md](SECURITY_DEPENDENCY_REMEDIATION.md) for
the assessment, corrected versions and current scan results.
This inventory preserves the initial dependency-path/advisory evidence. Runtime image scans may add OS findings.

## Packaged backend

Grype v0.119.0, database built 2026-09-28; source `backend/target` after clean verify.
72 advisory matches: 7 critical, 26 high, 32 medium, 7 low.

| Package | Version | Severity | Advisories |
| --- | --- | --- | --- |
| jackson-core | 2.21.1 | High | GHSA-r7wm-3cxj-wff9 |
| jackson-core | 3.1.0 | High | GHSA-2m67-wjpj-xhg9, GHSA-r7wm-3cxj-wff9 |
| jackson-databind | 2.21.1 | High | GHSA-j3rv-43j4-c7qm, GHSA-rmj7-2vxq-3g9f |
| jackson-databind | 3.1.0 | High | GHSA-j3rv-43j4-c7qm, GHSA-rmj7-2vxq-3g9f |
| micrometer-core | 1.16.4 | High | GHSA-g3pr-3p32-fp23, GHSA-w737-wx49-qj23 |
| postgresql | 42.7.10 | High | GHSA-98qh-xjc8-98pq, GHSA-j92g-9f8w-j867 |
| spring-boot | 4.0.4 | Critical | GHSA-8v8j-3hxp-93wr |
| spring-boot | 4.0.4 | High | GHSA-wwpq-f5c3-7hvx |
| spring-data-commons | 4.0.4 | High | GHSA-88fw-v6x4-3f58, GHSA-9fw2-h3hf-293r |
| spring-expression | 7.0.6 | High | GHSA-r5w3-xv2f-j59q |
| spring-security-config | 7.0.4 | High | GHSA-4vrc-j85c-598c, GHSA-4wrg-8wpc-h923 |
| spring-webmvc | 7.0.6 | High | GHSA-3chg-m5w7-qfv5, GHSA-x23c-287f-qqv5 |
| tomcat-embed-core | 11.0.18 | Critical | GHSA-5m62-pw8w-7w9f, GHSA-9xv2-5v5q-p794, GHSA-gcx9-497g-6cp6, GHSA-h3x4-894j-xpx5, GHSA-h6fc-48rj-7qqh, GHSA-r29c-68gh-xp6x |
| tomcat-embed-core | 11.0.18 | High | GHSA-563x-q5rq-57qp, GHSA-5mp6-jrq3-r938, GHSA-69cc-cv78-qc8g, GHSA-fv25-8xcx-gqjc, GHSA-gx5v-xp9w-j4cg, GHSA-rv64-5gf8-9qq8, GHSA-x4m4-345f-5h5g |

## Frontend lockfile

npm audit: 19 affected packages, 1 critical, 10 high, 5 moderate, 3 low.
The npm package counts are not directly comparable to Grype advisory-match counts.

| Package | Severity | Audit fix suggestion |
| --- | --- | --- |
| brace-expansion | high | Compatible dependency update available |
| browserslist | high | Compatible dependency update available |
| fast-uri | high | Compatible dependency update available |
| hono | high | Compatible dependency update available |
| ip-address | high | Compatible dependency update available |
| nanoid | high | Compatible dependency update available |
| next | critical | next 16.3.6 |
| path-to-regexp | high | Compatible dependency update available |
| picomatch | high | Compatible dependency update available |
| postcss | high | next 16.3.6 |
| sharp | high | next 16.3.6 |

## Follow-up

Review the exact advisory and dependency path, prioritize runtime critical/high
findings, update compatible versions deliberately, then rerun backend, frontend,
OpenAPI and runtime gates. npm suggests Next.js 16.3.6 for several frontend
findings; that suggestion has not been applied or compatibility-tested here.
Registry advisories and vulnerability databases change, so rescan before deciding.
The above suggestions describe the original snapshot; the linked remediation
report supersedes this original follow-up status.
