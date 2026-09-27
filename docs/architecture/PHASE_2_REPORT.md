# PHASE 2 REPORT

Stand: 2026-09-27. Auftrag: Konfiguration und Secret Hardening gemaess
`FASSWERK_CODEX_REFACTOR.md`. Phase-1-Aenderungen bleiben erhalten.

## Ist-Zustand und minimaler Plan

Ausgangslage: Konfiguration, Compose und Maven enthielten nutzbare Credential-
Defaults; JWT wurde erst bei Tokenoperationen validiert. `backend/.env` war versioniert.
V2 erzeugt bekannte aktive ADMIN-/STAFF-Konten. Das lokale Startscript lud `.env`,
startete aber auch direkt nach Erzeugung der Vorlage und setzte kein explizites Profil.

Plan: fehlende/ungeeignete Secrets vor dem DB-Start ablehnen; lokale Defaults nur
unter dev; Testprofil im Test-Classpath belassen; neue Dummy-Templates und Ignore-Regeln;
Seed-Konten ausschliesslich ueber eine neue Migration absichern; expliziten Erst-Admin-
Zugang ermoeglichen; Verhalten durch Konfigurations-, Bootstrap- und Migrationstests
sowie echten PostgreSQL-Start pruefen.

## Geaenderte Dateien

- `.gitignore`: Umgebungsdateien ignorieren, Beispieldateien zulassen.
- `.env.example`: Compose-Template mit leeren Pflicht-Secrets.
- `backend/.env`: nur aus Git-Index entfernt; private lokale Datei erhalten.
- `backend/.env.example`: reine Entwicklungs-/Dummy-Konfiguration und Variablendokumentation.
- `backend/.gitignore`: lokale Umgebungsdateien ausschliessen.
- `backend/pom.xml`: DB-Konfiguration des Flyway-Plugins aus Umgebungsvariablen.
- `backend/src/main/resources/application.yml`: DB/JWT ohne Defaults; optionaler Bootstrap.
- `backend/src/main/resources/application-dev.yml`: explizite lokale Defaults.
- `backend/src/main/resources/application.properties`: redundante Datei entfernt.
- `backend/src/main/java/org/thomcgn/backend/config/StartupSecurityConfiguration.java`: fruehe, wertfreie Fehlermeldungen und Profil-/Secret-Pruefung.
- `backend/src/main/java/org/thomcgn/backend/auth/JwtProperties.java`: Schluessellaenge/Issuer/Laufzeiten validieren, toString redigieren.
- `backend/src/main/java/org/thomcgn/backend/auth/service/AdminBootstrap.java`: explizite einmalige Erst-Admin-Anlage mit BCrypt, keine Ueberschreibung bestehender Konten.
- `backend/src/main/java/org/thomcgn/backend/auth/repository/AppUserRepository.java`: gezielte Bootstrap-Abfragen.
- `backend/src/main/resources/db/migration/V21__disable_known_seed_credentials.sql`: nur bekannte unveraenderte Seed-Hashes sperren und betroffene Refresh-Sessions entfernen.
- `backend/scripts/run-local.sh`: privates Template, Review vor Erststart, ausschliesslich dev, optionaler externer ENV-Dateipfad.
- `frontend/playwright.config.ts`: lokaler Worker-Count auf denselben Wert wie CI gesetzt (1); keine Testfaelle oder Zeitlimits geaendert.
- `docker-compose.yml`: private Pflichtwerte, prod-Profil, konsistente DB-Credentials und dynamischer DB-Healthcheck.
- `backend/README.md`, `docs/deployment-docker.md`, `docs/configuration.md`: aktueller Start-/Upgrade-/Bootstrap-Ablauf.
- `backend/src/test/java/org/thomcgn/backend/config/SecretConfigurationTest.java`: 22 Konfigurationsfaelle.
- `backend/src/test/java/org/thomcgn/backend/auth/AdminBootstrapTest.java`: fuenf Bootstrapfaelle.
- `backend/src/test/java/org/thomcgn/backend/auth/LegacySeedMigrationTest.java`: selektive Datenkorrektur und Sessionwiderruf.
- `docs/architecture/TECH_DEBT.md`, `docs/architecture/PHASE_2_REPORT.md`: Fortschritt und Nachweise.

