#!/usr/bin/env bash
set -euo pipefail
# Only disposable resources; this script never connects to a configured deployment.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${ACCEPTANCE_JAR:?Build with clean verify and set ACCEPTANCE_JAR to the resulting absolute JAR path}"
[[ -f "$ACCEPTANCE_JAR" && -f "$ROOT_DIR/frontend/.next/BUILD_ID" ]]
export ACCEPTANCE_DIRECTORY="$(mktemp -d /tmp/fasswerk-acceptance.XXXXXX)"
chmod 700 "$ACCEPTANCE_DIRECTORY"
container="fasswerk-acceptance-$$-$(date +%s)"
backend_pid= left_pid= right_pid=
stop_apps() {
  for process in "$left_pid" "$right_pid" "$backend_pid"; do
    if [[ -n "$process" ]]; then kill "$process" 2>/dev/null || true; wait "$process" 2>/dev/null || true; fi
  done
  backend_pid= left_pid= right_pid=
}
cleanup() {
  result=$?
  trap - EXIT
  stop_apps
  docker rm -fv "$container" >/dev/null 2>&1 || true
  # Contains disposable session credentials; never retain or print these files.
  rm -rf "$ACCEPTANCE_DIRECTORY"
  exit "$result"
}
trap cleanup EXIT
unset DEBUG TRACE
export DB_USER=acceptance DB_PASSWORD="$(openssl rand -hex 24)" JWT_SECRET="$(openssl rand -hex 48)"
export BOOTSTRAP_ADMIN_EMAIL=acceptance@example.test BOOTSTRAP_ADMIN_PASSWORD="$(openssl rand -hex 24)"
export SERVER_PORT="${ACCEPTANCE_BACKEND_PORT:-18101}" SPRING_PROFILES_ACTIVE=prod
export RESERVATION_TIMEZONE=UTC SALES_BUSINESS_TIMEZONE=UTC RESERVATION_MAIL_ENABLED=false
export ACCEPTANCE_BFF_LEFT="http://127.0.0.1:${ACCEPTANCE_LEFT_PORT:-13101}"
export ACCEPTANCE_BFF_RIGHT="http://127.0.0.1:${ACCEPTANCE_RIGHT_PORT:-13102}"
export BACKEND_BASE_URL="http://127.0.0.1:$SERVER_PORT"
docker run -d --name "$container" -e POSTGRES_USER="$DB_USER" -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -e POSTGRES_DB=acceptance -p 127.0.0.1::5432 \
  postgres@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea >/dev/null
export DB_URL="jdbc:postgresql://$(docker port "$container" 5432)/acceptance"
wait_http() {
  local url="$1" process="$2"
  for ((attempt=0; attempt<90; attempt++)); do
    kill -0 "$process" 2>/dev/null || { echo 'Acceptance service exited; inspect its startup configuration.' >&2; return 1; }
    if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "Acceptance service did not become ready: $url" >&2; return 1
}
start_apps() {
  java -jar "$ACCEPTANCE_JAR" > "$ACCEPTANCE_DIRECTORY/backend.log" 2>&1 & backend_pid=$!
  wait_http "$BACKEND_BASE_URL/actuator/health" "$backend_pid"
  (cd "$ROOT_DIR/frontend" && APP_ORIGIN="$ACCEPTANCE_BFF_LEFT" exec node node_modules/next/dist/bin/next start --hostname 127.0.0.1 --port "${ACCEPTANCE_LEFT_PORT:-13101}") > "$ACCEPTANCE_DIRECTORY/left.log" 2>&1 & left_pid=$!
  (cd "$ROOT_DIR/frontend" && APP_ORIGIN="$ACCEPTANCE_BFF_RIGHT" exec node node_modules/next/dist/bin/next start --hostname 127.0.0.1 --port "${ACCEPTANCE_RIGHT_PORT:-13102}") > "$ACCEPTANCE_DIRECTORY/right.log" 2>&1 & right_pid=$!
  wait_http "$ACCEPTANCE_BFF_LEFT/login" "$left_pid"
  wait_http "$ACCEPTANCE_BFF_RIGHT/login" "$right_pid"
}
start_apps
# Time-independent booking windows only in this newly created acceptance DB.
docker exec "$container" psql -U "$DB_USER" -d acceptance -v ON_ERROR_STOP=1 -c \
  "update opening_hours set open_time='00:00', close_time='23:59', open=true, second_open_time=null, second_close_time=null; update booking_slot_config set slot_duration_minutes=1, booking_interval_mode='FLEXIBLE';" >/dev/null
node "$ROOT_DIR/frontend/scripts/fullstack-acceptance.mjs" before
stop_apps
start_apps
node "$ROOT_DIR/frontend/scripts/fullstack-acceptance.mjs" after
stop_apps
restore_started=$SECONDS
docker exec "$container" pg_dump -U "$DB_USER" -d acceptance -Fc > "$ACCEPTANCE_DIRECTORY/backup.dump"
docker exec "$container" createdb -U "$DB_USER" restored
docker exec -i "$container" pg_restore -U "$DB_USER" -d restored --exit-on-error < "$ACCEPTANCE_DIRECTORY/backup.dump"
# Exact business-row and Flyway-history equality after a real dump/restore.
for table in flyway_schema_history reservations reservation_tables table_orders table_order_items billing_operations inventory_items inventory_movements; do
  original="$(docker exec "$container" psql -U "$DB_USER" -d acceptance -Atc "select md5(coalesce(string_agg(row_to_json(t)::text, ',' order by row_to_json(t)::text), '')) from $table t")"
  restored="$(docker exec "$container" psql -U "$DB_USER" -d restored -Atc "select md5(coalesce(string_agg(row_to_json(t)::text, ',' order by row_to_json(t)::text), '')) from $table t")"
  [[ "$original" == "$restored" ]]
done
printf 'PASS: synthetic PostgreSQL dump/restore and exact row/history comparison (%s seconds); no production RPO/RTO claim.\n' "$((SECONDS-restore_started))"
