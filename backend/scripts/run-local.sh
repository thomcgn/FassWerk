#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
ENV_EXAMPLE_FILE="$ROOT_DIR/.env.example"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[INFO] Keine .env gefunden. Erstelle Vorlage aus .env.example"
  cp "$ENV_EXAMPLE_FILE" "$ENV_FILE"
  echo "[INFO] Bitte Werte in $ENV_FILE prüfen und erneut starten."
fi

set -a
source "$ENV_FILE"
set +a

SERVER_PORT_EFFECTIVE="${SERVER_PORT:-8080}"

if command -v ss >/dev/null 2>&1; then
  if ss -ltn "( sport = :$SERVER_PORT_EFFECTIVE )" | grep -q LISTEN; then
    echo "[ERROR] Port $SERVER_PORT_EFFECTIVE ist bereits belegt."
    echo "[ERROR] Beende den laufenden Prozess oder setze SERVER_PORT in $ENV_FILE auf einen freien Port."
    exit 1
  fi
elif command -v lsof >/dev/null 2>&1; then
  if lsof -nP -iTCP:"$SERVER_PORT_EFFECTIVE" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "[ERROR] Port $SERVER_PORT_EFFECTIVE ist bereits belegt."
    echo "[ERROR] Beende den laufenden Prozess oder setze SERVER_PORT in $ENV_FILE auf einen freien Port."
    exit 1
  fi
fi

# Parse JDBC URL like jdbc:postgresql://host:port/db
if [[ "${DB_URL:-}" =~ ^jdbc:postgresql://([^:/]+):([0-9]+)/([^?]+) ]]; then
  DB_HOST="${BASH_REMATCH[1]}"
  DB_PORT="${BASH_REMATCH[2]}"
  DB_NAME="${BASH_REMATCH[3]}"

  if command -v psql >/dev/null 2>&1; then
    if ! PGPASSWORD="${DB_PASSWORD:-}" psql \
      -h "$DB_HOST" \
      -p "$DB_PORT" \
      -U "${DB_USER:-}" \
      -d "$DB_NAME" \
      -c "select 1;" >/dev/null 2>&1; then
      echo "[ERROR] Datenbank-Login fehlgeschlagen: DB_USER/DB_PASSWORD oder DB_URL prüfen."
      echo "[ERROR] Getestet gegen: host=$DB_HOST port=$DB_PORT db=$DB_NAME user=${DB_USER:-}"
      exit 1
    fi
  fi
fi

cd "$ROOT_DIR"
exec ./mvnw spring-boot:run

