# FassWerk – Projektdokumentation

## 1. Zweck und Scope
FassWerk ist eine Webanwendung fuer Gastronomie-Betriebe mit Schwerpunkt auf:
- Reservierungen inkl. QR-Check-in
- Tischabrechnung (Bon-Flow)
- Lagerverwaltung und automatische Bestandsabzuege
- Bar-Administration (Kategorien, Getraenke, Varianten, Volumenpreise)
- Schicht-/Lohnabrechnung
- Nachbestelllogik auf Basis realer Verkaufsdaten
- Session- und Rollenverwaltung

Die Architektur besteht aus einem Spring-Boot-Backend und einem Next.js-Frontend (App Router) mit API-Proxy-Schicht.

---

## 2. Systemarchitektur

## 2.1 High-Level
- **Frontend:** Next.js 16 (`frontend/`)
  - UI-Routen unter `frontend/app/*`
  - serverseitige Proxy-Routen unter `frontend/app/api/*`
- **Backend:** Spring Boot 4 (`backend/`)
  - REST-APIs unter `/api/*`
  - Auth/Security, Domain-Services, Datenpersistenz
- **Datenbank:** PostgreSQL (Flyway-Migrationen)
- **Container-Setup:** `docker-compose.yml` (Postgres, Backend, Frontend)

## 2.2 Kommunikationsfluss
1. Browser -> Next.js UI
2. Next.js UI -> Next.js API-Route (`/api/...`)
3. Next.js API-Route -> Backend (`BACKEND_BASE_URL`)
4. Backend -> PostgreSQL

## 2.3 Auth-Architektur
- Login/Refresh/Logout laufen ueber Backend-Endpunkte (`/api/auth/*`).
- Frontend speichert Tokens als `HttpOnly` Cookies (`fw_access_token`, `fw_refresh_token`).
- `backendFetchWithAuth` in `frontend/lib/server-auth.ts` uebernimmt:
  - Bearer-Header
  - 401-Handling
  - automatische Refresh-Retry-Logik

---

## 3. Repo-Struktur

## 3.1 Root
- `backend/` – Spring Boot Backend
- `frontend/` – Next.js Frontend
- `docs/` – Fach-/Betriebsdokumentation
- `docker-compose.yml` – lokales Compose-Deployment

## 3.2 Backend-Module (`backend/src/main/java/org/thomcgn/backend`)
- `auth/` – Login, Refresh, Sessions, JWT
- `reservation/` – Reservierungslogik, QR-Flow, Status
- `billing/` – Tischbon/Order-Lifecycle
- `inventory/` – Lager, Bewegungen, Nachbestellung, Sales-Tracking
- `menu/` – Kategorien/Getraenke/Varianten/Volumenpreise
- `shift/` – Schicht- und Lohnabrechnung
- `table/` – Tischverwaltung
- `report/` – PDF/Revenue-Reports
- `config/`, `common/`, `qr/` – Querfunktionen

## 3.3 Frontend-Routen (`frontend/app`)
- `bookings/` – Reservierungen
- `table-billing/` – Tische/Bons
- `inventory/` – Lagerverwaltung
- `bar-admin/` – Karten- und Variantenverwaltung
- `shift-settlement/` – Lohn-/Schichtmodul
- `sales-configuration/` – Nachbestell-/Business-Day-Konfig
- `sessions/` – Sessionverwaltung
- `ops/auth-metrics/` – Kennzahlen/Ops-Ansicht

---

## 4. Kern-Workflows

## 4.1 Reservierung
- Erstellung ueber `POST /api/reservations`
- Aktueller Status-Flow:
  - `PENDING` (Anfrage)
  - `CONFIRMED` (bestaetigt)
  - `CHECKED_IN` (angekommen)
  - `REJECTED` (abgelehnt)
  - weitere historische/technische Stati: `NO_SHOW`, `EXPIRED`, `CANCELLED`, `COMPLETED`
- Staff-Aktionen:
  - `POST /api/reservations/{id}/confirm`
  - `POST /api/reservations/{id}/check-in`
  - `POST /api/reservations/{id}/cancel` (mit optionalem Grund, Ablehnung)
- QR:
  - Tokenbasiert (`qrCodeToken`)
  - Scan-Endpoint vorhanden

## 4.2 Reservierungs-E-Mail-Entscheidungen
- Mail-Service: `ReservationMailService`
- Versand bei:
  - Bestaetigung (`sendConfirmedMail`)
  - Ablehnung (`sendRejectedMail`)
- Feature-Flags in `application.yml`:
  - `app.reservation.mail.enabled`
  - `app.reservation.mail.from`
  - SMTP via `spring.mail.*`

## 4.3 Tischabrechnung
- Open/Load Tischbon
- Positionen hinzufuegen/entfernen
- Split-Payment
- Abschluss (`close`)
- Bestandsabzug erfolgt transaktional im Backend

