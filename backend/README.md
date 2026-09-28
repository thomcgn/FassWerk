# FassWerk Backend

Spring Boot backend for reservation, menu, table billing, and transactional inventory deduction.

## Features in this iteration
- Flyway-based PostgreSQL schema and seed data
- Reservation API with slot/capacity checks and QR token flow
- QR code PNG generation endpoint
- Drink category/drink/variant CRUD APIs
- Table order open/add item/remove item/close APIs
- Transactional stock deduction and inventory movement logging
- JWT login with role-based authorization (`ADMIN`, `STAFF`)
- Refresh-token rotation and logout revoke flow
- Inventory CRUD + manual adjustment + movement API
- Reorder suggestions API and PDF export endpoint
- Actuator auth metrics (`/actuator/metrics/*`) and Prometheus endpoint (`/actuator/prometheus`)

## Configuration and local run

See [configuration and secrets](../docs/configuration.md) for profiles, environment
variables, initial admin provisioning and the migration of existing installations.

```bash
./scripts/run-local.sh
```

On first use this creates a private `.env` from `.env.example` and exits for review.
On subsequent starts it loads that file and explicitly enables `dev`. Only this
profile supplies local database defaults and a development-only signing key.
The default profile and `prod` require explicit private DB credentials and JWT key.
Do not use `dev` for deployed environments.

For login on a fresh database, provide `BOOTSTRAP_ADMIN_EMAIL` and
`BOOTSTRAP_ADMIN_PASSWORD` once, using a new email and a private password of at least
16 characters (at most 72 UTF-8 bytes). Remove both variables after provisioning.
The bootstrap never resets an existing account or adds another admin when an active
administrator already exists.

## Test run

```bash
./mvnw test
./mvnw verify
```

Tests explicitly use the `test` profile from `src/test/resources`. This profile
and its PostgreSQL/Testcontainers setup are not included in the production JAR.
A reachable Docker engine is required. Testcontainers supplies an isolated PostgreSQL
16 instance and generated credentials; no local/production database is used. Flyway
creates the schema and Hibernate only validates it. Docker unavailability fails
the tests (no skips or H2 fallback). Combining `prod` with `dev` or `test` fails at startup.
See [database test strategy](../docs/testing.md), including known concurrency gaps.

## Actuator
- `GET /actuator/health` is public
- `GET /actuator/metrics/**` and `GET /actuator/prometheus` require `ADMIN`

## OpenAPI / Swagger
- OpenAPI JSON: `GET /v3/api-docs`
- OpenAPI YAML: `GET /v3/api-docs.yaml`
- Swagger UI: `GET /swagger-ui/index.html`

Versionierte OpenAPI lokal exportieren:

```bash
./scripts/export-openapi.sh
```

Der Export erwartet eine erreichbare PostgreSQL-Datenbank und die in
[configuration.md](../docs/configuration.md) beschriebenen DB-/JWT-Variablen.
Standardmaessig wird zuvor `clean verify` ausgefuehrt. `OPENAPI_SKIP_BUILD=true`
verwendet ein bereits geprueftes JAR; `OPENAPI_OUT_DIR` und `SERVER_PORT` sind optional.
Start-/Downloadfehler zeigen das Backend-Log; nur der gestartete Prozess wird beendet.

CI fuehrt zuerst alle Backendtests aus und startet fuer den Export eine separate,
kurzlebige PostgreSQL-16-Instanz mit generierten Zugangsdaten und JWT-Schluessel.
Es sind keine Produktions-Secrets notwendig. Nur im CI-Export ist der SMTP-Healthcheck
via `MANAGEMENT_HEALTH_MAIL_ENABLED=false` ausgenommen; DB-Health bleibt aktiv.
Bei Fehlern wird
`backend/target/openapi-backend.log` als Diagnoseartefakt hochgeladen; die Datenbank
wird auch bei einem fehlgeschlagenen Export entfernt (siehe `.github/workflows/ci.yml`).

## Legacy seed users

Migration V21 disables accounts that still carry the published legacy seed password
hashes and removes their refresh sessions. Accounts with already changed passwords
are preserved. Do not edit V2 or restore the published seed credentials.
See the upgrade procedure in [configuration.md](../docs/configuration.md).

