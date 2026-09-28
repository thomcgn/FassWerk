# FassWerk – Production-Ready Roadmap für Codex

## Vereinbarte Reihenfolge für UI und Flow

Nutzerentscheidung vom 29.09.2026: Der umfassende Rehaul der komplexen,
unübersichtlichen UI und Bedienabläufe kommt **ganz zum Schluss**, nach den
fachlichen und technischen Phasen. Bis dahin nur die für die jeweilige Phase
notwendigen UI-Ergänzungen und Fehlerkorrekturen. Phase 17 wird für diesen
abschließenden Rehaul vorgemerkt; die übrige Phasenarbeit wird dadurch nicht
vorgezogen oder ersetzt.

## Auftrag

Entwickle FassWerk schrittweise vom aktuellen funktionsfähigen Stand zu einer belastbaren Anwendung für einen realen Gastronomiebetrieb.

**Kein Big-Bang-Refactor. Keine Microservices. Keine unnötigen neuen Features.**

Priorität:

1. Datenintegrität und Zahlungs-/Bestandskonsistenz
2. Betriebssicherheit
3. Recovery und Auditierbarkeit
4. Direktverkauf / Barverkauf
5. UX unter realer Gastro-Last
6. Deployment, Monitoring, Backup und Security
7. belastbare Tests und dokumentierte Release-Kriterien

Nach jeder Phase Tests und Builds ausführen. Bei Fehlern nicht selbstständig zur nächsten Phase wechseln.

---

# Grundregeln

- Aktuellen `main`-Branch zuerst analysieren.
- Bestehende Schutzmechanismen dokumentieren und wiederverwenden.
- Keine hypothetischen Probleme als vorhandene Bugs darstellen.
- Änderungen möglichst klein und fachlich begründet halten.
- Domainlogik gehört ins Backend, nicht in Controller oder React-Komponenten.
- Bestehende API-Verträge möglichst erhalten.
- Flyway-Migrationen niemals nachträglich verändern.
- Neue Schemaänderungen ausschließlich über neue Migrationen.
- Kritische Fehler zunächst mit einem Test reproduzieren.
- Keine Java-`synchronized`-Lösung für DB-Konsistenz.
- Die Architektur muss auch mit mehreren Backend-Instanzen korrekt bleiben.
- Keine Secrets committen.
- Keine produktiven Default-Credentials.
- Nach jeder Phase einen Report erstellen und stoppen, wenn eine manuelle Freigabe verlangt wird.

---

# PHASE 0 – Production-Readiness Baseline

Noch keine produktiven Änderungen.

Analysiere:

- `auth`
- `billing`
- `table`
- `inventory`
- `reservation`
- `shift`
- `report`
- `config`
- Next.js BFF/API-Routen
- Docker/Compose
- Flyway
- Tests
- Logging/Actuator
- Environment-Konfiguration

Erstelle:

`docs/production/PRODUCTION_READINESS_BASELINE.md`

Für jeden kritischen Workflow dokumentieren:

- beteiligte Entities
- Services
- Endpoints
- Transaktionsgrenzen
- Authentifizierung/Autorisierung
- Idempotenz
- Concurrency-Verhalten
- Fehler-/Rollback-Verhalten
- vorhandene Tests
- verbleibendes Risiko

Priorisierung:

- **P0:** falsche Zahlung, falscher Bestand, Datenverlust, Auth-/Security-Risiko
- **P1:** inkonsistenter Geschäftsprozess oder Betriebsunterbrechung
- **P2:** Recovery-/UX-/Observability-Problem
- **P3:** Komfort/Optimierung

Am Ende konkrete Empfehlung, welche folgenden Phasen bereits teilweise oder vollständig erfüllt sind.

---

# PHASE 1 – Zahlungs- und Request-Idempotenz

Kritische mutierende Aktionen gegen Double-Tap, Retry und Timeout absichern.

Insbesondere prüfen:

