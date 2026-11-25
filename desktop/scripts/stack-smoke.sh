#!/usr/bin/env bash
set -euo pipefail

# Headless-Prüfung der paketierten Laufzeit: PostgreSQL + Backend + Next.js-BFF.
# Nutzt ausschließlich die unter desktop/resources/ bereitgestellten Bestandteile.
DESKTOP="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RES="$DESKTOP/resources"
DATA="$(mktemp -d /tmp/fasswerk-smoke.XXXXXX)"
chmod 700 "$DATA"

PG_PORT="${FW_SMOKE_PG_PORT:-56432}"
BACKEND_PORT="${FW_SMOKE_BACKEND_PORT:-18180}"
FRONTEND_PORT="${FW_SMOKE_FRONTEND_PORT:-13100}"
DB_PASSWORD="smoke-db-password-1234567890"
JWT_SECRET="smoke-only-signing-key-0123456789abcdef0123456789abcdef"

backend_pid= frontend_pid=
cleanup() {
  result=$?
  trap - EXIT
  for pid in "$frontend_pid" "$backend_pid"; do
    if [[ -n "$pid" ]]; then kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; fi
  done
  if [[ -f "$DATA/pg/PG_VERSION" ]]; then
    "$RES/postgres/bin/pg_ctl" -D "$DATA/pg" -m fast -w stop >/dev/null 2>&1 || true
  fi
  rm -rf "$DATA"
  exit "$result"
}
trap cleanup EXIT

wait_http() {
  local url="$1" label="$2" process="${3:-}"
  for _ in $(seq 1 180); do
    if [[ -n "$process" ]] && ! kill -0 "$process" 2>/dev/null; then
      echo "FEHLER: $label wurde unerwartet beendet. Log:" >&2
      tail -40 "$DATA/logs/$label.log" >&2 2>/dev/null || true
      return 1
    fi
    if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "FEHLER: $label nicht bereit: $url" >&2
  tail -40 "$DATA/logs/$label.log" >&2 2>/dev/null || true
  return 1
}

mkdir -p "$DATA/pg" "$DATA/socket" "$DATA/logs"
if [[ "$(uname -s)" == "Linux" ]]; then
  export LD_LIBRARY_PATH="$RES/postgres/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
elif [[ "$(uname -s)" == "Darwin" ]]; then
  export DYLD_LIBRARY_PATH="$RES/postgres/lib${DYLD_LIBRARY_PATH:+:$DYLD_LIBRARY_PATH}"
else
  export PATH="$RES/postgres/lib:$PATH"
fi

echo "==> Datenbank initialisieren"
printf '%s' "$DB_PASSWORD" > "$DATA/pw"
"$RES/postgres/bin/initdb" -D "$DATA/pg" -U fasswerk \
  --auth-local=trust --auth-host=scram-sha-256 --encoding=UTF8 --no-locale \
  --pwfile "$DATA/pw" > "$DATA/logs/initdb.log" 2>&1
rm -f "$DATA/pw"
"$RES/postgres/bin/pg_ctl" -D "$DATA/pg" -l "$DATA/logs/postgres.log" \
  -o "-p $PG_PORT -h 127.0.0.1 -k \"$DATA/socket\" -c listen_addresses=127.0.0.1" -w start >/dev/null

echo "==> Backend starten"
DB_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/postgres" \
DB_USER=fasswerk DB_PASSWORD="$DB_PASSWORD" JWT_SECRET="$JWT_SECRET" \
SERVER_PORT="$BACKEND_PORT" SPRING_PROFILES_ACTIVE=prod \
DESKTOP_STAFF_LOGIN=true DESKTOP_STAFF_EMAIL=staff@fasswerk.local DESKTOP_STAFF_PASSWORD=fasswerk \
QR_SCAN_BASE_URL="http://127.0.0.1:$FRONTEND_PORT" RESERVATION_MAIL_ENABLED=false \
FLYWAY_CONNECT_RETRIES=30 FLYWAY_CONNECT_RETRIES_INTERVAL=2s \
  "$RES/jre/bin/java" -jar "$RES/backend/backend.jar" > "$DATA/logs/backend.log" 2>&1 &
backend_pid=$!
wait_http "http://127.0.0.1:$BACKEND_PORT/actuator/health" backend "$backend_pid"
echo "    Backend bereit"

echo "==> Oberfläche starten"
cp -a "$RES/frontend" "$DATA/frontend"
(cd "$DATA/frontend" && \
  NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT="$FRONTEND_PORT" HOSTNAME=127.0.0.1 \
  BACKEND_BASE_URL="http://127.0.0.1:$BACKEND_PORT" \
  APP_ORIGIN="http://127.0.0.1:$FRONTEND_PORT" \
    exec node server.js) > "$DATA/logs/frontend.log" 2>&1 &
frontend_pid=$!
wait_http "http://127.0.0.1:$FRONTEND_PORT/login" frontend "$frontend_pid"
echo "    Oberfläche bereit"

echo "==> BFF prüft die Anmeldung gegen das Backend"
login_request() {
  curl -sS -o "$DATA/login-response.json" -w '%{http_code}' \
    -X POST "http://127.0.0.1:$FRONTEND_PORT/api/auth/login" \
    -H 'Content-Type: application/json' -H "Origin: http://127.0.0.1:$FRONTEND_PORT" \
    -H 'Sec-Fetch-Site: same-origin' \
    -d "$1"
}
status="$(login_request '{"email":"staff@fasswerk.local","password":"fasswerk"}')"
if [[ "$status" != "200" ]]; then
  echo "FEHLER: Staff-Login sollte 200 liefern, bekam $status" >&2
  cat "$DATA/login-response.json" >&2
  exit 1
fi
echo "    staff / fasswerk: 200"
status="$(login_request '{"email":"staff@fasswerk.local","password":"falsches-passwort"}')"
if [[ "$status" != "401" ]]; then
  echo "FEHLER: Falsches Passwort sollte 401 liefern, bekam $status" >&2
  cat "$DATA/login-response.json" >&2
  exit 1
fi
echo "    falsches Passwort: 401 wie erwartet"

echo "PASS: lokaler Stack (PostgreSQL + Backend + BFF) ist lauffähig."
