# Copilot-Briefing: Gastro-Buchungssystem mit Warenwirtschaft, Getränkekarte und Tischabrechnung

## Ziel
Baue eine produktionsnahe Webanwendung für ein **Gastro-Buchungssystem** mit:
- Reservierungen
- Dashboard für Verwaltung
- Kalenderansicht
- Konfiguration von Öffnungszeiten und Buchungsslots
- Getränkekarte
- Tischabrechnung
- Warenwirtschaft / Bestandsführung
- QR-Code Check-in
- No-Show-Logik
- Bestellvorschlägen bei Mindestbestand
- PDF-Export für Bestelllisten

## Tech-Stack
- **Backend:** Spring Boot
- **Frontend:** Next.js
- **UI:** shadcn/ui
- **Icons:** lucide-react
- **Datenbank:** PostgreSQL
- **Auth:** Spring Security + JWT oder Session-basierte Auth
- **QR-Code:** serverseitig generiert
- **PDF-Export:** serverseitig erzeugt
- **Deployment-ready:** Docker / Docker Compose

## Produktziel
Die Anwendung soll einem Gastronomiebetrieb ermöglichen, Reservierungen anzunehmen, Gäste per QR-Code einzuchecken, Tische abzurechnen und dabei automatisch Getränkebestände zu reduzieren. Zusätzlich soll das System frühzeitig melden, welche Getränke nachbestellt werden müssen, inklusive exportierbarer Bestelllisten als PDF.

---

# 1. Rollen & Berechtigungen

## Rollen
### Admin
- Vollzugriff auf alle Bereiche
- Öffnungszeiten konfigurieren
- Buchungsslots konfigurieren
- Getränkekarte konfigurieren
- Lagerbestand pflegen
- Mindestbestände definieren
- Produkte / Liefergebinde verwalten
- PDF-Bestelllisten exportieren
- Benutzer / Staff verwalten

### Staff
- Reservierungen einsehen
- Gäste per QR-Code scannen und einchecken
- Tische verwalten
- Bestellungen auf Tische buchen
- Tisch abrechnen
- Status von Reservierungen sehen

### Gast
- Kann verfügbare Reservierungszeiten sehen
- Reservierung anlegen
- Erhält bei erfolgreicher Reservierung einen QR-Code

---

# 2. Hauptmodule

## 2.1 Reservierungssystem

### Anforderungen
Ein Gast kann eine Reservierung anlegen, sofern für den gewünschten Zeitraum noch Kapazität vorhanden ist.

### Pflichtfelder bei Reservierung
- Gastname
- Datum
- Uhrzeit
- optional: Personenzahl
- optional: Kontaktmöglichkeit (E-Mail oder Telefon)

### Buchungslogik
- Buchbare Zeiträume werden im Dashboard konfiguriert
- Öffnungszeiten definieren, wann Buchungen grundsätzlich möglich sind
- Pro Tag und Zeitfenster muss geprüft werden, ob noch Tische / Plätze frei sind
- Reservierungen dürfen nur innerhalb konfigurierter Öffnungszeiten und Slots erfolgen
- Reservierung erhält Status:
  - `PENDING`
  - `CONFIRMED`
  - `CHECKED_IN`
  - `NO_SHOW`
  - `EXPIRED`
  - `CANCELLED`
  - `COMPLETED`

### QR-Code nach Buchung
Wenn Platz verfügbar ist und die Reservierung erfolgreich angelegt wurde:
- QR-Code generieren
- QR-Code eindeutig der Reservierung zuordnen
- Gast erhält QR-Code zur Bestätigung
- QR-Code muss am Reservierungstag vom Staff per Smartphone scanbar sein

### Check-in per QR-Code
- Staff öffnet mobile Scan-Ansicht im Browser
- Scan des QR-Codes ruft Reservierung auf
- Staff kann Gast einchecken
- Nach Check-in wechselt Status auf `CHECKED_IN`
- Es soll geprüft werden, ob Reservierung für den aktuellen Tag gültig ist