- Bon bezahlen
- Split Payment
- Bon schließen
- Direktverkauf bezahlen
- Inventory Deduction
- Reservation Check-in
- Wiederöffnen/Storno

Geeignete Mechanismen:

- fachliche Zustandsprüfung
- eindeutige Constraints
- `Idempotency-Key` für kritische Abschlussoperationen

Tests:

- identischer Payment-Request zweimal
- Retry nach erfolgreicher Verarbeitung ohne Client-Antwort
- Double-Tap
- parallele Abschlussrequests

Erwartung:

**ein Geschäftsvorgang = genau eine fachliche Wirkung.**

---

# PHASE 2 – Concurrent Editing / Lost Updates

Mehrere Geräte müssen denselben Tisch bzw. Bestand sicher bearbeiten können.

Prüfen:

- Table Order
- Order Items
- Payment State
- Inventory
- Shift State

Wo sinnvoll JPA `@Version`, atomische Updates oder gezieltes DB-Locking einsetzen.

Veraltete Änderungen dürfen nicht still überschreiben.

Geeignete Konfliktantwort:

`409 Conflict`

Frontend:

- aktuellen Zustand neu laden
- Konflikt verständlich anzeigen
- keine Daten still verwerfen

Integrationstest mit zwei konkurrierenden Clients erstellen.

---

# PHASE 3 – Atomare Geschäftsprozesse

Analysiere insbesondere:

```text
Order
→ Payment
→ Inventory
→ Sales Tracking
→ Reorder
→ Shift Revenue
```

Kritische DB-Änderungen eines Abschlusses müssen konsistent sein.

Es darf beispielsweise nicht entstehen:

```text
PAID
Inventory unverändert
```

oder:

```text
Inventory reduziert
Payment nicht gespeichert
```

Failure-Injection-Tests ergänzen und Rollback nachweisen.

Externe/langsame Operationen nicht unnötig innerhalb langer DB-Transaktionen halten.

---

# PHASE 4 – Inventory Concurrency

Parallele Verkäufe desselben Artikels müssen deterministisch korrekten Bestand ergeben.

Beispiel:

```text
Bestand 10
- Verkauf A: 2
- Verkauf B: 3
= Bestand 5
```

Read-Modify-Write-Race-Conditions suchen.

Je nach Modell:

- Optimistic Locking
- atomisches SQL Update
- Pessimistic Locking

Tests für:

- parallele Verkäufe
- Rollback
- Retry
- negativer Bestand
- Korrekturbuchungen

---

# PHASE 5 – Server State & Recovery

Browser, Tablet oder WLAN dürfen keinen offenen Geschäftsvorgang zerstören.

Nach Reload/Login rekonstruierbar:

- offene Bons
- unbezahlte Bons
- laufende Split Payments soweit fachlich sinnvoll
- Reservierungen
- laufende Schicht
- Belegungen

Keine kritischen Geschäftsdaten ausschließlich in React State, `localStorage` oder `sessionStorage`.

Playwright-Recovery-Test ergänzen.

---

# PHASE 6 – Korrekturen, Storno und Audit

Abgeschlossene historische Vorgänge nicht still überschreiben.

Bevorzugt fachliche Gegen-/Korrekturbuchungen.

Audit für mindestens:

- Payment
- Bonabschluss
- Wiederöffnung
- Split Payment
- Storno
- Bestandskorrektur
- Preisänderung
- Schichtabschluss

Auditinformationen:

```text
timestamp
actor
action
entityType
entityId
reason
metadata
```

Keine Tokens, Passwörter oder unnötigen personenbezogenen Daten protokollieren.

---

# PHASE 7 – Direktverkauf / Barverkauf

Neben TABLE einen schnellen Verkauf ohne Deckel unterstützen.

```text
TABLE
→ offener Bon
→ spätere Zahlung

DIRECT
→ Artikel
→ sofortige Zahlung
→ CLOSED
```

DIRECT muss dieselben Kernkomponenten verwenden:

- Preise
- Payment
- Inventory
- Sales Tracking
- Reorder
- Reporting
- Shift Settlement

Kein künstlicher Tisch.

UX-Ziel:

```text
Barverkauf
→ Getränk A
→ Getränk B
→ Bezahlen
→ Bar/Karte
→ nächster Verkauf
```

---

# PHASE 8 – Sales und Occupancy trennen

**Sales != Occupancy**

Ein Gast kann an der Bar kaufen und gehen oder anschließend Platz nehmen.

Darum Verkauf und Kapazitätsbelegung getrennt modellieren.

```text
SALES
├── TABLE
└── DIRECT

OCCUPANCY
├── RESERVATION
└── WALK_IN
```

Walk-in soll mindestens erfassen können:

```text
guestCount
tableId optional
area optional
startedAt
endedAt optional
source
```

Vor Einführung einer neuen Entity prüfen, ob bestehendes Reservation-/Table-Modell sauber erweitert werden kann.

Nach DIRECT Sale optional:

`Nimmt Platz`

Dann Personenanzahl und optional Tisch/Bereich erfassen.

Der DIRECT Sale bleibt trotzdem `PAID/CLOSED`.

Mehrere Barbestellungen derselben Gruppe dürfen nicht mehrfach Kapazität belegen.

Beim Verlassen Occupancy beenden und Kapazität freigeben.

---

# PHASE 9 – Kapazitätslogik

Kapazität darf nicht aus Verkaufsanzahl abgeleitet werden.

Berücksichtigen:

- aktuelle Walk-ins
- aktuelle Tischbelegung
- relevante Reservierungen
- bestehende Zeitfensterlogik

Barverkauf ohne Sitzplatz:

`Kapazitätsänderung = 0`

Walk-in mit drei Personen:

`Occupancy +3`

Tests für:

- Walk-in
- Gast geht
- Tischwechsel
- weitere Barbestellung
- Reservierung + Walk-ins
- keine Doppelzählung

---

# PHASE 10 – Security Hardening

Prüfe gegen aktuelle Produktionskonfiguration:

- keine Default-JWT-Secrets
- keine Dev-Credentials in Produktion
- sichere Cookie-Flags
- CORS
- CSRF-Konzept passend zur BFF-/Cookie-Architektur
- Refresh Token Handling
- Logout/Invalidierung
- Session-/Token-Lifetime
- Rate Limiting besonders für Login und öffentliche Reservierung
- Input Validation
- Authorization auf Service/Endpoint-Ebene
- Actuator-Exposition
- OpenAPI/Swagger in Produktion
- Security Headers
- Fehlerantworten ohne interne Details

Environment-Start soll bei fehlenden kritischen Secrets **fail fast** sein.

Security-E2E/Integrationstests ergänzen.

---

# PHASE 11 – Produktionskonfiguration

Dev/Test/Prod klar trennen.

Prüfen:

- Spring Profiles
- Environment Variables
- DB-Konfiguration
- Mail
- JWT
- Public URLs
- Cookie Domain/Secure/SameSite
- Logging
- Actuator
- CORS

Keine unsicheren produktiven Fallbackwerte.

Erstelle:

`docs/production/PRODUCTION_CONFIGURATION.md`

und aktualisiere `.env.example` ausschließlich mit ungefährlichen Beispielen.

---

# PHASE 12 – Docker & Deployment Hardening

Prüfen:

- Backend-Image führt Tests nicht nur implizit dauerhaft mit `-DskipTests` vorbei
- Multi-stage build
- non-root runtime
- feste/geeignete Image-Versionen statt unnötiger `latest`-Tags
- Healthchecks
- Readiness/Liveness
- graceful shutdown
- Restart-Verhalten
- persistente PostgreSQL-Daten
- keine Secrets im Image
- minimale Runtime-Images

Deployment muss einen fehlerhaften Container erkennen können.

Dokumentiere Start, Update und Rollback.