## Auth endpoints
- `POST /api/auth/login` -> access + refresh token
- `POST /api/auth/refresh` -> rotates refresh token and issues a new pair
- `POST /api/auth/logout` -> revokes refresh token + current access token (if bearer provided)
- `GET /api/auth/sessions` -> list active sessions for current user
- `DELETE /api/auth/sessions/{id}` -> revoke one session
- `POST /api/auth/logout-all` -> revoke all active sessions for current user

## Observability

Profil `prod` aktiviert ECS-JSON-Logs mit `requestId`. Actuator liefert JVM-,
HTTP-/Latenz- und Hikari-Metriken über den ADMIN-geschützten Prometheus-Endpunkt.
Reservierungsmails haben separate Zähler für `sent`, `failed` und `skipped`.
Der öffentliche Health-Endpunkt zeigt keine internen Details.
Scrape-Zugang, PromQL-Beispiele, Datenschutz und Diagnose:
[Monitoring und Alerting](../docs/operations/monitoring-alerting.md).

## Auditkorrekturen und Full-Stack-Abnahme

[Auditbehebung](../docs/architecture/AUDIT_REMEDIATION.md) beschreibt die neuen
Migrationen V26–V28, Geschäftstage, Session-Widerruf, Auth-Limits und verbleibende
Altbestandsgrenzen. V26 widerruft einmalig bestehende Refresh-Sitzungen; beim
Upgrade ist für alle Nutzer ein neuer Login erforderlich. Die Migration erfindet
keine Verkaufstage alter Bonpositionen.
Storno-Wiederholungen unterstützen jetzt ebenfalls `Idempotency-Key`.

Nach `./mvnw -B clean verify` und dem Frontend-Production-Build startet
`ACCEPTANCE_JAR=/absoluter/pfad/backend-0.0.1-SNAPSHOT.jar ../scripts/fullstack-acceptance.sh`
eigene temporäre Dienste: zwei BFFs, Backend und PostgreSQL. Geprüft werden echte
Fachaktionen, Antwortverlust, Neustart und ein synthetischer Dump/Restore.
`./scripts/audit-preflight.sql` enthält ausschließlich lesende Prüfungen für eine
separate V25+-Restore-Kopie; echte Produktionshistorien müssen zusätzlich geprüft werden.

### Production-Roadmap: Request-Idempotenz (Phase 1)

V29 ergänzt persistente Tagesabschluss-Belege; bestehende Migrationen und Sessions
bleiben unverändert. Der manuelle Tagesabschluss benötigt jetzt einen stabilen
`Idempotency-Key` und JSON mit `expectedBusinessDate`. Alte Clients ohne diese
Angaben erhalten 400; Backend und BFF/Frontend gemeinsam aktualisieren.
Bon-Abschluss, Zurückstellen und Wiederöffnung unterstützen ebenfalls den
Idempotenzheader. Details, Wiederholungsverhalten und Prüfungen:
[Phase-1-Bericht](../docs/production/PHASE_1_REPORT.md).

### Production-Roadmap: konkurrierende Formulare (Phase 2)

V30 ergänzt Revisionen für Inventar und Schichtabrechnung. Inventar-PUT sowie
Schicht-PUT senden die zuvor gelesene `expectedRevision`; Inventar-DELETE verwendet
sie als Queryparameter. Veraltete Stände ergeben 409. Stammdaten-PUT verändert
keine physische Bestandsmenge; Korrekturen bleiben Bewegungsbuchungen mit Begründung.
Backend und Frontend gemeinsam aktualisieren, ohne alte und neue Backend-Schreiber
parallel zu betreiben. Details: [Phase-2-Bericht](../docs/production/PHASE_2_REPORT.md).

### Production-Roadmap: atomare Geschäftsprozesse (Phase 3)