### No-Show-Regel
- Pro Betrieb konfigurierbare No-Show-Frist in Minuten
- Beispiel: Reservierung um 20:00 Uhr, Gast erscheint bis 20:15 Uhr nicht → Reservierung verfällt
- Nach Ablauf Statuswechsel auf `NO_SHOW` oder `EXPIRED`
- Plätze / Tische werden danach wieder freigegeben

---

## 2.2 Dashboard

### Funktionen
Erstelle ein modernes Admin-Dashboard mit Next.js + shadcn/ui.

### Dashboard-Bereiche
- Übersichtskarten
  - Reservierungen heute
  - Check-ins heute
  - No-Shows heute
  - Umsatz heute
  - Kritische Lagerbestände
- Kalenderansicht
  - Reservierungen pro Tag
  - Klick auf Tag zeigt Reservierungen und Auslastung
- Öffnungszeiten-Verwaltung
- Buchungsslots-Verwaltung
- Tischverwaltung
- Getränkekarten-Verwaltung
- Lagerverwaltung
- Bestellvorschläge / Nachbestellungen
- PDF-Exportbereich

### Kalender-Funktion
- Monats-, Wochen- und Tagesansicht
- Reservierungen visualisieren
- Farbliche Statuskennzeichnung
- Filter nach Status und Zeitraum

### Öffnungszeiten-Konfiguration
Konfigurierbar pro Wochentag:
- Tag
- geöffnet / geschlossen
- Startzeit
- Endzeit
- optional mehrere Zeitfenster pro Tag

### Buchungsslot-Konfiguration
- Slotdauer konfigurierbar, z. B. 30 / 60 Minuten
- Reservierungen nur in definierten Slots zulassen
- optional Vorlaufzeit definieren
- optional maximale Reservierungsdauer

---

## 2.3 Getränkekarte

### Getränkekategorien
Beispiele:
- Bier
- Wein
- Cocktails
- Softdrinks
- Spirituosen

### Pro Getränk konfigurierbar
- Kategorie
- Getränkename
- Aktiv / inaktiv
- Beschreibung optional
- Bild optional

### Pro Volumenvariante konfigurierbar
Ein Getränk kann mehrere Volumen / Verkaufsvarianten haben.

Beispiele:
- Guinness
  - 0.5l → Anzeigename: `Pint`
  - 1.5l → Anzeigename: `Pitcher`

### Felder für Volumenvariante
- Anzeigename des Volumens, z. B. `Pint`, `Pitcher`, `Glas`, `Flasche`
- Liter / Milliliter-Menge
- Verkaufspreis
- SKU optional
- Aktiv / inaktiv

### Anforderungen
- CRUD für Kategorien
- CRUD für Getränke
- CRUD für Volumenvarianten
- Sortierung nach Kategorien
- Sichtbar im Frontend für Staff und optional Gastansicht

---

## 2.4 Tischverwaltung & Tischabrechnung

### Tischmodell
- Tischnummer / Name
- Kapazität
- Status:
  - frei
  - reserviert
  - belegt
  - abrechnungsbereit
- optional Bereich, z. B. Innen / Außen / Bar

### Tischabrechnung
Staff soll auf einem Tisch Bestellungen erfassen können.

### Anforderungen
- Tisch öffnen
- Getränke auf Tisch buchen
- Positionen mengenbasiert hinzufügen
- Positionen stornieren oder korrigieren mit Protokoll
- Zwischensumme berechnen
- Gesamtsumme berechnen
- Abrechnung abschließen
- optional Teilzahlung / Split Bill vorbereiten

### Wichtige Logik
Sobald ein Getränk auf einen Tisch gebucht wird:
- passender Lagerbestand wird reduziert
- Abzug erfolgt anhand der konfigurierten Volumenmenge

Beispiel:
- Lager: 3 Fässer Guinness à 30 Liter = 90 Liter Sollbestand
- Bestellung: 1x Guinness Pint = 0.5 Liter
- Neuer Sollbestand: 89.5 Liter

Diese Logik gilt für alle Getränke und Volumenvarianten.

---

## 2.5 Warenwirtschaft / Lagerbestand

## Ziel
Das Lager soll volumen- bzw. gebindebasiert geführt werden.

### Anforderungen
Jedes Getränk braucht eine Lagerdefinition.

