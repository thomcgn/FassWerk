#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"
OUT_DIR="${OPENAPI_OUT_DIR:-${ROOT_DIR}/docs/api}"
BASE_URL="http://127.0.0.1:${SERVER_PORT:-8080}"

cd "${BACKEND_DIR}"
if [[ "${OPENAPI_SKIP_BUILD:-false}" != "true" ]]; then
  ./mvnw -B clean verify
fi
VERSION="$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout)"
JAR_PATH="target/backend-${VERSION}.jar"
OUT_FILE="${OPENAPI_OUT_FILE:-${OUT_DIR}/openapi-v${VERSION}.yaml}"
LOG_FILE="${BACKEND_DIR}/target/openapi-backend.log"
mkdir -p "$(dirname "${OUT_FILE}")"
TEMP_FILE="$(mktemp "${OUT_FILE}.XXXXXX")"
BACKEND_PID=""

cleanup() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" 2>/dev/null || true
  fi
  rm -f "${TEMP_FILE}"
}
trap cleanup EXIT

fail() {
  echo "$1" >&2
  if [[ -f "${LOG_FILE}" ]]; then
    tail -n 100 "${LOG_FILE}" >&2
  fi
  exit 1
}

java -jar "${JAR_PATH}" > "${LOG_FILE}" 2>&1 &
BACKEND_PID=$!
READY=false
STARTUP_TIMEOUT="${OPENAPI_STARTUP_TIMEOUT_SECONDS:-120}"
[[ "$STARTUP_TIMEOUT" =~ ^[1-9][0-9]*$ ]] || fail "OPENAPI_STARTUP_TIMEOUT_SECONDS must be a positive integer."
DEADLINE=$((SECONDS + STARTUP_TIMEOUT))
while (( SECONDS < DEADLINE )); do
  kill -0 "${BACKEND_PID}" 2>/dev/null || fail "Backend exited before OpenAPI export."
  if curl -fsS --connect-timeout 1 --max-time 2 "${BASE_URL}/actuator/health" >/dev/null 2>&1; then
    READY=true
    break
  fi
  sleep 1
done
[[ "${READY}" == "true" ]] || fail "Backend was not healthy within ${STARTUP_TIMEOUT} seconds."

curl -fsS --connect-timeout 2 --max-time 20 "${BASE_URL}/v3/api-docs.yaml" -o "${TEMP_FILE}" \
  || fail "OpenAPI download failed."
grep -q '^openapi:' "${TEMP_FILE}" || fail "Response is not an OpenAPI YAML document."
chmod 0644 "${TEMP_FILE}"
mv "${TEMP_FILE}" "${OUT_FILE}"
if [[ -n "${GITHUB_ENV:-}" ]]; then
  echo "OPENAPI_FILE=${OUT_FILE}" >> "${GITHUB_ENV}"
fi
echo "OpenAPI export erstellt: ${OUT_FILE}"
