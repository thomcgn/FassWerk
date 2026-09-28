#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

: "${FASSWERK_IMAGE_TAG:?Set FASSWERK_IMAGE_TAG to the image tag under test}"
: "${DB_USER:?Set DB_USER for the disposable smoke database}"
: "${DB_PASSWORD:?Set DB_PASSWORD for the disposable smoke database}"
: "${JWT_SECRET:?Set JWT_SECRET for the disposable smoke backend}"

# Always use a fresh project: cleanup must never remove an existing deployment.
export COMPOSE_PROJECT_NAME="fasswerk-smoke-$(date +%s)-$$"
smoke_override="$(mktemp)"
cat > "${smoke_override}" <<'YAML'
services:
  postgres:
    ports: !reset []
  backend:
    ports: !override
      - "127.0.0.1::8080"
  frontend:
    ports: !override
      - "127.0.0.1::3000"
YAML
# Explicit files ignore local override files and private .env settings.
export COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml:${smoke_override}"
export COMPOSE_ENV_FILES=/dev/null
export COMPOSE_DISABLE_ENV_FILE=true
export BOOTSTRAP_ADMIN_EMAIL= BOOTSTRAP_ADMIN_PASSWORD=


cleanup() {
  status=$?
  if (( status != 0 )); then
    docker compose ps >&2 || true
    docker compose logs --no-color --tail=200 >&2 || true
  fi
  docker compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  rm -f "${smoke_override}"
  exit "${status}"
}
trap cleanup EXIT

docker compose config --quiet
docker compose up --detach --no-build --wait --wait-timeout 180

backend_id="$(docker compose ps --quiet backend)"
frontend_id="$(docker compose ps --quiet frontend)"

for service in backend frontend; do
  container_id="$(docker compose ps --quiet "${service}")"
  user="$(docker inspect --format '{{.Config.User}}' "${container_id}")"
  readonly="$(docker inspect --format '{{.HostConfig.ReadonlyRootfs}}' "${container_id}")"
  security_opts="$(docker inspect --format '{{json .HostConfig.SecurityOpt}}' "${container_id}")"
  cap_drop="$(docker inspect --format '{{json .HostConfig.CapDrop}}' "${container_id}")"

  [[ -n "${user}" && "${user}" != "0" && "${user}" != "root" ]]
  [[ "$(docker exec "${container_id}" id -u)" != "0" ]]
  [[ "${readonly}" == "true" ]]
  [[ "${security_opts}" == *"no-new-privileges"* ]]
  [[ "${cap_drop}" == *"ALL"* ]]

  docker exec "${container_id}" sh -c \
    '! touch /app/read-only-probe 2>/dev/null && touch /tmp/writable-probe && rm /tmp/writable-probe'
done

docker exec "${frontend_id}" sh -c 'touch /app/.next/cache/writable-probe && rm /app/.next/cache/writable-probe'
# Verify the native runtime paths after removing package managers/system zlib.
docker exec "${frontend_id}" sh -c '! command -v npm && ! command -v apk'
docker exec "${frontend_id}" node -e '
  const zlib = require("node:zlib");
  const input = Buffer.from("FassWerk runtime smoke");
  if (!zlib.gunzipSync(zlib.gzipSync(input)).equals(input)) process.exit(1);
  const sharp = require("sharp");
  sharp({ create: { width: 2, height: 2, channels: 3, background: "#123456" } })
    .avif().toBuffer()
    .then(data => sharp(data).png().toBuffer())
    .then(data => { if (!data.length) process.exit(1); })
    .catch(error => { console.error(error); process.exit(1); });
'

backend_address="$(docker compose port backend 8080)"
frontend_address="$(docker compose port frontend 3000)"
curl --fail --silent --show-error --max-time 5 "http://${backend_address}/actuator/health" >/dev/null
curl --fail --silent --show-error --max-time 5 "http://${frontend_address}/" >/dev/null

[[ "$(docker inspect --format '{{.State.Health.Status}}' "${backend_id}")" == "healthy" ]]
[[ "$(docker inspect --format '{{.State.Health.Status}}' "${frontend_id}")" == "healthy" ]]

docker compose stop --timeout 30 frontend backend
[[ "$(docker inspect --format '{{.State.OOMKilled}}' "${backend_id}")" == "false" ]]
[[ "$(docker inspect --format '{{.State.OOMKilled}}' "${frontend_id}")" == "false" ]]

for container_id in "${backend_id}" "${frontend_id}"; do
  exit_code="$(docker inspect --format '{{.State.ExitCode}}' "${container_id}")"
  [[ "${exit_code}" == "0" || "${exit_code}" == "143" ]]
done
printf 'Container smoke test passed.\n'