### Mögliche Lagereinheiten
- Fass
- Kiste
- Flasche
- Karton
- Einzelflasche

### Beispiele
- Guinness: 3 Fässer à 30 Liter
- Kölsch: X Fässer à Y Liter
- Weißwein: 4 Kisten à 6 Flaschen à 0.75 Liter

### Benötigte Datenstruktur
Für jeden Lagerartikel:
- Referenz auf Getränk oder Getränkvariante
- Lagerartikelname
- Gebindetyp
- Anzahl Gebinde auf Lager
- Inhalt pro Gebinde
- Mengeneinheit (Liter, ml, Stück)
- aktueller Sollbestand gesamt
- Mindestbestand
- Warnschwelle
- empfohlene Nachbestellmenge
- Lieferant optional

### Bestandslogik
- Aus Verkäufen / Tischbuchungen Bestand automatisch reduzieren
- Historie aller Bestandsbewegungen speichern
- Manuelle Korrekturen ermöglichen
- Gründe protokollieren: Verkauf, Schwund, Bruch, Inventur, Korrektur

### Warn- & Nachbestelllogik
Wenn Bestand unter Warnschwelle oder Mindestbestand fällt:
- Hinweis im Dashboard anzeigen
- Kennzeichnen:
  - was bestellt werden muss
  - wann der Schwellwert unterschritten wurde
  - wie viel empfohlen wird

### Beispielausgabe
- 12 Fässer Kölsch nachbestellen
- 4 Kisten Weißwein nachbestellen
- 2 Kartons Tonic Water nachbestellen

### PDF-Export
Erzeuge PDF-Export für Bestelllisten mit:
- Datum
- Lagerartikel
- aktueller Bestand
- Mindestbestand
- empfohlene Nachbestellmenge
- Gebindetyp
- optionale Gruppierung nach Kategorie / Lieferant

---

# 3. Technische Anforderungen

## Backend mit Spring Boot

### Baue folgende Bereiche
- REST API
- saubere Layer:
  - Controller
  - Service
  - Repository
  - Domain / Entity
  - DTO / Mapper
- Validierung
- Fehlerbehandlung
- Logging
- Security
- Audit-Felder (`createdAt`, `updatedAt`, `createdBy` optional)

### Empfohlene Packages
```text
com.example.gastro
  ├─ auth
  ├─ user
  ├─ reservation
  ├─ table
  ├─ menu
  ├─ inventory
  ├─ billing
  ├─ qr
  ├─ report
  ├─ config
  └─ common
```

### Wichtige Backend-Features
- OpenAPI / Swagger
- Pageable Listen
- Filter / Suche
- Statusübergänge sauber modellieren
- Transaktionale Lagerabzüge bei Bestellung
- Scheduler / Cron für No-Show-Timeouts
- PDF-Erstellung serverseitig
- QR-Code Generierung serverseitig

## Frontend mit Next.js

### Anforderungen
- App Router verwenden
- TypeScript verwenden
- shadcn/ui Komponenten nutzen
- lucide-react Icons nutzen
- responsive Layout
- mobile optimierte Staff-Ansichten

### Frontend-Bereiche
- Login
- Dashboard
- Kalender
- Reservierungen
- Tische
- Getränkekarte
- Lager / Warenwirtschaft
- Reports / PDF-Exporte
- QR-Scanner-Seite für Staff

### QR-Scanner
- Browserbasierter Scanner für Smartphone
- Kamera-Zugriff im Staff-Frontend
- Nach Scan Reservierungsdaten anzeigen
- Check-in Aktion auslösen

---

# 4. Datenmodell

## Entitäten

### User
- id
- name
- email
- passwordHash
- role
- active

### OpeningHour
- id
- weekday
- isOpen
- openTime
- closeTime
- optional secondOpenTime
- optional secondCloseTime

### BookingSlotConfig
- id
- slotDurationMinutes
- maxReservationDurationMinutes
- noShowGracePeriodMinutes
- bookingIntervalMode
- active

### TableEntity
- id
- name
- capacity
- area
- status
- active

### Reservation
- id
- guestName
- contactEmail optional
- contactPhone optional
- reservationDate
- reservationTime
- guestCount
- status
- qrCodeToken
- expiresAt
- checkedInAt
- assignedTableId optional
- createdAt