## Behobene Probleme

- Ohne JWT-Secret startender Produktionskontext zuerst durch roten Regressionstest reproduziert, danach abgesichert.
- Default-/prod-Start erfordert DB_URL, DB_USER, DB_PASSWORD und private JWT_SECRET-Konfiguration.
- Platzhalter, bekannte alte Keys, Dev-/Test-Keys in Produktion und prod mit dev/test werden abgelehnt.
- Keine nutzbaren Secrets in den neuen Beispieldateien oder impliziten Produktionsdefaults.
- Alte Seed-Konten nach V21 nicht mehr mit ihren veroeffentlichten Passwoertern nutzbar; bereits geaenderte Passwoerter bleiben erhalten.
- Erst-Admin-Anlage nur explizit konfiguriert; kein Reset/Promote bestehender Konten.
- Lokale Entwicklung bleibt ueber dev und das Startscript verfuegbar.

## Neue Tests und ausgefuehrte Pruefungen

| Pruefung | Ergebnis |
| --- | --- |
| Reproduktion vor Fix | Neuer Test meldet korrekt FAIL: Kontext startete trotz leerem JWT_SECRET |
| Konfiguration/Bootstrap gezielt | PASS |
| `./mvnw clean verify` | PASS: 44 Tests, keine Fehler/Skips; JAR und JaCoCo erzeugt |
| JAR ohne JWT_SECRET / DB_PASSWORD | PASS: prod beendet sich mit benannter fehlender Variable vor Hikari/DB-Zugriff |
| Compose mit unveraendertem Dummy-Template | erwartete Ablehnung wegen fehlender privater Secrets |
| Compose mit expliziten privaten Werten | PASS: `docker compose config --quiet` |
| Isolierter PostgreSQL-16-Container | PASS: alle 14 Migrationen einschliesslich V21, zwei Seed-Konten deaktiviert, genau ein privat provisionierter ADMIN; Login erfolgreich, alter Seed-Login abgelehnt |
| Lokales run-local.sh gegen isolierte DB | PASS: dev ohne expliziten JWT_SECRET, Login mit zuvor angelegtem Admin erfolgreich |
| JAR-Inhalt | kein application-test.yml und keine redundante application.properties; dev nur explizit waehlbar |
| Historische Migrationen | V1-V20 im vorhandenen Dateisatz bytegleich zu HEAD |
| `npm ci` | PASS; Lockfile unveraendert; bekannte 20 Audit-Befunde weiterhin gemeldet |
| `npm run lint` | PASS: 0 Fehler, fuenf bestehende Warnungen |
| `npm run build` | PASS: Production-Build und TypeScript |
| E2E-Erstlauf, vier Worker | FAIL: acht bestanden, ein 30-s-Timeout im bekannten prerequisite-flow |
| E2E-Kontrolllauf, ein Worker | PASS: neun Tests, 59.5 s; derselbe prerequisite-flow benoetigte 12.2 s |
| `npm run test:e2e:critical`, neuer Standard | PASS: neun Tests, keine Fehler/Skips, ca. eine Minute |
| Shell / Diff | `bash -n` und `git diff --check` ohne Fehler |

PostgreSQL-Smoke: eigener kurzlebiger Container, zufaellige nur im Prozess gehaltene
Zugangsdaten, eigene dynamische localhost-Ports, keine bestehenden Datenbanken benutzt.
Testcontainer und Backendprozesse wurden gestoppt; temporaere private ENV-Datei entfernt.
Der Smoke testet Phase-2-Verhalten, ersetzt aber keine dauerhaften PostgreSQL-/Concurrency-
Integrationstests aus Phase 3. Die permanente Suite nutzt weiterhin H2 fuer DB-Tests.

Lokale Nachweise dieser Sitzung: `/tmp/fasswerk-phase2-backend-verify.log`,
`/tmp/fasswerk-phase2-frontend-*.log`, `/tmp/fasswerk-phase2-*-startup.log` und
`/tmp/fasswerk-phase2-missing-*.log`. Der isolierte Smoke kann waehrend dieser Sitzung
mit `python3 /tmp/fasswerk-phase2-smoke.py` wiederholt werden; Docker/PostgreSQL-Image
und der gebaute JAR muessen vorhanden sein. Temporaere Dateien werden nicht eingecheckt.

