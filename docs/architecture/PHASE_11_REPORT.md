# PHASE 11 REPORT

## Result

Docker hardening is implemented. Both application images build locally and the
isolated Compose runtime smoke test passes. No commit, push, registry publication
or deployment to an existing environment was performed.

## Implementation

- Pinned Maven, JRE, Node and PostgreSQL base images by digest. Application image
  tags are required and CI uses the commit SHA; OCI labels capture version/revision.
- Kept multi-stage builds, reduced build contexts and excluded private environment
  files, generated output and local dependency directories.
- Both application services run as non-root with read-only root filesystems,
  dropped capabilities, no-new-privileges, PID limits and bounded temporary mounts.
  Frontend cache ownership matches its explicit UID/GID 1001.
- Frontend listens on all container interfaces. PostgreSQL and backend host ports
  default to loopback. Fixed container names were removed to allow isolated stacks.
- Added image healthchecks and healthy dependency ordering. Mail contributes to
  backend health only when reservation mail is enabled.
- Added explicit shutdown grace periods, Spring graceful shutdown and JVM exit on
  OOM. Database storage remains persistent.
- Image builds depend on backend/OpenAPI and frontend quality/E2E gates in CI.
  The new runtime gate builds and tests images without publishing them.
- Hardened the smoke script to use a fresh project, random loopback ports and an
  isolated database, ignoring local Compose overrides and `.env` files. Cleanup is
  restricted to that project. Testcontainers uses a digest-only PostgreSQL reference
  because its image-name parser rejects the combined tag/digest form.
- Updated deployment, rollback and local verification instructions.

## Validation

- Backend Docker image build: passed.
- Frontend Docker image build, including Next.js and TypeScript: passed.
- `scripts/container-smoke.sh`: passed. All three services healthy; HTTP access,
  effective non-root UIDs, read-only root filesystems, writable tmp/cache paths,
  capabilities, no-new-privileges and shutdown exit status checked. Disposable
  resources removed successfully.
- Shell syntax and `git diff --check`: passed.
- `./mvnw -B clean verify`: passed, 200 tests, zero failures/errors/skips.

The first local verification attempts exposed the incompatible Testcontainers image
reference, the Docker Desktop daemon-side socket mapping requirement, and stale
IDE-generated class files. The image reference is fixed in source; the local socket
setup is documented, and final verification uses `clean verify`.

## Remaining limitations

- GitHub-hosted CI has not been executed in this session. Local Docker builds do
  not themselves enforce all predecessor test gates.
- A required SHA-style tag is an operational convention, not registry immutability
  enforcement. Protect release tags and record deployed image digests.
- Existing dependency vulnerabilities remain separate work. Pinning prevents
  silent base-image drift but requires deliberate security updates.
- Healthchecks cover process/HTTP/dependency health, not complete business flows;
  Compose does not restart containers solely because they are unhealthy.
- Rollback must account for Flyway compatibility and verified backups. An image
  rollback does not roll back database migrations.
- Release publishing, vulnerability enforcement and further CI/release work remain
  outside this phase.
