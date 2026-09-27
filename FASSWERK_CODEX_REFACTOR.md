# FassWerk -- Codex Refactoring & Hardening Plan

> Ziel: FassWerk schrittweise stabilisieren, absichern und wartbarer
> machen, ohne einen Big-Bang-Refactor zu erzeugen.
>
> Repository: `thomcgn/FassWerk`
>
> **Arbeitsweise:** Codex arbeitet phasenweise. Nach jeder Phase müssen
> Build, Tests und die definierten Akzeptanzkriterien erfüllt sein. Erst
> danach darf die nächste Phase begonnen werden.

## 0. Rolle und verbindliche Regeln für Codex

Arbeite als **Senior Software Engineer / Software Architect** mit
Schwerpunkt auf:

-   Java 21
-   Spring Boot 4
-   PostgreSQL
-   Flyway
-   Spring Security
-   Next.js 16 / React 19 / TypeScript
-   Docker
-   Domain-Driven Design
-   Clean Code
-   automatisierten Tests

### Nicht verhandelbare Regeln

1.  **Keine Komplett-Neuschreibung.**
2.  Bestehendes Verhalten zuerst verstehen und durch Tests absichern.
3.  Keine Änderung nur aus ästhetischen Gründen.
4.  Änderungen klein und nachvollziehbar halten.
5.  Keine Breaking API Changes ohne zwingenden Grund.
6.  Datenbankänderungen ausschließlich über neue Flyway-Migrationen.
7.  Bereits ausgeführte Flyway-Migrationen niemals nachträglich
    verändern.
8.  Keine Secrets, Passwörter oder produktionsfähigen
    Default-Credentials committen.
9.  Domainlogik gehört nicht in Controller oder React-Komponenten.
10. Nach jeder Phase:

-   Backend kompilieren
-   Backend-Tests ausführen
-   Frontend linten
-   Frontend bauen
-   relevante E2E-Tests ausführen

11. Fehler zuerst reproduzieren, danach beheben.
12. Vor größeren Strukturänderungen Tests ergänzen.
13. Keine neue Dependency einführen, wenn das Problem mit dem
    vorhandenen Stack sauber lösbar ist.
14. Keine Warnungen oder fehlschlagenden Tests einfach deaktivieren.
15. `skipLibCheck`, Test-Skips oder Linter-Ausnahmen nicht als Ersatz
    für eine echte Fehlerbehebung verwenden.

------------------------------------------------------------------------

# Analyse des aktuellen Standes

Die folgende Analyse basiert auf dem aktuellen `main`-Branch und den
vorhandenen Build-/Runtime-Konfigurationen.

## Positiv

Das Projekt besitzt bereits eine brauchbare technische Grundlage:

-   Java 21
-   Spring Boot 4.0.4
-   PostgreSQL
-   JPA mit deaktiviertem Open Session in View
-   Flyway
-   Bean Validation
-   Spring Security
-   JWT
-   MapStruct
-   Actuator
-   JaCoCo
-   Next.js 16.2.1
-   React 19.2.4
-   TypeScript `strict: true`
-   Playwright-E2E-Tests
-   Docker Multi-Stage Builds
-   nicht privilegierte Benutzer in Backend- und Frontend-Container
-   PostgreSQL-Healthcheck
-   reproduzierbare Frontend-Installation über `npm ci`

Das ist deutlich besser als ein typischer früher Prototyp. Die größten
Schwachstellen liegen deshalb weniger bei der Wahl des Stacks als bei
**Hardening, Teststrategie, Architekturgrenzen und reproduzierbaren
Deployments**.

------------------------------------------------------------------------

# Erkannte Schwachstellen / Risiken

## P0 -- Konfiguration und Secrets

Im Docker-Compose stehen Entwicklungs-Credentials und ein JWT-Secret
direkt in der Konfiguration.

Beispiele:

-   PostgreSQL-Benutzer/Passwort `fasswerk`
-   `JWT_SECRET=docker-dev-secret-change-me-please-at-least-32-chars`

Auch `application.yml` besitzt Defaultwerte für Datenbankzugang und
JWT-Secret.

Das ist für lokale Entwicklung bequem, darf aber nicht versehentlich als
produktionsfähige Konfiguration interpretiert werden.

### Ziel

Saubere Trennung:

-   Development
-   Test
-   Production

Produktionsbetrieb muss bei fehlenden Secrets **fail fast**.