Der E2E-Erstfehler ist der in Phase 1 dokumentierte Last-Timeout (TD-035), kein
Aufruf des geaenderten Backends: diese Tests verwenden API-Mocks. Zur Stabilisierung
des vorgeschriebenen Gates laeuft Playwright lokal nun mit demselben einzelnen Worker
wie bereits in CI. Testauswahl, Assertions, Retries und Timeouts bleiben unveraendert.
Die parallele Fehlausfuehrung ist unter `/tmp/fasswerk-phase2-playwright-parallel/`
einschliesslich Trace gesichert, der Kontrolllauf unter
`/tmp/fasswerk-phase2-playwright-serial/`. Diese begrenzte Aenderung am Testbetrieb
wurde nach reproduziertem Fehler vorgenommen.

## Secret-Pruefung und Grenzen

Die bisher versionierte `.env` enthielt genau das alte bekannte JWT-Default und das
Entwicklungs-DB-Passwort; ihre Werte wurden im Bericht nicht wiederholt. Sie ist im
Index nicht mehr enthalten, bleibt lokal vorhanden und wird ignoriert. Die neuen
Templates enthalten nur leere Werte bzw. erklaerte Dummies. Alte Seed-Hashes bleiben
in unveraenderten historischen Migrationen und werden durch V21 unbrauchbar gemacht.
Es wurden keine privaten Secrets generiert und ins Repository geschrieben.
Eine zusaetzliche Suche nach privaten PEM/OpenSSH-Schluesseln sowie erkennbaren GitHub-,
AWS- und OpenAI-Tokenformaten in den vorhandenen versionierten Dateien lieferte keine
Treffer. Das ist keine Garantie fuer beliebige unbekannte Secret-Formate.

Git-Historie und bestehende Deployments wurden nicht umgeschrieben/geaendert. Falls
alte Werte ausserhalb lokaler Entwicklung verwendet wurden, muss der Betreiber sie
rotieren. Ein vollstaendiger Secret-Scan jedes historischen Blobs ist nicht nachgewiesen.

## Offene Risiken und Upgrade

- Beim naechsten regulaeren Start wird V21 angewendet. Bestehende Standardkonten mit unveraenderten Seed-Hashes verlieren ihren Zugang; Erst-Admin rechtzeitig konfigurieren.
- JWT-Schluessel bei Abloesung alter Defaults rotieren: vorhandene Access-JWTs werden durch reine Kontodeaktivierung im bisherigen Filter nicht sofort gesperrt. V21 entfernt Refresh-Sessions. Details in `docs/configuration.md`.
- Die erhaltene lokale backend/.env enthaelt noch den alten JWT-Default; fuer dev diesen Eintrag entfernen oder durch einen privaten Key ersetzen. Kein privater Schluessel wird automatisch dauerhaft angelegt.
- Bereits initialisierte PostgreSQL-Volumes uebernehmen neue POSTGRES_PASSWORD-Werte nicht automatisch. Bestehende DB-Zugaenge muessen kontrolliert rotiert werden; kein Volume loeschen.
- Fuenf Lintwarnungen, 20 npm-Audit-Befunde und uebrige Phase-1-Risiken bleiben offen.
- Keine bestehenden Deployments migriert, keine Git-Historie geloescht, keine neue Dependency, kein Commit erstellt.

## Ergebnis

**PASS fuer Phase 2.** Backend, erforderliche Secret-Validierung, Dev-Setup,
Frontend-Lint/-Build und kritische E2E sind nachgewiesen. Keine allgemeine
Produktionsfreigabe fuer die noch offenen Security-/Domain-Befunde aus Phase 1.

Die abschliessenden E2E-Berichte liegen fuer diese Sitzung unter
`/tmp/fasswerk-phase2-playwright-final/`; die beiden zuvor unveraenderten versionierten
Reportdateien im Repository wurden auf ihren Ausgangsinhalt zurueckgesetzt, um
Testartefakte aus dem Aenderungsdiff herauszuhalten.

Empfohlener naechster Schritt: **PHASE 3** (PostgreSQL-Teststrategie).
Phase 3 wurde nicht begonnen; diese Sitzung endet nach Phase 2.
