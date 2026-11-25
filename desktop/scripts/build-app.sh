#!/usr/bin/env bash
set -euo pipefail

# Baut Backend, Frontend und das Electron-Paket. Alles bleibt lokal; es wird nichts gehostet.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DESKTOP="$ROOT/desktop"
FRONTEND_TARGET="$DESKTOP/resources/frontend"

echo "==> 1/4 Backend bauen"
(cd "$ROOT/backend" && ./mvnw -B -Dmaven.test.skip=true clean package)

echo "==> 2/4 Frontend bauen"
(cd "$ROOT/frontend" && npm ci && npm run build)

echo "==> 3/4 Next.js-Standalone nach desktop/resources kopieren"
rm -rf "$FRONTEND_TARGET"
mkdir -p "$FRONTEND_TARGET/.next"
cp -a "$ROOT/frontend/.next/standalone/." "$FRONTEND_TARGET/"
cp -a "$ROOT/frontend/.next/static" "$FRONTEND_TARGET/.next/static"
cp -a "$ROOT/frontend/public" "$FRONTEND_TARGET/public"

echo "==> 3b/4 Laufzeit automatisch installieren (JRE, PostgreSQL)"
(cd "$DESKTOP" && npm ci && npm run prepare:resources)

echo "==> 4/4 Electron-Paket bauen"
(cd "$DESKTOP" && npm run package)

echo "Fertig. Installierbare Pakete liegen unter desktop/dist/."
