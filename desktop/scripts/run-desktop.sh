#!/usr/bin/env bash
set -euo pipefail

# Ein Befehl für den lokalen Start: baut fehlende Bestandteile, installiert die
# nativen Laufzeiten und startet danach Electron mit Ladebildschirm.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DESKTOP="$ROOT/desktop"
JAR="$(find "$ROOT/backend/target" -maxdepth 1 -name 'backend-*.jar' -type f ! -name '*.original' -print -quit 2>/dev/null || true)"
SERVER_JS="$ROOT/frontend/.next/standalone/server.js"

if [[ ! -f "$JAR" ]]; then
  echo "==> Backend wird gebaut"
  (cd "$ROOT/backend" && ./mvnw -B -Dmaven.test.skip=true package)
fi

if [[ ! -f "$SERVER_JS" ]]; then
  echo "==> Frontend wird gebaut"
  (cd "$ROOT/frontend" && npm ci && npm run build)
fi

if [[ ! -d "$DESKTOP/node_modules" ]]; then
  echo "==> Electron-Abhängigkeiten werden installiert"
  (cd "$DESKTOP" && npm ci)
fi

echo "==> Laufzeit wird geprüft / installiert"
(cd "$DESKTOP" && npm run prepare:resources)

echo "==> FassWerk wird gestartet"
(cd "$DESKTOP" && npm run dev)
