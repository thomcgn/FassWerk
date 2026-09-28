# API-Spezifikation (OpenAPI)

`openapi.yaml` ist der versionierte, aus dem laufenden Backend exportierte
HTTP-Vertrag. Die Datei wird nicht von Hand gepflegt. Springdoc und die annotierten
Backend-DTOs bleiben die einzige fachliche Quelle.

## Lokal aktualisieren

Der Export benoetigt PostgreSQL und dieselben verpflichtenden prod-Einstellungen
wie der Backend-Start:

```bash
OPENAPI_OUT_FILE="$PWD/docs/api/openapi.yaml" \
  ./backend/scripts/export-openapi.sh
cd frontend
npm run api:generate
```

Ohne `OPENAPI_OUT_FILE` erzeugt das Skript weiterhin ein versioniertes Artefakt
`docs/api/openapi-v<version>.yaml`, beispielsweise fuer einen Release-Download.

## Vertrag pruefen

```bash
cd frontend
npm run api:check
npx tsc --noEmit
```

`api:check` generiert im Speicher gegen `docs/api/openapi.yaml` und scheitert, wenn
`frontend/types/generated/api.ts` abweicht. `frontend/types/api.ts` enthaelt nur
stabile Aliasnamen fuer diese generierten Schemas. Beide generierten Dateien duerfen
nicht unabhaengig vom Backend editiert werden.

Der CI-Job `backend-openapi` startet das produktive Backend gegen PostgreSQL,
exportiert erneut nach `docs/api/openapi.yaml`, prueft den Git-Diff und danach die
generierten Frontend-Typen. Eine DTO-, Required-/Nullable- oder Endpunktaenderung
wird dadurch als bewusste Vertragsaenderung sichtbar.

Der Vertrag verwendet die explizite relative Serveradresse `/`. Export-Host und
`SERVER_PORT` duerfen keinen Diff verursachen. Bei einem fehlgeschlagenen Schritt
`Verify committed OpenAPI contract` zeigt das CI-Log den Vertrags-Diff; der
exportierte Stand steht auch bei diesem Fehler als `openapi-spec`-Artefakt bereit.
Echte Schemaaenderungen muessen gemeinsam mit dem aktualisierten Snapshot und den
generierten Frontend-Typen eingecheckt werden; der Vergleich bleibt verbindlich.