------------------------------------------------------------------------

## P0 -- Docker baut das Backend ohne Tests

Im Backend-Dockerfile wird ausgeführt:

`mvn -B -DskipTests clean package`

Damit kann ein Docker-Image erfolgreich gebaut werden, obwohl Tests
fehlschlagen.

### Ziel

CI entscheidet über die Qualität des Builds. Ein Release/Image darf nur
nach erfolgreichem Quality Gate entstehen.

------------------------------------------------------------------------

## P0/P1 -- Testdatenbank unterscheidet sich vom Produktivsystem

Das Backend verwendet PostgreSQL produktiv, gleichzeitig ist H2 als
Testdependency vorhanden.

H2 bildet PostgreSQL nicht vollständig ab. Unterschiede können
insbesondere auftreten bei:

-   SQL-Dialekt
-   Constraints
-   Datentypen
-   Transaktionen
-   Locking
-   Zeit-/Datumsverhalten
-   Flyway-Migrationen

### Ziel

Persistenz- und Integrationstests gegen echtes PostgreSQL ausführen,
vorzugsweise über Testcontainers.

H2 darf höchstens für isolierte Tests verwendet werden, die keinerlei
PostgreSQL-Verhalten voraussetzen.

------------------------------------------------------------------------

## P1 -- Doppelte Spring-Konfiguration

Es existieren gleichzeitig:

-   `application.yml`
-   `application.properties`

`application.properties` enthält aktuell lediglich:

`spring.application.name=backend`

Der gleiche Wert steht bereits in YAML.

### Ziel

Eine eindeutige Konfigurationsquelle.

------------------------------------------------------------------------

## P1 -- Container-Orchestrierung

PostgreSQL besitzt einen Healthcheck, das Backend jedoch nicht.

Das Frontend wartet lediglich auf:

`backend: condition: service_started`

Ein gestarteter Prozess bedeutet nicht, dass Spring Boot bereits
requestsicher ist.

### Ziel

Backend-Healthcheck über Actuator und Frontend-Abhängigkeit von einem
tatsächlich gesunden Backend.

------------------------------------------------------------------------

## P1 -- Actuator Exposure prüfen

Aktuell werden veröffentlicht:

-   health
-   metrics
-   prometheus

Das kann sinnvoll sein, muss aber für Produktion bewusst abgesichert
werden.

### Ziel

Prüfen:

-   Welche Endpoints sind öffentlich?
-   Welche Informationen werden preisgegeben?
-   Wird `/actuator/health` bewusst anonym zugelassen?
-   Müssen `metrics` und `prometheus` intern/authentifiziert sein?

------------------------------------------------------------------------

## P1 -- Docker Images verwenden `latest`

Compose verwendet:

-   `fasswerk/backend:latest`
-   `fasswerk/frontend:latest`

`latest` erschwert reproduzierbare Deployments und Rollbacks.

### Ziel

Immutable Image Tags verwenden, z. B. Commit SHA oder Release-Version.

------------------------------------------------------------------------

## P1 -- Frontend-Testpyramide

Das Frontend besitzt umfangreiche Playwright-E2E-Skripte, im
`package.json` ist aber kein Unit-/Component-Test-Runner erkennbar.

E2E-Tests sind wertvoll, sollten aber nicht jede kleine Businessregel
absichern müssen.

### Ziel

Prüfen, welche reine Logik vorhanden ist. Für relevante isolierbare
Logik gezielte schnelle Tests ergänzen. Kein Testframework nur für
triviale Komponenten einführen.

------------------------------------------------------------------------

## P1 -- Backend-Testabdeckung nicht nur messen, sondern bewerten

JaCoCo erzeugt bereits Reports. Im sichtbaren Maven-Setup ist jedoch
kein Coverage-Gate definiert.

### Ziel

Nicht blind eine hohe Prozentzahl erzwingen. Stattdessen kritische
Domain- und Application-Services gezielt absichern und anschließend eine
realistische Mindestabdeckung für neue/geänderte Businesslogik
definieren.

------------------------------------------------------------------------

## P1 -- Architekturgrenzen verifizieren

FassWerk enthält mehrere fachliche Bereiche, unter anderem
Authentifizierung, Reservierung, Verkauf/Abrechnung, Bestand und
Administration.