Bonposition, Bestandsbewegung, Absatz und Nachbestellberechnung werden gemeinsam
gebucht. Zahlung, Teilbon, Tischfreigabe und Reservierungsabschluss bleiben ebenfalls
transaktional; Zahlung erzeugt keinen zweiten Bestandsabgang. Neue PostgreSQL-Tests
prüfen Fehler bis zum Commit, vollständigen Rollback und anschließende idempotente
Wiederholung. Details und Abnahme: [Phase-3-Bericht](../docs/production/PHASE_3_REPORT.md).

### Production-Roadmap: parallele Bestandsbuchungen (Phase 4)

Konkurrenztests prüfen Verkäufe, gemeinsame Getränke-Bestände, Korrekturen, Storno,
Überverkauf und wiederholte Befehle gegen PostgreSQL. Die bestehenden DB-Sperren
schützen Mengen und Bewegungen; bezahlt wird ohne erneuten Warenverbrauch.
Details und Abnahme: [Phase-4-Bericht](../docs/production/PHASE_4_REPORT.md).

### Production-Roadmap: Server State & Recovery (Phase 5)

Die reale Fullstack-Abnahme prüft nun zusätzlich einen frischen Browser mit neuem
Login, Reload ohne Webspeicher sowie offene, unbezahlte und bereits teilbezahlte
Bons und gespeicherte Schichten vor und nach App-Neustart. Eine verlorene
Teilzahlungsantwort wird nach erneutem Login mit demselben Schlüssel wiederholt.
Ungespeicherte Formulare bleiben Entwürfe; automatische Speicherung wird nicht
behauptet. Details: [Phase-5-Bericht](../docs/production/PHASE_5_REPORT.md).

### Production-Roadmap: Preisbindung und Audit (Phase 6)

Gebuchte Bonpositionen behalten ihren Einzelpreis. Neue Bestellungen zu einem
anderen Katalogpreis erhalten eine eigene Position; Standardpreisänderungen
berechnen offene Bons nicht mehr neu. Preisänderungen nutzen den Buchungslock.

V31 ergänzt ein transaktionales Auditjournal für Zahlung, Split, Storno,
Bestandsbewegungen, Preise, Schichtspeicherungen und Tagesabschlüsse. Benutzer-ID,
Zeit, fachlicher Bezug und Vorher-/Nachher-Werte bleiben erhalten; ein Auditfehler
rollt die Buchung zurück. Keine rückwirkende Historie und keine neue Refund-API.
Audit- und Restore-Nachweise sowie Betreiberabfragen:
[Phase-6-Bericht](../docs/production/PHASE_6_REPORT.md).

### Phase 7: Direktverkauf

`POST /api/table-orders/direct` ist für ADMIN, BARCHEF und STAFF verfügbar und
benötigt einen `Idempotency-Key`. Der Request enthält `paymentMethod` (`CASH` oder
`CARD`) und `items` mit `drinkVariantId`, `quantity` und `expectedUnitPrice`.
Bestätigte Preise werden gegen den Katalog geprüft; Abweichungen ergeben 409.
Der gesamte Verkauf einschließlich Bestand, Sales, Nachbestellberechnung und
Audit wird atomar gebucht und bezahlt geschlossen. Wiederholungen mit demselben
Schlüssel und Payload liefern denselben Bon, abweichende Payloads ergeben 409.

V32 ergänzt `saleType` (`TABLE`/`DIRECT`) und `paymentMethod`; `tableId` ist bei
DIRECT null. Bestehende Bons bleiben TABLE und behalten ihre bisherigen Werte.
Ein verzögerter DB-Trigger verhindert das Committen eines offenen DIRECT-Bons.
Das Archiv enthält Direktbons als „Barverkauf“. Kartenerlöse zählen zum Umsatz,
aber nicht zum erwarteten Bargeldbestand; historische Tischbons ohne Zahlungsart
behalten die bisherige Bargeldbehandlung.

Frontend: `/direct-sales`. Zahlung wird nur erfasst, kein Terminal angesteuert.
Ein ungesendeter Warenkorb ist ein flüchtiger Entwurf. Bereits gesendete,
ungeklärte Abschlüsse bleiben zur sicheren Wiederholung im Session-Speicher.
Siehe [Phase-7-Report](../docs/production/PHASE_7_REPORT.md).