---

# PHASE 13 – Database Migration Safety

Flyway als alleinige Schemahistorie absichern.

Prüfen:

- Migrationen unveränderlich
- Constraints
- Indizes
- Foreign Keys
- Unique Constraints für Idempotenz
- sinnvolle NOT NULL-Regeln
- Migration auf produktionsähnlicher DB
- Upgrade von bestehendem Schema

Test:

```text
leere DB → latest
bestehende vorherige Version → latest
```

Beide müssen erfolgreich sein.

---

# PHASE 14 – Backup & Restore

Erstelle:

`docs/operations/BACKUP_RESTORE.md`

Dokumentiere:

- PostgreSQL Backup
- Restore
- benötigte Secrets/Environment
- Flyway-Verhalten
- Backup-Aufbewahrung
- Verifikation

Restore-Test:

```text
Testdaten
→ Backup
→ DB zurücksetzen
→ Restore
→ zentrale Geschäftsdaten prüfen
```

Ein Backup gilt erst dann als belastbar, wenn Restore getestet wurde.

---

# PHASE 15 – Observability

Strukturierte, brauchbare Logs einführen/prüfen.

Logs müssen bei einem Fehler beantworten können:

- welcher Request?
- welcher Business-Vorgang?
- welcher Benutzer, soweit zulässig?
- welche Entity?
- welcher Fehler?

Correlation/Request ID prüfen.

Metriken mindestens für:

- HTTP Fehler
- Auth-Fehler
- DB-/Connection-Probleme
- Payment-/Order-Abschlussfehler
- Inventory-Abschlussfehler
- JVM
- Health

Keine sensiblen Daten loggen.

Actuator nur gezielt exponieren.

---

# PHASE 16 – Fehler- und Recovery-UX

Frontend unterscheidet mindestens:

- Offline/Netzwerk
- 401
- 403
- 409
- 4xx Validation
- 5xx
- unbekannter Status nach Timeout

Besonders kritisch:

Bei Verbindungsabbruch während Zahlung niemals ungeprüft anzeigen:

`Zahlung fehlgeschlagen`

Stattdessen Serverstatus erneut ermitteln.

Beispiel:

> Verbindung während des Abschlusses unterbrochen. Der aktuelle Bonstatus wird geprüft.

Double-Submit-Buttons während laufender Requests UI-seitig verhindern – zusätzlich zur serverseitigen Idempotenz.

---

# PHASE 17 – Gastro-UX unter Last / abschließender UI- und Flow-Rehaul

Auf Nutzerwunsch vom 29.09.2026 ganz zum Schluss durchführen. Die frühere
Begrenzung auf kleine UX-Optimierungen ist aufgehoben: Navigation, Informationsdichte
und Bedienabläufe sollen als Ganzes überarbeitet werden. Zuerst die vorhandenen
Arbeitsabläufe bewerten, dann ein zusammenhängendes Bedienkonzept umsetzen.

Optimieren für:

> Freitagabend, laut, voll, Touchgerät, wenig Zeit.

Prüfen:

- Touch Targets
- Anzahl notwendiger Taps
- Kategorien
- häufige Produkte
- Favoriten
- zuletzt verwendete Produkte
- direktes Feedback
- Tischwechsel
- Barverkauf
- Zahlung
- Storno
- Konflikte

Erstelle zuerst:

`docs/ux/STAFF_WORKFLOW_REVIEW.md`

Ziel:

```text
Tischverkauf:
Tisch → Produkt → Produkt → fertig

Direktverkauf:
Barverkauf → Produkt → Produkt → Zahlung
```

Nur danach kleine gezielte UX-Verbesserungen implementieren.

---

# PHASE 18 – Testpyramide vervollständigen

Bestehende Playwright-E2E-Tests beibehalten.

Zusätzlich prüfen, wo schnelle Backend Unit-/Integrationstests und Frontend Component-/Unit-Tests sinnvoll sind.