Codex soll prüfen, ob diese Bereiche tatsächlich voneinander getrennt
sind oder ob Controller, Repositories und Services quer durch die
Anwendung gekoppelt sind.

### Ziel

Ein **modularer Monolith**, kein unnötiges Microservice-System.

Bevorzugte Richtung:

`domain -> application -> infrastructure/web`

Nicht:

`controller -> riesiger service -> beliebige repositories`

------------------------------------------------------------------------

# PHASE 1 -- Baseline herstellen

## Aufgabe

Noch nichts groß refactoren.

Analysiere zuerst das komplette Repository.

Erstelle:

`docs/architecture/BASELINE.md`

Dokumentiere darin:

-   vollständige Modul-/Package-Struktur
-   Backend-Domänen
-   Frontend-Routen
-   REST-Endpunkte
-   Datenbanktabellen
-   Flyway-Migrationen
-   Security-Konzept
-   Rollen/Berechtigungen
-   externe Integrationen
-   Mail
-   QR
-   PDF
-   Reservierungsfluss
-   Verkaufs-/Abrechnungsfluss
-   Bestandsfluss
-   vorhandene Tests

Suche zusätzlich nach:

-   TODO
-   FIXME
-   HACK
-   deaktivierten Tests
-   `@Disabled`
-   leeren Catch-Blöcken
-   `catch (Exception ...)`
-   `any` in TypeScript
-   `@ts-ignore`
-   ESLint-Disable
-   duplizierter Businesslogik
-   Controller mit Businesslogik
-   Services mit zu vielen Verantwortlichkeiten
-   direktem Repository-Zugriff über fachliche Grenzen hinweg

## Baseline-Kommandos

Backend:

``` bash
cd backend
./mvnw test
./mvnw verify
```

Falls kein Maven Wrapper vorhanden ist:

``` bash
mvn test
mvn verify
```

Frontend:

``` bash
cd frontend
npm ci
npm run lint
npm run build
```

E2E nur wenn die benötigte Umgebung reproduzierbar gestartet werden
kann:

``` bash
npm run test:e2e:critical
```

## Ergebnis

Erstelle zusätzlich:

`docs/architecture/TECH_DEBT.md`

Format:

  ID   Severity   Bereich   Problem   Risiko   vorgeschlagene Lösung
  ---- ---------- --------- --------- -------- -----------------------

Severity:

-   P0 = Security/Data Loss/Build blocker
-   P1 = hohe technische Schuld
-   P2 = Wartbarkeit
-   P3 = Nice-to-have

### Gate

Keine Phase 2, solange die Baseline nicht reproduzierbar ist.

------------------------------------------------------------------------

# PHASE 2 -- Konfiguration und Secret Hardening

## Aufgaben

1.  Entferne produktionsgefährliche Secret-Defaults.
2.  Development-Konfiguration darf weiterhin einfach startbar sein.
3.  Production muss bei fehlendem JWT-Secret scheitern.
4.  Datenbank-Credentials nicht als produktionsfähige Defaults
    behandeln.
5.  Prüfe, ob `.env*`, Secrets oder Credentials committed wurden.
6.  Dokumentiere erforderliche Environment-Variablen in `.env.example`.
7.  `.env.example` darf ausschließlich Dummywerte enthalten.
8.  Entferne die redundante `application.properties`, sofern keine echte
    Funktion davon abhängt.

## Wichtig

Keine Secrets generieren und committen.

### Tests

Ergänze einen Test, der sicherstellt, dass eine produktionsnahe
Konfiguration ohne erforderliche Security-Konfiguration nicht
stillschweigend startet.

### Gate

-   keine echten Secrets im Repo
-   Dev-Setup funktioniert
-   Production fail-fast
-   Backendtests grün

------------------------------------------------------------------------

# PHASE 3 -- PostgreSQL-Teststrategie

## Ziel

Datenbankverhalten gegen dieselbe Datenbankfamilie testen wie
Produktion.

## Aufgaben

1.  Prüfe alle Repository- und Integrationstests.
2.  Identifiziere Tests, die H2 verwenden.
3.  Führe Testcontainers PostgreSQL für DB-relevante Integrationstests
    ein.
4.  Flyway muss beim Integrationstest ausgeführt werden.
5.  Verifiziere alle Migrationen gegen einen leeren
    PostgreSQL-Container.