## 4.4 Lager & Nachbestellung
- Lagerartikel mit Verpackungs-/Mengenkonzept
- Bewegungsprotokoll
- Sales-Tracking und Reorder-Berechnung
- Konfiguration von Business-Day, Lookback, Safety-Factor, Lead-Time

## 4.5 Bar Admin
- CRUD fuer Kategorien/Getraenke/Varianten
- Batch-Anlage mehrerer Getraenke mit Varianten
- Volumenpreis-Anpassung (gezielt pro Volumen)

## 4.6 Schichtabrechnung
- Tagesbezogene Erfassung von:
  - Kassenstart
  - sonstigen Ausgaben
  - Mitarbeitenden (von/bis, Stundenlohn)
- Berechnung erwarteter Kassenbestand

---

## 5. Wichtige APIs (Auszug)

## 5.1 Auth (`/api/auth`)
- `POST /login`
- `POST /refresh`
- `POST /logout`
- `GET /sessions`
- `DELETE /sessions/{id}`
- `POST /logout-all`

## 5.2 Reservierung (`/api/reservations`)
- `POST /`
- `GET /?date=...`
- `GET /{id}`
- `POST /{id}/confirm`
- `POST /{id}/check-in`
- `POST /{id}/cancel`
- `POST /scan/{token}`
- `GET /{id}/qr-code`

## 5.3 Tischbon (`/api/table-orders`)
- `POST /open`
- `POST /{id}/items`
- `DELETE /{id}/items/{itemId}`
- `POST /{id}/split-payment`
- `POST /{id}/close`
- `GET /open/table/{tableId}`

## 5.4 Menu (`/api`)
- `GET/POST/PUT/DELETE /drink-categories`
- `GET/POST/PUT/DELETE /drinks`
- `GET/POST/PUT/DELETE /drink-variants`
- `GET/POST/PUT/DELETE /volume-prices`

## 5.5 Inventory (`/api/inventory`)
- CRUD + Adjust + Movements
- Defaults
- Sales-Tracking Endpunkte
- Reorder- und Configuration-Endpunkte

---

## 6. Security und Rollen
Konfiguriert in `backend/config/SecurityConfig.java`.

- **Public:** Reservierungsanlage (`POST /api/reservations`), Health, Login/Refresh
- **STAFF/ADMIN:** operative APIs (Reservierungen lesen/entscheiden, Tische, Bons, Shift)
- **ADMIN-only:** Menu-Admin, Volume-Price-Admin, Teile von Inventory/Reports/Ops

Hinweis: Rollenmodell ist im Code aktiv in den Request-Matchern hinterlegt und nicht nur UI-seitig.

---

## 7. Datenbank und Migrationen
- Flyway-Migrationen unter `backend/src/main/resources/db/migration`
- Basisschema in `V1__baseline_schema.sql`
- Weitere Features (u. a. Sales-Tracking) in nachfolgenden Migrationen
- Reservation-Status wird als String gespeichert (`status varchar(32)`), daher sind neue Enum-Werte wie `REJECTED` ohne zwingende Tabellenmigration nutzbar (sofern fachlich gewollt).

---

## 8. Betrieb / Deployment

## 8.1 Lokal mit Docker Compose
`docker-compose.yml` startet:
- `postgres` (Port 5432)
- `backend` (Port 8080)
- `frontend` (Port 3000)

## 8.2 Backend Konfiguration (Auszug)
- DB: `DB_URL`, `DB_USER`, `DB_PASSWORD`
- JWT: `JWT_SECRET`, `JWT_ISSUER`, Token-Laufzeiten
- QR: `QR_SCAN_BASE_URL`
- Mail: `spring.mail.*`, `RESERVATION_MAIL_ENABLED`, `RESERVATION_MAIL_FROM`

## 8.3 Frontend Konfiguration
- `BACKEND_BASE_URL` (default `http://localhost:8080`)

---

## 9. Entwicklungs-Runbook

## 9.1 Frontend
```bash
cd frontend
npm install
npm run dev
```

## 9.2 Backend
```bash
cd backend
./mvnw spring-boot:run
```

## 9.3 Compose
```bash
docker compose up --build
```

---

## 10. Qualitaet und aktueller Stand

## 10.1 Beobachtungen aus der Analyse
- Frontend ist funktional breit ausgebaut und nahe am operativen Einsatz.
- API-Proxy-Pattern im Frontend ist konsistent umgesetzt.
- Reservierungs-Entscheidungsflow (Bestaetigen/Ablehnen + Mail) ist vorhanden.
- Umfangreiche Feature-Dichte im Inventory/Sales-Tracking.

## 10.2 Technische Schulden / Risiken
- Teilweise Dokumentation ist historisch und nicht immer 1:1 mit dem aktuellen Code synchron.
- Mindestens ein bestehender Backend-Testfall im Inventory-Bereich war in frueheren Checks instabil/fehlerhaft (fachfremd zu Reservierung), sollte separat bereinigt werden.
- Einzelne API-Endpunkte wirken in Controllern als Platzhalter angelegt (z. B. Consumption-Metadata-GET war zeitweise als null-Placeholder implementiert); regelmaessige API-Audits empfohlen.