### DrinkCategory
- id
- name
- sortOrder
- active

### Drink
- id
- categoryId
- name
- description
- imageUrl optional
- active

### DrinkVariant
- id
- drinkId
- displayVolumeName
- volumeMl
- price
- sku optional
- active

### InventoryItem
- id
- name
- linkedDrinkId optional
- linkedDrinkVariantId optional
- packageType
- packagesInStock
- contentPerPackage
- contentUnit
- totalStockAmount
- reorderThreshold
- minimumStock
- recommendedReorderAmount
- supplier optional
- active

### InventoryMovement
- id
- inventoryItemId
- movementType
- amount
- unit
- reason
- referenceType
- referenceId
- createdAt
- createdBy optional

### TableOrder
- id
- tableId
- reservationId optional
- status
- openedAt
- closedAt

### TableOrderItem
- id
- tableOrderId
- drinkVariantId
- quantity
- unitPrice
- totalPrice
- deductedVolumeMl

### ReorderSuggestion
- id
- inventoryItemId
- currentStock
- threshold
- recommendedOrderQuantity
- generatedAt
- status

---

# 5. API-Anforderungen

## Reservierungen
- `POST /api/reservations`
- `GET /api/reservations`
- `GET /api/reservations/{id}`
- `POST /api/reservations/{id}/check-in`
- `POST /api/reservations/scan/{token}`
- `POST /api/reservations/{id}/cancel`

## Dashboard
- `GET /api/dashboard/summary`
- `GET /api/dashboard/calendar`

## Öffnungszeiten / Buchungskonfiguration
- `GET /api/opening-hours`
- `PUT /api/opening-hours`
- `GET /api/booking-config`
- `PUT /api/booking-config`

## Getränkekarte
- `GET /api/drink-categories`
- `POST /api/drink-categories`
- `PUT /api/drink-categories/{id}`
- `DELETE /api/drink-categories/{id}`
- `GET /api/drinks`
- `POST /api/drinks`
- `PUT /api/drinks/{id}`
- `DELETE /api/drinks/{id}`
- `GET /api/drink-variants`
- `POST /api/drink-variants`
- `PUT /api/drink-variants/{id}`
- `DELETE /api/drink-variants/{id}`

## Tische / Abrechnung
- `GET /api/tables`
- `POST /api/tables`
- `PUT /api/tables/{id}`
- `POST /api/table-orders/open`
- `POST /api/table-orders/{id}/items`
- `DELETE /api/table-orders/{id}/items/{itemId}`
- `POST /api/table-orders/{id}/close`

## Lager
- `GET /api/inventory`
- `POST /api/inventory`
- `PUT /api/inventory/{id}`
- `POST /api/inventory/{id}/adjust`
- `GET /api/inventory/movements`
- `GET /api/inventory/reorder-suggestions`
- `GET /api/reports/reorder-list.pdf`

---

# 6. Geschäftslogik, die zwingend sauber umgesetzt werden muss

## Reservierungskapazität
- Prüfe freie Tische / Plätze pro Zeitfenster
- Keine Überbuchung
- Berücksichtige Öffnungszeiten, Slots und Reservierungsdauer

## QR-Code
- QR-Code enthält nur sicheren Token, keine sensiblen Klartextdaten
- Token serverseitig validieren

## Check-in
- Nur am gültigen Reservierungstag akzeptieren
- Bereits eingecheckte Reservierungen nicht erneut einchecken

## No-Show
- Scheduler prüft laufend Reservierungen
- Wenn aktuelle Zeit > Reservierungszeit + konfigurierbare Karenzzeit und kein Check-in erfolgt ist:
  - Status auf `NO_SHOW` oder `EXPIRED`
  - Tisch / Kapazität freigeben

## Tischabrechnung und Lager
- Jede Bestellposition reduziert Bestand transaktional
- Bei Fehler darf keine inkonsistente Bestellung entstehen
- Bestandsbewegung sofort protokollieren

## Nachbestellung
- System erzeugt automatische Vorschläge bei Unterschreiten der Schwelle
- Exportierbar als PDF

---

# 7. UI/UX-Anforderungen

