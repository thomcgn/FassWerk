# CI OpenAPI Export Fix

Datum: 2026-09-27. Gezielte Fehlerkorrektur nach Phase 3, keine neue Refactoring-Phase.

## Reproduzierte Ursachen

- Der Job backend-openapi startete das JAR ohne PostgreSQL und ohne die seit Phase 2
  erforderlichen DB-/JWT-Variablen. Die alte Warteschleife pruefte den Prozessstatus
  nicht und versuchte nach rund 40 Sekunden trotzdem den Download.
- Mit korrekter DB-/JWT-Konfiguration lieferte Health HTTP 503: Der MailHealthIndicator
  versuchte localhost:1025 zu erreichen, obwohl der Schemaexport kein SMTP braucht.
- Nach erfolgreichem Healthcheck lieferte der YAML-Download HTTP 403. Die Freigabe
  fuer /v3/api-docs/** umfasst nicht /v3/api-docs.yaml.

Diese Ursachen wurden lokal am echten JAR reproduziert. Der konkrete fehlgeschlagene
GitHub-Run und dessen Logs wurden nicht abgerufen.

## Korrektur

- `.github/workflows/ci.yml` fuehrt jetzt `clean verify` statt skipTests aus.
- Eine separate PostgreSQL-16-Instanz unterstuetzt den anschliessenden Export.
  DB-Passwort und JWT-Schluessel werden pro Run generiert und in GitHub maskiert;
  Produktions-Secrets und vorhandene Datenbanken sind nicht erforderlich.
- Nur fuer den CI-Export wird MANAGEMENT_HEALTH_MAIL_ENABLED=false gesetzt.
  DB-Health, prod-Profil und die strenge Secret-Validierung bleiben aktiv.
- `SecurityConfig` gibt die exakte YAML-Adresse fuer GET frei, entsprechend der
  bereits oeffentlichen JSON-/Swagger-Dokumentation. Fachendpunkte bleiben geschuetzt.
- `export-openapi.sh` bricht bei Prozessende, fehlender Bereitschaft oder Downloadfehler
  klar ab, zeigt das Backend-Log und beendet nur den selbst gestarteten Prozess.
  Die bestehende 40-Sekunden-Grenze wurde nicht erhoeht. Ein temporaerer Download wird
  erst nach erfolgreicher OpenAPI-Kopfpruefung atomar als Ergebnis veroeffentlicht.
- CI sichert bei Fehlern target/openapi-backend.log und entfernt seine Exportdatenbank
  auch bei Fehlern. Fehlende Exportartefakte sind ein Fehler, keine stille Warnung.
- OpenApiIntegrationTest prueft anonymen JSON-/YAML-Abruf, Media Types, Dokumentkopf
  und enthaltene Auth-/Billing-Routen ueber echtes HTTP mit PostgreSQL.

## Pruefergebnisse

| Pruefung | Ergebnis |
| --- | --- |
| `./mvnw -B clean verify` | PASS: 70 Tests, 0 Fehler, 0 Failures, 0 Skips; 1:10 min |
| Fehlende Secrets am echten JAR | PASS: klarer JWT_SECRET-Startfehler nach 7 Sekunden, kein Exportartefakt |
| Echter Export mit frischem PostgreSQL und prod-Profil | PASS: 68195 Bytes OpenAPI-YAML, erwartete Routen und GitHub-Artefaktpfad |
| Cleanup | PASS: eigener Backend-Port geschlossen, eigener Container entfernt |
| Shell-Syntax / Workflow-YAML / git diff --check | PASS |

Logs: `/tmp/fasswerk-openapi-verify.log` und
`/tmp/fasswerk-openapi-ci-smoke-final.log`.

Frontend und Abhaengigkeitsversionen wurden fuer diesen Fix nicht geaendert;
Frontendpruefungen deshalb nicht erneut ausgefuehrt. Vorhandene Phase-3-Aenderungen
bleiben erhalten. Kein Commit/Push und kein erneuter GitHub-Run wurden ausgefuehrt.
Die Korrektur wird nach Commit/Push durch den Workflow geprueft. Weitergehende
Release-/Image-Gates und die dokumentierten Domainrisiken bleiben offen.
