#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/backend"
OUT_DIR="${ROOT_DIR}/docs/api"

cd "${BACKEND_DIR}"
./mvnw -q -DskipTests package
VERSION="$(./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout)"
JAR_PATH="target/backend-${VERSION}.jar"
OUT_FILE="${OUT_DIR}/openapi-v${VERSION}.yaml"

java -jar "${JAR_PATH}" > /tmp/fasswerk-backend-openapi.log 2>&1 &
BACKEND_PID=$!

cleanup() {
  kill "${BACKEND_PID}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

for _ in $(seq 1 40); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

curl -fsS http://127.0.0.1:8080/v3/api-docs.yaml -o "${OUT_FILE}"
echo "OpenAPI export erstellt: ${OUT_FILE}"