## Design
- modern
- clean
- dark/light mode optional
- gute Tabellen, Filter und Dialoge
- klare Statusbadges

## shadcn/ui Komponenten, die genutzt werden sollen
- Card
- Table
- Dialog
- Sheet
- Form
- Input
- Select
- Calendar
- Badge
- Tabs
- Dropdown Menu
- Toast

## lucide Icons Beispiele
- CalendarDays
- QrCode
- Beer
- Wine
- ShoppingCart
- ClipboardList
- AlertTriangle
- Users
- Table2
- ScanLine

---

# 8. Was Copilot konkret liefern soll

Bitte generiere ein vollständiges Projektgerüst und implementiere die Kernlogik für:

1. Spring-Boot-Backend mit sauberer Architektur
2. Next.js-Frontend mit App Router und shadcn/ui
3. PostgreSQL-Schema / Migrationen
4. Authentifizierung und Rollen
5. Reservierungsmodul inkl. QR-Code
6. Dashboard inkl. Kalender
7. Öffnungszeiten- und Slot-Konfiguration
8. Getränkekartenverwaltung
9. Tischverwaltung und Tischabrechnung
10. Lagerverwaltung mit automatischem Bestandsabzug
11. Warnungen für Mindestbestand
12. PDF-Export für Bestelllisten
13. Mobile Staff-Ansicht für QR-Scan
14. Seed-Daten für Demo
15. Docker-Setup

---

# 9. Erwartete Projektstruktur

## Backend
```text
backend/
  src/main/java/...
  src/main/resources/
    application.yml
    db/migration/
```

## Frontend
```text
frontend/
  app/
  components/
  lib/
  hooks/
  types/
```

---

# 10. Zusätzliche Implementierungsdetails

## Backend
- Nutze Flyway oder Liquibase für Migrationen
- Nutze MapStruct oder manuelle Mapper
- Nutze Bean Validation
- Nutze globale Exception-Handler
- Schreibe sinnvolle DTOs statt Entities direkt zu exposen

## Frontend
- Nutze React Hook Form + Zod
- Nutze TanStack Query für API-Calls
- Nutze server actions nur falls sinnvoll, primär saubere API-Integration
- Nutze Tabellen mit Filtern und Statusbadges

## Reports
- PDF mit Firmenname, Datum und gruppierten Nachbestellungen
- Druckfreundliches Layout

## Inventur / Nachbestellung
- Konfigurierbare Einheiten
- Konfigurierbare Warnschwellen
- Bestellvorschläge sollen auf Gebindeebene formuliert werden, nicht nur in Liter

Beispiel:
Nicht nur:
- 360 Liter Kölsch bestellen

Sondern bevorzugt:
- 12 Fässer Kölsch à 30 Liter bestellen

---

# 11. Akzeptanzkriterien

Das Projekt ist fertig, wenn:
- ein Gast Reservierungen anlegen kann
- nur freie Kapazitäten reservierbar sind
- QR-Code erzeugt und gescannt werden kann
- Staff Gäste einchecken kann
- No-Show-Logik Reservierungen automatisch verfallen lässt
- Getränkekarte vollständig verwaltbar ist
- Tische geöffnet und abgerechnet werden können
- jede Getränkebestellung Bestand reduziert
- Mindestbestände erkannt werden
- Nachbestellvorschläge im Dashboard sichtbar sind
- PDF-Bestelllisten exportiert werden können
- App responsive nutzbar ist

---

# 12. Konkrete Arbeitsanweisung an Copilot

Baue das Projekt end-to-end als Fullstack-Anwendung mit Spring Boot + Next.js. Starte mit dem Datenmodell, danach Backend-APIs, dann Frontend-Seiten und Admin-Dashboard. Implementiere zuerst die Kernprozesse Reservierung, QR-Check-in, Getränkekarte, Tischabrechnung und Lagerabzug. Achte darauf, dass Lagerbewegungen transaktional und nachvollziehbar gespeichert werden. Erstelle saubere, erweiterbare Architektur, vollständige CRUD-Strecken, Seed-Daten und Docker-Setup.

Liefere produktionsnahen Code, keine bloßen Platzhalter.