Kritische E2E-Suite mindestens:

1. Reservation Lifecycle
2. Table Billing
3. Split Payment
4. Double Payment
5. Inventory Deduction
6. paralleler Inventory Sale
7. Concurrent Table Editing
8. Reload/Recovery
9. Failure During Close
10. Correction/Storno
11. DIRECT Sale
12. DIRECT Sale Double Payment
13. Walk-in Occupancy
14. Occupancy End
15. Auth/BFF Security
16. Session/Refresh

JaCoCo nicht nur Report erzeugen lassen: prüfen, ob ein sinnvoller Coverage-Gate für kritische Backendbereiche eingeführt werden kann.

Kein sinnloses 100%-Coverage-Ziel.

---

# PHASE 19 – Performance & Datenbank

Mit realistischen Datenmengen prüfen:

- Getränkekatalog
- Sales History
- Inventory Movements
- Reservations
- Audit Events
- Reporting

Untersuchen:

- N+1 Queries
- fehlende Indizes
- unnötig große Payloads
- Pagination
- langsame Reports
- Connection Pool
- Transaktionsdauer

Optimierungen nur nach messbarem Befund durchführen.

---

# PHASE 20 – Datenschutz & Datenlebenszyklus

Prüfen, welche personenbezogenen Daten tatsächlich gespeichert werden.

Insbesondere Reservierungen.

Dokumentieren:

- Zweck
- Aufbewahrung
- Löschung/Anonymisierung
- Logs
- Backups
- Admin-Zugriff

Keine unnötigen personenbezogenen Daten sammeln.

Erstelle:

`docs/production/DATA_LIFECYCLE.md`

Keine rechtlichen Garantien formulieren; technische Datenflüsse und vorhandene Mechanismen dokumentieren.

---

# PHASE 21 – Operational Runbook

Erstelle:

`docs/operations/RUNBOOK.md`

Mindestens:

- Anwendung starten
- Anwendung stoppen
- Deployment
- Rollback
- DB Migration
- Backup
- Restore
- Health prüfen
- Logs prüfen
- DB nicht erreichbar
- Backend nicht erreichbar
- Frontend nicht erreichbar
- Mailproblem
- Authproblem
- voller Datenträger
- fehlerhafte Migration

Ziel:

Ein Betreiber soll nicht den Sourcecode verstehen müssen, um einen typischen Ausfall einzugrenzen.

---

# PHASE 22 – Repo- und Produktdokumentation

Root-`README.md` erstellen bzw. professionalisieren.

Enthalten:

- Was ist FassWerk?
- Zielgruppe
- Kernproblem
- wichtigste Workflows
- Architektur
- Screenshots
- lokale Installation
- Tests
- Docker
- Konfiguration
- Production-Hinweise
- Status des Projekts

Historische/interne Markdown-Dateien prüfen.

Veraltete oder widersprüchliche Dokumentation markieren, zusammenführen oder entfernen.

Keine Behauptung wie „production ready“ oder „fully tested“, solange die Definition of Done nicht erfüllt ist.

---

# PHASE 23 – Release Candidate

Einen produktionsnahen Release Candidate erstellen.

Vorher vollständig ausführen:

```text
Backend compile
Backend tests
Frontend lint
Frontend build
Frontend tests
Critical E2E
Security tests
Migration test
Backup/restore test
```

Zusätzlich manueller Smoke Test:

```text
Login
→ Reservierung
→ Check-in
→ Tischbestellung
→ Zahlung
→ Inventory prüfen
→ Direktverkauf
→ Walk-in
→ Schichtabschluss
→ Reporting
```

Keine neuen Features mehr in dieser Phase.

Nur Release-Blocker beheben.

---

# Production-Ready Definition of Done

FassWerk darf technisch erst dann als **Production Ready** bezeichnet werden, wenn mindestens Folgendes nachgewiesen ist:

## Datenintegrität