---

## 11. Empfehlungen (naechste Schritte)

## 11.1 Priorisierter Umsetzungsplan (30/60/90 Tage)

### Phase 1 (0-30 Tage) – API-Vertrag und Test-Basis
1. **API-Vertrag dokumentieren (OpenAPI/Swagger)**
   - Backend um OpenAPI-Generierung erweitern (z. B. springdoc).
   - Alle Kerncontroller sauber dokumentieren:
     - `ReservationController`, `TableOrderController`, `InventoryController`, `MenuController`, `ShiftSettlementController`, `AuthController`.
   - Build-Artefakt: versionierte OpenAPI-Datei (JSON/YAML) im CI erzeugen und ablegen.
   - Akzeptanzkriterium: Frontend kann gegen eine eindeutig versionierte API-Spezifikation entwickeln.

2. **E2E-Testgrundlage aufbauen (Playwright)**
   - Testdaten-Setup fuer reproduzierbare Flows definieren.
   - Erste kritische Flows automatisieren:
     - Reservierung `PENDING -> CONFIRMED/REJECTED -> CHECKED_IN`
     - Tischbon inkl. Split-Payment
     - Inventory-Abzug nach Order-Events
   - Akzeptanzkriterium: Tests laufen lokal und in CI stabil durch.

### Phase 2 (31-60 Tage) – Betriebssicherheit
3. **Betriebsdoku erweitern (Backup/Restore, Monitoring, Alerting)**
   - Backup/Restore Runbook fuer PostgreSQL dokumentieren:
     - Vollbackup, Restore in Staging, Recovery-Time-Ziel.
   - Monitoring-Baseline definieren:
     - Health, JVM, DB-Verbindungen, API-Error-Rate, Scheduler-Laufzeiten.
   - Alerting-Regeln fuer kritische Events erstellen:
     - API-Fehlerraten, Job-Ausfaelle, DB-Erreichbarkeit, Auth-Anomalien.
   - Akzeptanzkriterium: On-Call kann mit Doku einen Restore-Test und eine Incident-Erstreaktion durchfuehren.

4. **Datenqualitaets-Checks fuer Sales/Reorder-Scheduler**
   - Plausibilitaetschecks in Schedulern einbauen (z. B. negative/inkonsistente Verbrauchswerte blocken).
   - Taegliche Data-Quality-Reports erfassen:
     - fehlende Verbrauchsdaten,
     - Ausreisser in Nachbestellmengen,
     - abgebrochene Joblaeufe.
   - Akzeptanzkriterium: Auffaellige Daten werden automatisch markiert und nachvollziehbar protokolliert.

### Phase 3 (61-90 Tage) – Prozessreife
5. **Release-Notes-Prozess fuer Fachaenderungen etablieren**
   - Pflichttemplate fuer Releases einfuehren mit:
     - Fachliche Auswirkungen,
     - API-/Datenmodell-Aenderungen,
     - Migrationshinweise,
     - Rollback-Option.
   - Spezieller Fokus auf Aenderungsklassen:
     - Statusmodell (z. B. `REJECTED`),
     - Preislogik,
     - Reservierungsregeln.
   - Akzeptanzkriterium: Jede produktive Auslieferung hat nachvollziehbare, auditierbare Release Notes.

## 11.2 Konkrete Deliverables
- `docs/api/openapi-v<version>.yaml` + CI-Artefakt (`.github/workflows/ci.yml`)
- `backend/scripts/export-openapi.sh` fuer lokalen versionierten Export
- `frontend/e2e/*.spec.ts` fuer die drei kritischen End-to-End-Flows
- `frontend/e2e/support/test-data.ts` + `frontend/e2e/support/mock-api.ts` fuer reproduzierbare Testdaten
- `docs/operations/backup-restore.md`
- `docs/operations/monitoring-alerting.md`
- `docs/operations/data-quality-checks.md`
- `docs/releases/RELEASE_TEMPLATE.md`

## 11.3 Verantwortlichkeiten (Vorschlag)
- **Backend:** OpenAPI, Scheduler-Checks, Monitoring-Metriken
- **Frontend:** E2E-Flows und API-Vertragsabgleich
- **Ops/DevOps:** Backup/Restore, Alerting, Dashboards
- **Product/Tech Lead:** Release-Notes-Freigabe und fachliche Aenderungsbewertung

---

## 12. Begriffe
- **BFF:** Backend-for-Frontend (hier: Next.js API-Routen als Proxy)
- **No-Show:** Reservierung nicht rechtzeitig wahrgenommen
- **Lead-Time:** Lieferzeit fuer Nachbestellung
- **Safety Factor:** Sicherheitsbestand-Multiplikator

---

## 13. Stand dieser Doku
- Stand: 2026-03-25
- Quelle: Code-Analyse von `backend/`, `frontend/`, `docs/`, `docker-compose.yml`
- Datei: `PROJEKT-DOKUMENTATION.md`