6.  Prüfe Constraints und Unique Constraints.
7.  Prüfe insbesondere konkurrierende Schreibvorgänge bei:
    -   Reservierungen
    -   Rechnungen/Zahlungen
    -   Bestand
    -   Refresh Tokens

## Nicht tun

Nicht sämtliche Unit Tests zu Integrationstests machen.

### Gate

Ein frischer PostgreSQL-Container kann ausschließlich aus
Flyway-Migrationen den vollständigen erwarteten Schema-Stand erzeugen.

------------------------------------------------------------------------

# PHASE 4 -- Domain Map und Modulgrenzen

## Ziel

FassWerk als modularen Monolithen strukturieren.

Codex soll zunächst eine Domain Map erstellen.

Mögliche fachliche Kontexte sind nur Ausgangshypothesen und müssen am
tatsächlichen Code überprüft werden:

-   Identity & Access
-   Reservation
-   Table / Service
-   Ordering
-   Billing
-   Inventory
-   Administration
-   Reporting

## Aufgaben

Für jeden Kontext dokumentieren:

-   Aggregate / Entities
-   Value Objects
-   Application Services / Use Cases
-   Repositories
-   REST Adapter
-   fachliche Events
-   erlaubte Abhängigkeiten

Erstelle:

`docs/architecture/DOMAIN_MAP.md`

## Refactoring

Nur klare Grenzverletzungen schrittweise beheben.

Keine Paketverschiebung des gesamten Projekts in einem Commit.

### Gate

Für jede neu geänderte Funktion ist klar, welchem fachlichen Kontext sie
gehört.

------------------------------------------------------------------------

# PHASE 5 -- Controller und API Layer

## Aufgaben

Alle Controller überprüfen.

Controller dürfen hauptsächlich:

1.  Request entgegennehmen
2.  validieren
3.  Auth-Kontext bestimmen
4.  Use Case aufrufen
5.  Response erzeugen

Suche nach:

-   Businessberechnungen
-   Repository-Aufrufen
-   Transaktionslogik
-   langen Methoden
-   Entity-Rückgaben
-   inkonsistenten HTTP-Statuscodes

## Fehlerbehandlung

Zentralisiere Fehler über `@RestControllerAdvice`, falls noch nicht
sauber vorhanden.

Definiere konsistente Fehlerantworten, vorzugsweise auf Basis von
Problem Details / RFC 9457, soweit sinnvoll mit Spring Boot umsetzbar.

Fehler dürfen keine:

-   Stacktraces
-   SQL-Details
-   Secrets
-   internen Implementierungsdetails

an Clients ausgeben.

### Gate

Controller sind dünn und relevante API-Fehler besitzen Tests.

------------------------------------------------------------------------

# PHASE 6 -- Security Review

## Aufgaben

Prüfe:

-   JWT-Erstellung
-   JWT-Verifikation
-   Issuer-Prüfung
-   Ablaufzeiten
-   Refresh-Token-Rotation
-   Token-Reuse
-   Logout/Revocation
-   Passwort-Hashing
-   Authorization
-   Rollenprüfung
-   CORS
-   CSRF-Konzept
-   Security Headers
-   Rate-Limit-Bedarf für Auth-Endpunkte
-   Logging sensibler Daten
-   Actuator Security

## Besonders wichtig

Suche nach Endpunkten, bei denen lediglich das Frontend Funktionen
versteckt, während das Backend keine entsprechende Autorisierung
erzwingt.

Backend-Autorisierung ist maßgeblich.

## Tests

Für geschützte Endpunkte mindestens:

-   anonymous -\> abgelehnt
-   falsche Rolle -\> abgelehnt
-   erlaubte Rolle -\> erlaubt

### Gate

Keine sicherheitsrelevante Entscheidung hängt ausschließlich vom
Frontend ab.

------------------------------------------------------------------------

# PHASE 7 -- Reservierungs-Domain

## Aufgaben

Analysiere den kompletten Reservation Lifecycle.

Prüfe:

-   Erstellung
-   Änderung
-   Stornierung
-   Zeitüberschneidungen
-   Tischzuordnung
-   Kapazität
-   Zeitzonen
-   Business Date
-   Mail
-   QR-Verwendung
-   parallele Requests

Definiere Invarianten explizit im Domain-/Application-Layer.

### Tests

Erzeuge Tests für:

-   gültige Reservierung
-   ungültiges Zeitfenster
-   Überschneidung
-   konkurrierende Reservierung
-   Stornierung
-   Änderung
-   Zeitzonen-/Tagesgrenzen

### Gate

Reservierungsregeln sind nicht auf Controller, React-Komponenten und
Datenbankabfragen verteilt.

------------------------------------------------------------------------

# PHASE 8 -- Billing / Ordering / Inventory

## Ziel

Finanz- und Bestandsänderungen müssen deterministisch und transaktional
sein.

## Aufgaben

Prüfe:

-   Bestellereignisse
-   Rechnungsstatus
-   Split Payments
-   Rundung
-   Money-Datentypen
-   Bestandsabbuchung
-   Doppelverarbeitung
-   Idempotenz
-   Transaktionsgrenzen

Geldbeträge niemals über `double`/`float` berechnen.

Verwende vorhandene geeignete Decimal-/Money-Repräsentation.

## Tests

Insbesondere:

-   mehrere Positionen
-   Rundung
-   Split Payment
-   Wiederholung desselben Requests/Event
-   Bestand genau auf Null
-   nicht ausreichender Bestand
-   Transaktions-Rollback

### Gate

Ein fehlgeschlagener Zahlung-/Bestandsvorgang darf keinen halbfertigen
fachlichen Zustand hinterlassen.

------------------------------------------------------------------------

# PHASE 9 -- Frontend Architektur

## Aufgaben

Analysiere App Router Struktur und Komponenten.

Identifiziere:

-   übergroße Client Components
-   unnötiges `"use client"`
-   duplizierte Fetch-Logik
-   duplizierte DTO-Typen
-   Businesslogik in JSX
-   inkonsistentes Error Handling
-   inkonsistentes Loading Handling
-   fehlende Error Boundaries
-   `any`
-   unsichere Type Assertions

## Zielstruktur

Nicht blind anwenden, sondern dem tatsächlichen Projekt anpassen:

``` text
app/
features/
  reservation/
  billing/
  inventory/
components/
lib/
```

Feature-Code soll möglichst bei seinem Feature liegen.

### Gate

UI-Komponenten enthalten keine komplexen fachlichen Berechnungen.

------------------------------------------------------------------------

# PHASE 10 -- API Contract

## Aufgabe

Backend und Frontend dürfen nicht unbemerkt auseinanderlaufen.

Da Springdoc bereits vorhanden ist:

1.  OpenAPI-Ausgabe prüfen.
2.  DTOs vollständig dokumentieren.
3.  Entscheiden, ob Frontend-Typen aus OpenAPI generiert werden sollen.
4.  Falls ja: reproduzierbaren Generator integrieren.
5.  Falls nein: Contract Tests als Alternative dokumentieren.

Keine zweite Wahrheit neben dem Backend-Vertrag schaffen.

### Gate

Breaking API Changes werden beim Build oder in Tests sichtbar.

------------------------------------------------------------------------

# PHASE 11 -- Docker Hardening

## Aufgaben

Positiv erhalten:

-   Multi-Stage Builds
-   non-root User

Verbessern:

1.  Backend-Healthcheck ergänzen.
2.  Frontend nicht nur von `service_started` abhängig machen.
3.  `latest` für reproduzierbare Deployments ersetzen.
4.  Images nach Möglichkeit über immutable Version/SHA referenzieren.
5.  `.dockerignore` prüfen.
6.  unnötige Build-Dateien aus Images fernhalten.
7.  Container-Dateisystem und Schreibrechte prüfen.
8.  Shutdown-Verhalten prüfen.
9.  JVM Container Settings prüfen.

## Backend Build

Tests nicht dadurch umgehen, dass das Release-Artefakt unabhängig vom
Quality Gate erzeugt wird.

Bevorzugter CI-Fluss:

``` text
test
 -> verify
 -> frontend lint/build/test
 -> docker build
 -> image scan
 -> publish
```

### Gate

Ein Release-Image kann nicht aus einem Commit veröffentlicht werden,
dessen Quality Gate fehlgeschlagen ist.

------------------------------------------------------------------------

# PHASE 12 -- CI/CD

Falls noch keine ausreichende Pipeline vorhanden ist, ergänzen.

## Backend Job

``` bash
mvn -B verify
```

## Frontend Job

``` bash
npm ci
npm run lint
npm run build
```

## E2E

Kritische E2E-Flows gegen reproduzierbare Umgebung.