- kein Double Payment
- kein doppelter Inventory-Abgang durch Retry
- kein Lost Update bei kritischen Workflows
- parallele Verkäufe erzeugen korrekten Bestand
- kritische Abschlüsse sind atomar

## Recovery

- Reload verliert keine offenen serverseitigen Vorgänge
- Timeout nach Payment kann sicher aufgelöst werden
- Browser-/Gerätewechsel erzeugt keinen inkonsistenten Zustand

## Korrektur

- abgeschlossene Vorgänge können nachvollziehbar korrigiert werden
- kritische Änderungen sind auditiert

## Betrieb

- Healthchecks vorhanden
- strukturierte Logs vorhanden
- Backup erfolgreich
- Restore erfolgreich getestet
- Deployment und Rollback dokumentiert
- DB-Migrationen getestet

## Security

- keine Default-Secrets
- sichere Produktionskonfiguration
- Authorization getestet
- Session-/Token-Verhalten getestet
- öffentliche Endpoints angemessen geschützt

## Gastro-Workflow

- TABLE funktioniert
- DIRECT funktioniert
- WALK_IN funktioniert
- Sales und Occupancy sind fachlich getrennt
- Shift Settlement berücksichtigt beide Verkaufstypen
- Inventory/Sales Tracking berücksichtigt beide Verkaufstypen

## Tests

- kritische Backendtests grün
- Frontend Build/Lint grün
- Critical E2E grün
- Security Tests grün
- Migration Tests grün
- Restore Test grün

---

# Reihenfolge / Priorität

Nicht alle Phasen haben dieselbe Dringlichkeit.

## Release Blocker

```text
Phase 0
Phase 1
Phase 2
Phase 3
Phase 4
Phase 5
Phase 6
Phase 10
Phase 11
Phase 12
Phase 13
Phase 14
Phase 15
Phase 16
Phase 18
Phase 21
Phase 23
```

## Fachlich wichtig vor echtem Einsatz

```text
Phase 7
Phase 8
Phase 9
Phase 17
```

## Qualitäts-/Reifegrad

```text
Phase 19
Phase 20
Phase 22
```

---

# Pflichtreport nach jeder Phase

```text
PHASE X REPORT

Status:
PASS / FAIL

Ist-Zustand:
- ...

Gefundene reale Risiken:
- ...

Bereits vorhandene Schutzmechanismen:
- ...

Änderungen:
- ...

Migrationen:
- ...

Tests:
- ...

Backend Build:
PASS / FAIL

Backend Tests:
PASS / FAIL

Frontend Lint:
PASS / FAIL

Frontend Build:
PASS / FAIL

Critical E2E:
PASS / FAIL

Verbleibende Risiken:
- ...

Nächster Schritt:
- ...
```

Bei einem Release-Blocker mit `FAIL` nicht zur nächsten Phase wechseln.

---

# STARTAUFTRAG FÜR CODEX

Beginne ausschließlich mit **PHASE 0 – Production-Readiness Baseline**.

Analysiere den aktuellen `main`-Branch und gleiche den tatsächlichen Stand mit dieser Roadmap ab.

Erstelle:

`docs/production/PRODUCTION_READINESS_BASELINE.md`

Wichtig:

- Noch keine produktiven Änderungen.
- Bereits vorhandene Lösungen ausdrücklich anerkennen.
- Keine theoretischen Risiken als vorhandene Bugs ausgeben.
- Für jedes reale Risiko konkrete Dateien, Klassen, Services, Entities, Endpoints und vorhandene Tests nennen.
- P0–P3 priorisieren.
- Prüfen, welche Roadmap-Phasen bereits ganz oder teilweise erledigt sind.
- Keine Architektur nur deshalb umbauen, weil diese Roadmap ein mögliches Modell nennt.
- Die kleinste robuste Lösung bevorzugen.

Beende mit dem `PHASE 0 REPORT`.

**Danach stoppen und auf Freigabe für PHASE 1 warten.**
