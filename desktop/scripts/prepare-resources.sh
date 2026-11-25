#!/usr/bin/env bash
set -euo pipefail

# Prüft die nativen Laufzeiten und lädt fehlende Bestandteile automatisch herunter.
# FW_JRE_DIR / PG_HOME können gesetzt werden, um offline eigene Binaries zu verwenden.
DESKTOP="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT="$(cd "$DESKTOP/.." && pwd)"

echo "==> Backend-JAR"
mkdir -p "$DESKTOP/resources/backend"
jar="${FW_BACKEND_JAR:-}"
if [[ -z "$jar" ]]; then
  jar="$(find "$ROOT/backend/target" -maxdepth 1 -name 'backend-*.jar' -type f ! -name '*.original' -print -quit 2>/dev/null || true)"
fi
if [[ -n "$jar" && -f "$jar" ]]; then
  cp "$jar" "$DESKTOP/resources/backend/backend.jar"
  echo "    $DESKTOP/resources/backend/backend.jar"
else
  echo "    Keine JAR gefunden. Zuerst: cd backend && ./mvnw -Dmaven.test.skip=true package"
fi

cd "$DESKTOP"
npm run compile >/dev/null
node -e "require('./out/resources.js').ensureNativeResources('resources', function (m) { console.log('    ' + m); }).catch(function (e) { console.error(e); process.exit(1); })"