Vorhandenes Script:

`test:e2e:critical`

bevorzugt als Release-/Merge-Gate verwenden, sobald es stabil
reproduzierbar läuft.

## Zusätzlich prüfen

-   Dependency Caching
-   Dependabot/Renovate-Konzept
-   Dependency Vulnerability Scan
-   Container Scan
-   Upload der Testreports
-   JaCoCo Report
-   Playwright Report

### Gate

Ein roter Test darf kein Release erzeugen.

------------------------------------------------------------------------

# PHASE 13 -- Observability

## Aufgaben

Actuator ist bereits vorhanden.

Darauf aufbauen statt neues Monitoring im Anwendungscode zu erfinden.

Prüfe:

-   strukturierte Logs
-   Request/Correlation ID
-   Health
-   Metrics
-   Prometheus
-   DB Pool
-   HTTP Error Rates
-   Mailfehler
-   Reservierungsfehler

Keine personenbezogenen oder sicherheitsrelevanten Daten in Logs.

### Gate

Ein Produktionsfehler muss anhand von Logs/Metriken diagnostizierbar
sein, ohne Secrets oder sensible Payloads zu protokollieren.

------------------------------------------------------------------------

# PHASE 14 -- Cleanup

Erst jetzt allgemeines Cleanup durchführen.

Entferne nach Verifikation:

-   Dead Code
-   unbenutzte Imports
-   redundante Konfiguration
-   veraltete Kommentare
-   unbenutzte Dependencies
-   duplizierte Helper
-   obsolete DTOs
-   tote API-Routen

Nicht entfernen, nur weil Codex die Verwendung nicht sofort findet. Erst
Referenzen und Runtime-Nutzung prüfen.

------------------------------------------------------------------------

# PHASE 15 -- Abschluss-Audit

Führe eine erneute vollständige Analyse durch.

Erstelle:

`docs/architecture/FINAL_AUDIT.md`

Vergleiche:

``` text
Baseline
vs.
Final State
```

Dokumentiere:

-   behobene P0
-   behobene P1
-   offene P1
-   P2/P3 Technical Debt
-   Testabdeckung
-   bekannte Risiken
-   nächste sinnvolle Schritte

------------------------------------------------------------------------

# Definition of Done

Das Refactoring gilt erst als abgeschlossen, wenn:

-   Backend vollständig baut
-   Backendtests grün sind
-   Flyway auf leerem PostgreSQL funktioniert
-   Frontend lintet
-   Frontend Production Build funktioniert
-   kritische E2E-Flows grün sind
-   keine produktionsfähigen Default-Secrets existieren
-   Security serverseitig erzwungen wird
-   Datenbankänderungen ausschließlich über Flyway erfolgen
-   kritische Businessregeln automatisiert getestet sind
-   Docker Images reproduzierbar versioniert werden können
-   Architektur dokumentiert ist

------------------------------------------------------------------------

# Arbeitsmodus für Codex

Bearbeite **immer nur eine Phase gleichzeitig**.

Zu Beginn jeder Phase:

1.  relevante Dateien lesen
2.  Ist-Zustand beschreiben
3.  konkrete Probleme nennen
4.  minimalen Änderungsplan formulieren

Dann implementieren.

Am Ende jeder Phase ausgeben:

``` text
PHASE X REPORT

Geänderte Dateien:
- ...

Behobene Probleme:
- ...

Neue/geänderte Tests:
- ...

Ausgeführte Prüfungen:
- ...

Ergebnis:
PASS / FAIL

Offene Risiken:
- ...

Empfohlener nächster Schritt:
PHASE X+1
```

Bei `FAIL`:

**Nicht mit der nächsten Phase fortfahren.**

Stattdessen Ursache beheben oder präzise dokumentieren, warum die Phase
nicht abgeschlossen werden kann.

------------------------------------------------------------------------

# Startauftrag an Codex

Beginne jetzt ausschließlich mit **PHASE 1 -- Baseline herstellen**.

Noch keine großflächigen Refactorings durchführen.

Analysiere das gesamte Repository, führe die vorhandenen Builds und
Tests aus und erstelle:

-   `docs/architecture/BASELINE.md`
-   `docs/architecture/TECH_DEBT.md`

Beende deine Arbeit anschließend mit dem `PHASE 1 REPORT`.

Warte danach auf die ausdrückliche Freigabe für Phase 2.
