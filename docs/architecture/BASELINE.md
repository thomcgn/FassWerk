# FassWerk: Phase 1 Baseline

Stand: 2026-09-27. Ausgangscommit: `4ebd166` (`Bar Admin fixes`).
Auftrag: `FASSWERK_CODEX_REFACTOR.md`, ausschliesslich Phase 1.
Zu Beginn war nur die Auftragsdatei unversioniert; keine vorhandenen Code-Aenderungen.
Diese Bestandsaufnahme beschreibt den gelesenen Code und tatsaechliche lokale Pruefungen,
keinen abgeschlossenen Security-Audit oder Produktionsfreigabe. Befunde und Prioritaeten:
[TECH_DEBT.md](TECH_DEBT.md).

## 1. Ist-Zustand und Arbeitsplan

Ein Maven-Backend und eine Next.js-Anwendung mit Backend-for-Frontend-Adaptern bilden
einen fachlich paketierten Monolithen. Es gibt keine getrennten Maven-Domaenenmodule.
Die Services koppeln zum Teil direkt an Repositories anderer Kontexte. Die groessten
Client-Komponenten verbinden Laden, Formulare, Validierung und Darstellung.

Der Ausgangsstand besitzt Default-Zugangsdaten, H2-Integrationstests ohne Flyway,
ein unvollstaendiges CI-Gate und gemockte Browsertests. Bestehende Betriebs- und
Feature-Dokumentation ist vorhanden; sie ist keine Verifikation der Implementierung.

Minimaler Plan fuer diese Phase:

1. Packages, Routen, Endpunkte, Schema, Security und Fachablaeufe aus dem Code erfassen.
2. Bestehende Test-, Lint- und Build-Kommandos ausfuehren; Fehler reproduzieren.
3. Kleine, nachweislich veraltete Testannahmen korrigieren und erneut pruefen.
4. Risiken, offene Nachweise und Gate-Ergebnis dokumentieren; danach stoppen.

## 2. Stack, Struktur und Umgebung

| Bereich | Beobachteter Stand |
| --- | --- |
| Backend | Java 21; Spring Boot 4.0.4; Maven Wrapper 3.9.14 |
| Persistenz | JPA/Hibernate; PostgreSQL-Treiber; Flyway; OSIV aus; DDL-Auto `none` |
| Weitere Backend-Bibliotheken | JJWT 0.12.7; ZXing 3.5.3; PDFBox 3.0.4; MapStruct 1.6.3; Springdoc 2.8.13; Lombok; Actuator |
| Frontend | Next.js 16.2.1; React 19.2.4; TypeScript strict; ESLint 9; Tailwind 4; Playwright |
| Laufzeit lokal | OpenJDK 21.0.10; Node 25.2.1; npm 11.7.0; Docker 29.2.1 |
| CI / Images | CI Node 22, Java 21; Docker Node 22, Java 21, PostgreSQL 16 |
| Testdatenbank | H2 PostgreSQL-Modus, Hibernate `create-drop`, Flyway deaktiviert |
| Konfigurationsquellen | `application.yml`, redundante `application.properties`, Test-YAML, Compose, `.env`-Startscript |

Lokale Node-Version weicht von CI/Docker ab; Ergebnisse sind fuer diese Umgebung
nachgewiesen, nicht fuer jede Toolchain. Normaler Sandbox-Terminalzugriff schlug mit
`mountinfo path is not absolute` fehl; Befehle wurden ueber freigegebenen Zugriff ausgefuehrt.
Ein lokaler PostgreSQL-Port 5432 war belegt. Bestehende Datenbanken wurden nicht veraendert;
kein Compose-Stack wurde gestartet. Browsertests starten ihren eigenen Next-Dev-Server.

```text
.github/workflows/ci.yml        Backend-OpenAPI-Export und kritische Browsertests
backend/pom.xml                einzelnes Maven-Modul
backend/.mvn/wrapper/          Maven-Wrapper-Konfiguration
backend/scripts/              lokaler Start und OpenAPI-Export
backend/src/main/java/        153 Java-Dateien, 7247 Zeilen zum Ausgangsstand
backend/src/main/resources/   Spring-Konfiguration und 13 SQL-Migrationen
backend/src/test/             7 Testklassen, 16 Tests, H2-Testprofil
frontend/app/                 10 Seiten, 47 Route-Handler, Layout und Styles
frontend/components/          Fachkomponenten, Navigation und UI-Bausteine
frontend/lib/                 Auth-Proxy, Cookies, URL-Konfiguration und Hilfsfunktionen
frontend/types/api.ts          manuell gepflegte API-Typen
frontend/e2e/                 13 Specs mit 17 Tests plus 4 Support-Dateien
frontend/public/              statische SVG-Assets
docs/                         Feature-, API-, Deployment- und Betriebsdokumentation
docker-compose.yml            PostgreSQL, Backend, Frontend
```

Root-Dokumentation: `PROJEKT-DOKUMENTATION.md`, `README-SALES-TRACKING.md`,
`CONFIGURATION-*`, `IMPLEMENTATION_*`, `CONFIG-DASHBOARD-SUMMARY.md` und der Refactoring-Auftrag.
Operations-Runbooks: `docs/operations/backup-restore.md`, `monitoring-alerting.md`.
IDE-Metadaten sind teilweise versioniert. Kein `AGENTS.md` im Repository gefunden.

### Vollstaendige Backend-Packages

Basis: `org.thomcgn.backend`. Klassen und Records einschliesslich DTOs:

| Package relativ zur Basis | Dateien ohne `.java` |
| --- | --- |
| `.` | `BackendApplication` |
| `auth` | `AppUserDetailsService`, `AuthCleanupProperties`, `JwtAuthenticationFilter`, `JwtProperties`, `JwtTokenService` |
| `auth.api` | `AuthController` |
| `auth.api.dto` | `LoginRequest`, `LoginResponse`, `LogoutRequest`, `RefreshTokenRequest`, `SessionResponse` |
| `auth.domain` | `AppUser`, `RefreshToken`, `RevokedAccessToken`, `UserRole` |
| `auth.repository` | `AppUserRepository`, `RefreshTokenRepository`, `RevokedAccessTokenRepository` |
| `auth.service` | `AuthService`, `ClientMetadata`, `DeviceFingerprintService`, `TokenCleanupJob` |
| `billing.api` | `TableOrderController` |
| `billing.api.dto` | `AddTableOrderItemRequest`, `OpenTableOrderRequest`, `SplitTableOrderItemRequest`, `SplitTableOrderPaymentRequest`, `SplitTableOrderPaymentResponse`, `TableOrderItemResponse`, `TableOrderResponse` |
| `billing.domain` | `TableOrder`, `TableOrderItem`, `TableOrderStatus` |
| `billing.repository` | `TableOrderItemRepository`, `TableOrderRepository` |
| `billing.service` | `TableOrderService` |
| `common.api` | `ApiErrorResponse`, `GlobalExceptionHandler`, `RequestCorrelationFilter` |
| `common.domain` | `BaseEntity` |
| `common.exception` | `ApiException`, `BadRequestException`, `ConflictException`, `NotFoundException` |
| `config` | `OpenApiConfig`, `SecurityConfig` |
| `inventory.api` | `InventoryController`, `ReorderOrderController` |
| `inventory.api.dto` | `ConsumptionMetadataRequest`, `ConsumptionMetadataResponse`, `DrinkSalesDailyResponse`, `DrinkSalesWeeklyResponse`, `InventoryAdjustmentRequest`, `InventoryItemRequest`, `InventoryItemResponse`, `InventoryMovementResponse`, `InventoryPackageDefaultsResponse`, `ReorderCalculationResponse`, `ReorderOrderRequest`, `ReorderOrderResponse`, `ReorderSuggestionResponse`, `SalesConfigurationRequest`, `SalesConfigurationResponse`, `SupplierRequest`, `SupplierResponse` |
| `inventory.config` | `InventoryDefaultsProperties` |
| `inventory.domain` | `ConsumptionMetadata`, `ContentUnit`, `DrinkSalesDaily`, `DrinkSalesWeekly`, `InventoryBusinessSettings`, `InventoryItem`, `InventoryMovement`, `InventoryMovementType`, `InventoryReferenceType`, `PackageType`, `ReorderCalculation`, `ReorderOrder`, `Supplier` |
| `inventory.repository` | `ConsumptionMetadataRepository`, `DrinkSalesDailyRepository`, `DrinkSalesWeeklyRepository`, `InventoryBusinessSettingsRepository`, `InventoryItemRepository`, `InventoryMovementRepository`, `ReorderCalculationRepository`, `ReorderOrderRepository`, `SupplierRepository` |
| `inventory.service` | `DrinkSalesTrackingService`, `InventoryService`, `ReorderCalculationService`, `ReorderOrderService`, `SalesConfigurationService` |
| `menu.api` | `MenuController` |
| `menu.api.dto` | `DrinkCategoryRequest`, `DrinkCategoryResponse`, `DrinkRequest`, `DrinkResponse`, `DrinkVariantRequest`, `DrinkVariantResponse`, `VolumePriceRequest`, `VolumePriceResponse`, `VolumePriceUpdateRequest` |
| `menu.domain` | `Drink`, `DrinkCategory`, `DrinkVariant`, `VolumePrice` |
| `menu.repository` | `DrinkCategoryRepository`, `DrinkRepository`, `DrinkVariantRepository`, `VolumePriceRepository` |
| `menu.service` | `MenuService` |
| `qr` | `QrCodeService`, `QrProperties` |
| `report.api` | `ReportController` |
| `report.api.dto` | `RevenueDayPointResponse`, `RevenueOverviewResponse` |
| `report.service` | `ReorderReportService`, `RevenueReportService` |
| `reservation.api` | `ReservationController` |
| `reservation.api.dto` | `CreateReservationRequest`, `ReservationDecisionRequest`, `ReservationResponse`, `ReservationScanResponse` |
| `reservation.domain` | `BookingIntervalMode`, `BookingSlotConfig`, `OpeningHour`, `Reservation`, `ReservationStatus` |
| `reservation.repository` | `BookingSlotConfigRepository`, `OpeningHourRepository`, `ReservationRepository` |
| `reservation.service` | `ReservationMailService`, `ReservationMapper`, `ReservationService` |
| `shift.api` | `ShiftSettlementController` |
| `shift.api.dto` | `ShiftSettlementRequest`, `ShiftSettlementResponse`, `ShiftWorkerEntryRequest`, `ShiftWorkerEntryResponse` |
| `shift.domain` | `ShiftSettlement`, `ShiftWorkerEntry` |
| `shift.repository` | `ShiftSettlementRepository`, `ShiftWorkerEntryRepository` |
| `shift.service` | `ShiftSettlementService` |
| `table.api` | `TableController` |
| `table.api.dto` | `TableRequest`, `TableResponse` |
| `table.domain` | `TableEntity`, `TableStatus` |
| `table.repository` | `TableRepository` |
| `table.service` | `TableService` |

### Frontend-Module

`app/` nutzt den App Router. Fachseiten liegen in `app/<feature>` und
`components/`; es gibt noch kein `features/`-Verzeichnis.
`components/ui` stellt Badge, Button, Card, Input, Label, Select, Tabs und Toaster bereit.
`components/navigation/app-nav.tsx` steuert Navigation anhand des Auth-Status.
`lib/server-auth.ts` buendelt Backend-Login, Refresh, Bearer-Weitergabe und Sessionverwaltung;
`lib/auth-cookies.ts`, `lib/config.ts`, `lib/utils.ts` und `lib/use-toast-feedback.ts`
enthalten die weiteren Hilfen. `types/api.ts` ist die gemeinsame lokale Typquelle.

### Vollstaendige Frontend-Hilfs- und Komponentenmodule

`frontend/components`:

- `frontend/components/consumption-metadata-form.tsx`
- `frontend/components/full-calendar-bookings.tsx`
- `frontend/components/fullcalendar-styles.ts`
- `frontend/components/navigation/app-nav.tsx`
- `frontend/components/reorder-calculation.tsx`
- `frontend/components/reorder-dashboard.tsx`
- `frontend/components/sales-configuration-dashboard.tsx`
- `frontend/components/sales-configuration-page.tsx`
- `frontend/components/table-detail-modal.tsx`
- `frontend/components/ui/app-toaster.tsx`
- `frontend/components/ui/badge.tsx`
- `frontend/components/ui/button.tsx`
- `frontend/components/ui/card.tsx`
- `frontend/components/ui/input.tsx`
- `frontend/components/ui/label.tsx`
- `frontend/components/ui/select.tsx`
- `frontend/components/ui/tabs.tsx`

`frontend/lib`:

- `frontend/lib/auth-cookies.ts`
- `frontend/lib/config.ts`
- `frontend/lib/server-auth.ts`
- `frontend/lib/use-toast-feedback.ts`
- `frontend/lib/utils.ts`

`frontend/types`:

- `frontend/types/api.ts`

## 3. Backend-Domaenen und Abhaengigkeiten

| Kontext | Verantwortung |
| --- | --- |
| auth / config | Benutzer, ADMIN/STAFF, JWT, Refresh-Sessions, Revocation, Securityfilter |
| reservation | Oeffnungszeiten, Slot-Konfiguration, Anfrage, Bestaetigung, Ablehnung, Check-in, No-show |
| table | Tische, Bereich, Aktivitaet und Belegungsstatus |
| menu | Kategorien, Getraenke, Varianten, volumenbasierte Standardpreise |
| billing | Tischbons, Positionen, Split Payment, bezahlt/unbezahlt, Archiv |
| inventory | Lager, Bewegungen, Tages-/Wochenverkaeufe, Nachbestellberechnung, Lieferanten, Bestellungen, Business Date |
| shift | Schichtabrechnung, Mitarbeiterzeiten, Loehne, erwarteter Kassenbestand |
| report | Umsatzuebersicht und Nachbestell-PDF |
| qr | QR-PNG-Erzeugung und Scan-Basis-URL |
| common | Basiseinheit, API-Fehler, Correlation-ID |

Kein gesonderter Administration-Kontext: Adminfunktionen verteilen sich auf menu,
inventory und Security. Keine fachlichen Event-Klassen oder Event-Bus gefunden;
Bestellung/Bestand/Statistik kommunizieren ueber synchrone Serviceaufrufe.

Direkte fachfremde Repository-Zugriffe:

| Service | Fremde Repositories |
| --- | --- |
| `TableOrderService` | `menu.DrinkVariantRepository`, `menu.VolumePriceRepository`, `reservation.ReservationRepository`, `table.TableRepository` |
| `InventoryService` | `menu.DrinkRepository`, `menu.DrinkVariantRepository` |
| `MenuService` | `billing.TableOrderItemRepository`, `inventory.InventoryItemRepository` |
| `RevenueReportService` | `billing.TableOrderItemRepository`, `billing.TableOrderRepository` |
| `ReservationService` | `table.TableRepository` |
| `ShiftSettlementService` | `billing.TableOrderItemRepository` |

Zusaetzlich ruft Billing den InventoryService auf; Inventory verarbeitet Menu-Entities.
Menu und Billing/Inventory sind damit wechselseitig gekoppelt. Das ist eine
belegte Modulgrenzen-Verletzung, kein Anlass fuer eine Komplettverschiebung in Phase 1.
Controller importieren keine Repositories. Der InventoryController orchestriert jedoch
mehrere Services und mappt deren Entities teilweise ausserhalb einer Service-Transaktion.

## 4. Frontend-Routen

Alle selbst implementierten Seiten, ohne das generierte Next.js-Not-found:

| Route | Datei | Zugang / Zweck |
| --- | --- | --- |
| `/bar-admin` | `frontend/app/bar-admin/page.tsx` | Getraenkeverwaltung, Cookie-Pruefung |
| `/bookings` | `frontend/app/bookings/page.tsx` | oeffentliche Buchung; Cookie-Praesenz schaltet Staff-Dashboard frei |
| `/inventory` | `frontend/app/inventory/page.tsx` | Lagerverwaltung, Cookie-Pruefung |
| `/login` | `frontend/app/login/page.tsx` | oeffentliche Anmeldung |
| `/ops/auth-metrics` | `frontend/app/ops/auth-metrics/page.tsx` | Umsatzdashboard unter historischem Auth-Metrics-Pfad; Cookie-Pruefung |
| `/` | `frontend/app/page.tsx` | oeffentliche Getraenkekarte |
| `/sales-configuration` | `frontend/app/sales-configuration/page.tsx` | Nachbestellung/Konfiguration, Cookie-Pruefung |
| `/sessions` | `frontend/app/sessions/page.tsx` | Sessionverwaltung, Cookie-Pruefung |
| `/shift-settlement` | `frontend/app/shift-settlement/page.tsx` | Schichtabrechnung; Clientseite, Datenzugriff ueber geschuetzte API |
| `/table-billing` | `frontend/app/table-billing/page.tsx` | Tischbons, Cookie-Pruefung |

Cookie-Praesenz auf einer Seite ist keine Rollen- oder Signaturpruefung. Die massgebliche
Autorisierung liegt im Backend. Keine eigenen `error.tsx`/`global-error.tsx` oder
`loading.tsx` gefunden; Lade-/Fehlerzustaende sind komponentenlokal.

### Vollstaendige Next.js-API-Adapter

Methoden aus den exportierten Route-Handlern; dynamische Parameter in Next-Syntax.
Die meisten Handler delegieren an denselben Backendpfad. Abweichungen danach.

| Route unter `frontend/app` | HTTP-Methoden |
| --- | --- |
| `/api/auth/login` | POST |
| `/api/auth/logout` | POST |
| `/api/auth/logout-all` | POST |
| `/api/auth/refresh` | POST |
| `/api/auth/sessions/[id]` | DELETE |
| `/api/auth/sessions` | GET |
| `/api/auth/status` | GET |
| `/api/drink-categories/[id]` | PUT, DELETE |
| `/api/drink-categories` | GET, POST |
| `/api/drink-variants/[id]` | PUT, DELETE |
| `/api/drink-variants` | GET, POST |
| `/api/drinks/[id]` | PUT, DELETE |
| `/api/drinks` | GET, POST |
| `/api/inventory/[id]/consumption-metadata` | GET, PUT |
| `/api/inventory/[id]/reorder-calculation` | GET |
| `/api/inventory/[id]` | PUT, DELETE |
| `/api/inventory/configuration/manual-day-close` | POST |
| `/api/inventory/configuration` | GET, PUT |
| `/api/inventory/defaults` | GET |
| `/api/inventory/reorder-calculations/below-threshold` | GET |
| `/api/inventory` | GET, POST |
| `/api/ops/auth-metrics` | GET |
| `/api/reorder/orders/inventory/[inventoryItemId]` | GET |
| `/api/reorder/orders` | GET, POST |
| `/api/reorder/suppliers/[id]` | PUT |
| `/api/reorder/suppliers/list` | GET |
| `/api/reorder/suppliers` | GET, POST |
| `/api/reports/reorder-list` | GET |
| `/api/reservations/[id]/cancel` | POST |
| `/api/reservations/[id]/check-in` | POST |
| `/api/reservations/[id]/confirm` | POST |
| `/api/reservations` | GET, POST |
| `/api/shift-settlements/[date]` | GET, PUT |
| `/api/shift-settlements` | GET |
| `/api/table-orders/[id]/close` | POST |
| `/api/table-orders/[id]/items/[itemId]` | DELETE |
| `/api/table-orders/[id]/items` | POST |
| `/api/table-orders/[id]/mark-unpaid` | POST |
| `/api/table-orders/[id]/reopen-unpaid` | POST |
| `/api/table-orders/[id]` | GET |
| `/api/table-orders/[id]/split-payment` | POST |
| `/api/table-orders/archive` | GET |
| `/api/table-orders/open` | POST |
| `/api/table-orders/open/table/[tableId]` | GET |
| `/api/tables` | GET, POST |
| `/api/volume-prices/[volumeMl]` | PUT, DELETE |
| `/api/volume-prices` | GET, POST |

Abweichungen: `GET /api/reorder/orders` delegiert nach `/api/reorder/orders/upcoming`;
`/api/reorder/suppliers/list` ist ein weiterer Lieferantenadapter;
`/api/reports/reorder-list` liefert das Backend-PDF `.pdf`;
`/api/ops/auth-metrics` aggregiert Actuator-Metriken;
`/api/auth/status` prueft Backend-Sessions und liefert nur `authenticated`, keine Rolle.
Backend-Scan/QR, Umsatzuebersicht, einige Sales-/Reorder-Endpunkte und Tisch-PUT
besitzen keinen korrespondierenden Next-Handler.

## 5. REST-Endpunkte des Backends

Vollstaendig aus den neun Controller-Klassen extrahiert; Queryparameter und DTO-Felder
stehen in den jeweiligen Klassen. Dies ist ein statisches Inventar, kein exportierter
oder zur Laufzeit auf Kompatibilitaet gepruefter OpenAPI-Vertrag.

| HTTP | Pfad | Controller |
| --- | --- | --- |
| POST | `/api/auth/login` | `AuthController` |
| POST | `/api/auth/refresh` | `AuthController` |
| POST | `/api/auth/logout` | `AuthController` |
| GET | `/api/auth/sessions` | `AuthController` |
| DELETE | `/api/auth/sessions/{id}` | `AuthController` |
| POST | `/api/auth/logout-all` | `AuthController` |
| POST | `/api/table-orders/open` | `TableOrderController` |
| POST | `/api/table-orders/{id}/items` | `TableOrderController` |
| DELETE | `/api/table-orders/{id}/items/{itemId}` | `TableOrderController` |
| POST | `/api/table-orders/{id}/close` | `TableOrderController` |
| POST | `/api/table-orders/{id}/mark-unpaid` | `TableOrderController` |
| POST | `/api/table-orders/{id}/reopen-unpaid` | `TableOrderController` |
| POST | `/api/table-orders/{id}/split-payment` | `TableOrderController` |
| GET | `/api/table-orders/{id}` | `TableOrderController` |
| GET | `/api/table-orders/open/table/{tableId}` | `TableOrderController` |
| GET | `/api/table-orders/archive` | `TableOrderController` |
| GET | `/api/inventory` | `InventoryController` |
| POST | `/api/inventory` | `InventoryController` |
| PUT | `/api/inventory/{id}` | `InventoryController` |
| DELETE | `/api/inventory/{id}` | `InventoryController` |
| POST | `/api/inventory/{id}/adjust` | `InventoryController` |
| GET | `/api/inventory/movements` | `InventoryController` |
| GET | `/api/inventory/reorder-suggestions` | `InventoryController` |
| GET | `/api/inventory/defaults` | `InventoryController` |
| GET | `/api/inventory/sales/daily` | `InventoryController` |
| GET | `/api/inventory/sales/weekly/{variantId}` | `InventoryController` |
| GET | `/api/inventory/{id}/reorder-calculation` | `InventoryController` |
| POST | `/api/inventory/{id}/calculate-reorder` | `InventoryController` |
| GET | `/api/inventory/reorder-calculations/below-threshold` | `InventoryController` |
| GET | `/api/inventory/{id}/reorder-calculations/history` | `InventoryController` |
| GET | `/api/inventory/{id}/consumption-metadata` | `InventoryController` |
| PUT | `/api/inventory/{id}/consumption-metadata` | `InventoryController` |
| GET | `/api/inventory/configuration` | `InventoryController` |
| PUT | `/api/inventory/configuration` | `InventoryController` |
| POST | `/api/inventory/configuration/manual-day-close` | `InventoryController` |
| POST | `/api/reorder/suppliers` | `ReorderOrderController` |
| GET | `/api/reorder/suppliers` | `ReorderOrderController` |
| PUT | `/api/reorder/suppliers/{id}` | `ReorderOrderController` |
| POST | `/api/reorder/orders` | `ReorderOrderController` |
| GET | `/api/reorder/orders/upcoming` | `ReorderOrderController` |
| GET | `/api/reorder/orders/by-date-range` | `ReorderOrderController` |
| GET | `/api/reorder/orders/inventory/{inventoryItemId}` | `ReorderOrderController` |
| PUT | `/api/reorder/orders/{id}/status` | `ReorderOrderController` |
| GET | `/api/drink-categories` | `MenuController` |
| POST | `/api/drink-categories` | `MenuController` |
| PUT | `/api/drink-categories/{id}` | `MenuController` |
| DELETE | `/api/drink-categories/{id}` | `MenuController` |
| GET | `/api/drinks` | `MenuController` |
| POST | `/api/drinks` | `MenuController` |
| PUT | `/api/drinks/{id}` | `MenuController` |
| DELETE | `/api/drinks/{id}` | `MenuController` |
| GET | `/api/drink-variants` | `MenuController` |
| POST | `/api/drink-variants` | `MenuController` |
| PUT | `/api/drink-variants/{id}` | `MenuController` |
| DELETE | `/api/drink-variants/{id}` | `MenuController` |
| GET | `/api/volume-prices` | `MenuController` |
| POST | `/api/volume-prices` | `MenuController` |
| PUT | `/api/volume-prices/{volumeMl}` | `MenuController` |
| DELETE | `/api/volume-prices/{volumeMl}` | `MenuController` |
| GET | `/api/reports/reorder-list.pdf` | `ReportController` |
| GET | `/api/reports/revenue-overview` | `ReportController` |
| POST | `/api/reservations` | `ReservationController` |
| GET | `/api/reservations` | `ReservationController` |
| GET | `/api/reservations/{id}` | `ReservationController` |
| POST | `/api/reservations/{id}/check-in` | `ReservationController` |
| POST | `/api/reservations/{id}/confirm` | `ReservationController` |
| POST | `/api/reservations/{id}/cancel` | `ReservationController` |
| POST | `/api/reservations/scan/{token}` | `ReservationController` |
| GET | `/api/reservations/{id}/qr-code` | `ReservationController` |
| GET | `/api/shift-settlements` | `ShiftSettlementController` |
| GET | `/api/shift-settlements/{date}` | `ShiftSettlementController` |
| PUT | `/api/shift-settlements/{date}` | `ShiftSettlementController` |
| GET | `/api/tables` | `TableController` |
| POST | `/api/tables` | `TableController` |
| PUT | `/api/tables/{id}` | `TableController` |

75 fachliche HTTP-Mappings. Hinzu kommen Framework-Endpunkte fuer Health, Metrics,
OpenAPI/Swagger und Fehlerbehandlung. Prometheus ist konfiguriert, aber eine
Prometheus-Registry-Dependency fehlt; im Teststart wurden nur zwei Actuator-Endpunkte exponiert.

## 6. Datenbank und Flyway

24 fachliche Tabellen/Entities; `flyway_schema_history` entsteht zusaetzlich durch Flyway.
IDs sind im SQL `bigserial`, Basiseinheiten enthalten `created_at` und `updated_at`.
Geld und Lagerwerte nutzen `numeric` / Java `BigDecimal`. Reservierungs- und Bonzeiten
sind teilweise lokale `timestamp`/LocalDateTime, Audit-/Tokenzeiten `timestamptz`/OffsetDateTime.

| Tabelle | Entity | Erste Migration |
| --- | --- | --- |
| `app_users` | `AppUser` | V2 |
| `refresh_tokens` | `RefreshToken` | V2 |
| `revoked_access_tokens` | `RevokedAccessToken` | V2 |
| `table_orders` | `TableOrder` | V1 |
| `table_order_items` | `TableOrderItem` | V1 |
| `consumption_metadata` | `ConsumptionMetadata` | V4 |
| `drink_sales_daily` | `DrinkSalesDaily` | V4 |
| `drink_sales_weekly` | `DrinkSalesWeekly` | V4 |
| `inventory_business_settings` | `InventoryBusinessSettings` | V3 |
| `inventory_items` | `InventoryItem` | V1 |
| `inventory_movements` | `InventoryMovement` | V1 |
| `reorder_calculations` | `ReorderCalculation` | V4 |
| `reorder_orders` | `ReorderOrder` | V17 |
| `suppliers` | `Supplier` | V17 |
| `drinks` | `Drink` | V1 |
| `drink_categories` | `DrinkCategory` | V1 |
| `drink_variants` | `DrinkVariant` | V1 |
| `volume_prices` | `VolumePrice` | V3 |
| `booking_slot_config` | `BookingSlotConfig` | V1 |
| `opening_hours` | `OpeningHour` | V1 |
| `reservations` | `Reservation` | V1 |
| `shift_settlements` | `ShiftSettlement` | V3 |
| `shift_worker_entries` | `ShiftWorkerEntry` | V3 |
| `tables` | `TableEntity` | V1 |

Wichtige Relationen: Reservation -> Table; Order -> Table/Reservation;
OrderItem -> Order/DrinkVariant; DrinkVariant -> Drink -> Category;
InventoryItem -> Drink/Variant; Movement/ConsumptionMetadata/ReorderCalculation -> InventoryItem;
ReorderOrder -> InventoryItem/Supplier; RefreshToken -> AppUser;
ShiftWorkerEntry -> ShiftSettlement. Bonpositionen und Schichteintraege besitzen SQL-Cascade
beim Loeschen ihres Elternobjekts.

Unique-Constraints betreffen u.a. Benutzer-E-Mail, Token-IDs, QR-Token,
Kategorie-/Tischnamen, Volumengroesse, Abrechnungsdatum, Artikelmetadaten,
Lieferantennamen sowie Tages-/Wochenaggregate. Kein partieller Unique-Index fuer
einen offenen Bon je Tisch, kein Reservierungs-Overlap-Constraint und kein
Idempotenzschluessel fuer Bestellrequests gefunden. Keine `@Version`/`@Lock`-Absicherung
in den Java-Entities/Repositories gefunden. Ein Concurrency-Nachweis fehlt.

### Vollstaendige Migrationsfolge

| Datei | Inhalt |
| --- | --- |
| `V1__baseline_consolidated_schema.sql` | Kernschema: Reservierung, Tische, Menu, Lager und Bons |
| `V2__auth_and_tokens_consolidated.sql` | Booking-Defaults, Oeffnungszeiten, Benutzer und Tokens; aktive Seed-Benutzer |
| `V3__pricing_settings_shifts_consolidated.sql` | Volumenpreise, Schichtabrechnung und Business-Day-Einstellungen |
| `V4__sales_tracking_consolidated.sql` | Tages-/Wochenaggregate, Nachbestellberechnung und Verbrauchsmetadaten |
| `V5__volume_prices.sql` | Erneute idempotente Anlage/Initialisierung der Volumenpreise |
| `V6__drink_variant_custom_price_flag.sql` | Backfill Standardpreis-Flag |
| `V7__shift_settlements.sql` | Erneute idempotente Anlage der Schichttabellen |
| `V8__inventory_business_settings.sql` | Erneute Anlage/Initialisierung der Business-Einstellungen |
| `V9__table_order_paid_flag.sql` | Backfill paid-Flag |
| `V17__reorder_orders.sql` | Lieferanten und Nachbestellungen |
| `V18__fix_tables_name_bytea.sql` | Bedingte Legacy-Konvertierung tables.name bytea -> varchar |
| `V19__fix_archive_query_lower_cast.sql` | Bedingte Legacy-Konvertierungen tables.name/area bytea -> varchar |
| `V20__inventory_soft_delete_with_unlink.sql` | Nur Kommentar zur Soft-Delete-Umstellung; keine SQL-Aenderung |

V10-V16 fehlen im aktuellen Dateisatz. Versionsluecken alleine sind kein Fehler.
`MIGRATION_CONSOLIDATION.md` beschreibt umgeschriebene/zusammengefuehrte V1-V9;
Kompatibilitaet mit bereits migrierten Bestandsdatenbanken ist ohne deren Historie offen.
Keine vorhandene Migration wurde in Phase 1 veraendert. Migrationen auf leerem PostgreSQL
sind durch die vorhandenen H2-Tests nicht geprueft und bleiben Phase 3 vorbehalten.

## 7. Security und Berechtigungen

`SecurityConfig` definiert stateless Bearer-Authentifizierung, deaktiviert CSRF im
Backend und verwendet BCrypt. `JwtTokenService` signiert HMAC-JWTs, prueft Signatur,
Issuer und Ablauf; Token-Typ und `jti` werden getrennt behandelt. Der Filter prueft
Access-Token-Revocation in der DB, uebernimmt Rollen aber aus dem JWT. Eine aktuelle
Benutzeraktivitaet/-rolle wird dort nicht nachgeladen. `JwtProperties` ist nicht validiert;
Default-Secret und Schluessellaenge werden nicht beim Konfigurationsbinding abgelehnt.

| Zugriff | Endpunkte / Aktionen |
| --- | --- |
| anonym | Login, Refresh, Logout; POST Reservierung; GET Menu-Kategorien/Getraenke/Varianten; GET Health und OpenAPI/Swagger |
| ADMIN oder STAFF | Sessions/logout-all, Reservierungsverwaltung/Scan/QR, Tische, Bons, einfache Lagerlese-Endpunkte, Umsatzuebersicht, Schichtabrechnung |
| ADMIN | Menu-Schreiboperationen, Volumenpreise, Lageranlage/-korrektur/-update/-delete, sonstige Reports/PDF, Metrics/Prometheus |
| nur authenticated (Fallback) | Reorder-Lieferanten/Bestellungen inkl. Schreiben; neuere Inventory-Leseendpunkte; POST calculate-reorder und manual-day-close |

Es gibt keine zusaetzliche Method-Security, die diese Fallbacks einschraenkt.
`@SecurityRequirement` dokumentiert OpenAPI und erzwingt keine Rolle.

Refresh-Tokens sind JWTs; serverseitig werden ID, Benutzer, Ablauf, Widerruf und
Sessionmetadaten gespeichert. Rotation, Replay-Erkennung, Session-Revocation,
Logout und Cleanup existieren. Beim Replay erfolgt Widerruf in derselben Transaktion,
aus der anschliessend eine RuntimeException geworfen wird: Rollback-Risiko.
Der vorhandene Auth-Test erwartet sogar, dass das bereits rotierte Token nach Replay
weiter genutzt werden kann. Rotation ist nicht gegen parallele Refresh-Requests gesperrt.
Logout-all widerruft Refresh-Sessions und nur das mitgegebene Access-Token.

Next.js setzt HttpOnly-Cookies, SameSite=Lax und Secure im Production-Modus;
Backend-Requests erhalten Bearer-Header. Automatischer Refresh reagiert auf 401,
waehrend Backend-Tests fuer ungueltige Access-Tokens 403 erwarten. Keine dedizierte
Origin-/CSRF-Pruefung in den Cookie-basierten Next-Adaptern gefunden. Kein explizites
CORS-Setup oder Rate Limiter im Backend gefunden. Spring-Security-Standardheader sind
nicht abgeschaltet, wurden aber nicht separat per HTTP vermessen.

`backend/.env` und `.env.example` sind versioniert. Werte werden hier nicht wiederholt;
ihre reale Verwendung/Produktionsgueltigkeit wurde nicht verifiziert. Konfiguration,
Compose und Maven enthalten nutzbare Entwicklungsdefaults. V2 legt aktive ADMIN/STAFF-
Benutzer mit festen Passwort-Hashes an, `backend/README.md` dokumentiert Seed-Zugaenge.
Ein vollstaendiger Secret-Scan der Git-Historie ist in Phase 1 nicht erfolgt.

## 8. Fachablaeufe und Integrationen

### Reservierung

POST -> Bean Validation -> aktive BookingSlotConfig -> Oeffnungszeit/Slot-Raster ->
Kapazitaetszaehlung -> PENDING und zufaelliger QR-Token. Kapazitaet zaehlt Anfragen nur
fuer genau denselben Tag/Zeitpunkt gegen aktive Tische; guestCount wird im Kapazitaets-
Check nicht verwendet. Kein Beleg fuer Intervallueberlappungspruefung oder eine
atomare Kapazitaetsreservierung. Erstellung weist keinen Tisch zu.

PENDING -> CONFIRMED versendet optional Mail; Ablehnung setzt REJECTED und sendet
optional Mail. Check-in setzt CHECKED_IN und Zeit, wenn bestaetigt und am heutigen Tag.
Minuetlicher Job setzt nicht eingecheckte aktive Reservierungen des heutigen Tages auf
NO_SHOW nach Ablauf der Kulanz. Vorherige Tage werden dadurch nicht nachbearbeitet.
LocalDate/LocalDateTime.now nutzen die Serverzeitzone; Business-Date-Konfiguration wird
hier nicht verwendet. Kein Aenderungs-Endpunkt fuer Datum/Zeit und kein kompletter
COMPLETED-Lifecycle im Service gefunden.

### Verkauf, Abrechnung und Schicht

Tischbon oeffnen -> vorhandenen offenen Bon pruefen -> Tisch OCCUPIED.
Position hinzufuegen -> aktive Variante, Preis und Bestand bestimmen -> Position anlegen
oder Menge zusammenfassen -> Lager sofort abbuchen -> Bewegung und Verkaufsstatistik.
Entfernen reduziert eine Einheit und bucht Lager zurueck; Verkaufsaggregate werden dabei
nicht korrigiert. `close` setzt CLOSED/paid=true; `markUnpaid` CLOSED/paid=false;
beide geben den Tisch frei. Unbezahlte Bons koennen wiedereroeffnet werden.
Split Payment erzeugt einen bezahlten Teilbon, verschiebt Mengen/Volumen und schliesst
den Ausgangsbon, wenn keine Positionen verbleiben. Dies ist interne Bonabrechnung;
keine externe Zahlungsanbieter-Anbindung gefunden.

Serviceoperationen sind transaktional; Geldberechnung nutzt BigDecimal.
Fehlende Sperren/Idempotenz sind durch die Tests nicht abgesichert. MenuService kann
Bonpositionen beim Getraenke-/Variantenloeschen unabhaengig vom Bonstatus entfernen;
bei vorhandenen weiteren Fremdschluesseln kann stattdessen die Transaktion fehlschlagen.
Bei erfolgreichem Loeschen koennen historische Summen verloren gehen.
Standardpreis-/Variantenupdates berechnen bereits offene Bonpositionen neu.

Archiv und Umsatzreports verwenden Kalendertage; unbezahltes Archiv ignoriert bewusst
den Datumsfilter. Reports beruecksichtigen nur bezahlte geschlossene Bons.
Schichtabrechnung berechnet Loehne, Mitternachtsuebergaenge und erwarteten Kassenbestand
mit BigDecimal aus Schichtzeiten, Umsatz und Ausgaben.

### Lager und Nachbestellung

Lageranlage/Update berechnet Gesamtmenge aus Gebindezahl mal Gebindegroesse.
Manuelle Anpassung und Bonabbuchung erzeugen Bewegungen; Liter/Milliliter werden
umgerechnet, Stuecklager wird fuer Getraenkevolumen abgelehnt. Soft Delete deaktiviert
den Artikel und entfernt beide Menu-Verknuepfungen; Bewegungen und Bestand bleiben.
Gebindezahl und Restbestand werden nicht bei jeder Abbuchung gemeinsam synchronisiert.

Verkaeufe aggregieren pro Business Date. Ein Bestellaufruf mit mehreren Einheiten
uebergibt derzeit immer quantity=ONE an das Tracking. Tageswerte werden additiv in Wochen
uebertragen (05:15 Europe/Berlin); Wiederholung ist nicht idempotent. Nachbestellungen
werden um 05:30 Europe/Berlin und bei Verkaeufen berechnet. Tagesdurchschnitt in ml wird
im Berechnungsservice als Wochenmenge verarbeitet und mit Artikelbestand verglichen,
ohne dessen Liter-Einheit zu konvertieren. Nachbestellmengen sind daher fachlich zu pruefen.

Globale Business-Zeitzone, Tagesgrenze und manuelles Datum werden gespeichert.
Aenderungen an globalem Lookback/Sicherheitsfaktor/Lieferzeit werden vom Update nicht
persistiert; gelesen werden weiter die Konfigurationsdefaults. Artikelmetadaten sind
speicherbar, aber ihr GET-Controller liefert nur null (trotz dokumentiertem 501).
Lieferbestellungen besitzen Status, aber RECEIVED setzt allein den Status und bucht
keinen Wareneingang. Die Lesemethoden mappen LAZY-Beziehungen teilweise ohne Transaktion.

### Externe Systeme / Dateien

| Integration | Implementierung / Grenze |
| --- | --- |
| PostgreSQL | produktive Persistenz; lokale H2-Tests kein SQL-/Locking-Nachweis |
| SMTP | JavaMailSender; optional, default aus; Bestaetigung/Ablehnung synchron in Service-Transaktion; Fehler geloggt und verschluckt |
| QR | ZXing, 320x320 PNG; URL `/api/reservations/scan/{token}`; Backend bietet POST, normaler QR-Browseraufruf waere GET; Compose zeigt auf Frontend ohne Scan-Adapter |
| PDF | PDFBox, Nachbestellliste mit optionalem Lieferantenfilter; genau eine Seite, weitere Zeilen werden abgeschnitten |
| Fonts | `next/font/google` laedt Geist/Geist Mono beim Build; Netzwerk-/Cache-Abhaengigkeit |
| Browserkalender | FullCalendar; keine externe Kalender-Synchronisation gefunden |
| Monitoring | Actuator, Micrometer-Auth-Counter, Request-ID-Filter mit MDC; Prometheus-Registry fehlt; Ops-UI zeigt Umsatzdaten |
| CI/Registry | GitHub Actions, npm/Maven Downloads; keine automatisierte Image-Publishing-/Scan-Pipeline gefunden |

## 9. Tests, Suchbefunde und Testluecken

| Backend-Testklasse | Tests | Art / gepruefter Bereich |
| --- | --- | --- |
| BackendApplicationTests | 1 | Spring/H2-Kontextstart |
| AuthFlowIntegrationTest | 3 | HTTP/H2: Logout, Rotation/Replay-Antwort, einzelne Session |
| SecurityRoleMatrixIntegrationTest | 1 | HTTP/H2: ADMIN/STAFF auf ausgewahlten Lager-/Reportpfaden |
| TableOrderArchiveApiIntegrationTest | 1 | HTTP/H2 mit Testkonfiguration fuer korrupte Beziehungen |
| TableOrderServiceArchiveFilterTest | 2 | Mockito: unbezahltes Archiv und Fallbacks |
| InventoryServiceUnitConversionTest | 7 | Mockito: Einheiten, Verknuepfung, Gebindeschwellen und Soft Delete |
| RevenueReportServicePaidOnlyTest | 1 | Mockito: bezahlte Bons im Report |

Keine PostgreSQL-/Testcontainers-Tests, kein Migrationsgate, kein Coverage-Mindestwert,
keine Backend-Lifecycle-Tests fuer Reservierungen, keine parallelen Zahlungs-/Bestands-
oder Refresh-Tests und keine serverseitigen Split-/Rollback-Tests gefunden.
Keine isolierten Frontend-Unit-/Component-Tests oder entsprechender Runner eingerichtet.

Browser-Specs und Testzahl:

| Spec | Tests |
| --- | --- |
| `auth-sessions.spec.ts` | 1 |
| `bar-admin-categories.spec.ts` | 4 |
| `bar-admin-drink-tabs.spec.ts` | 1 |
| `bar-admin-inventory-unlink.spec.ts` | 1 |
| `inventory-deduction-order-events.spec.ts` | 1 |
| `reservation-lifecycle.spec.ts` | 1 |
| `table-billing-archive-regression.spec.ts` | 2 |
| `table-billing-prerequisite-flow.spec.ts` | 1 |
| `table-billing-remove-single-item.spec.ts` | 1 |
| `table-billing-split-payment.spec.ts` | 1 |
| `table-billing-unpaid-archive.spec.ts` | 1 |
| `table-billing-unpaid-persistent.spec.ts` | 1 |
| `table-billing-unpaid-reopen-pay.spec.ts` | 1 |

`test:e2e:critical` waehlt sechs Specs mit neun Tests aus. Die kritischen Tests
verwenden Browser-API-Mocks und synthetische Auth-Cookies; kein Spring/PostgreSQL laeuft
in diesem Gate. Die uebrigen Specs verwenden ebenfalls Mocks (Auth-Spec eigene Handler).
Playwright startet Next Dev, verwendet Chromium und `reuseExistingServer: true`.
CI setzt feste Testdaten, friert damit aber nicht automatisch die Browseruhr ein.

### Verlangte Quellcodesuchen

Gescannter Bereich: Backend main/test/resources, Frontend app/components/lib/types/e2e,
Build-/CI-Konfiguration; generierte Dateien und node_modules ausgenommen.

| Muster | Ergebnis |
| --- | --- |
| TODO / FIXME / HACK | keine Treffer im untersuchten Anwendungs-/Testquellcode |
| @Disabled / @Ignore / test.skip / test.fixme / describe.skip / it.skip | keine Treffer |
| Build-Test-Skips | Backend-Dockerfile, CI-Backendjob, export-openapi.sh verwenden skipTests |
| leere Catch-Bloecke | keine syntaktisch leeren Bloecke gefunden; AuthService verschluckt Fehler mit Kommentar |
| catch(Exception ...) | 7 Stellen: JwtAuthenticationFilter, AuthService (2), InventoryService, ReorderCalculationService, ReservationMailService, QrCodeService |
| catch(RuntimeException ...) | 4 Archiv-Fallbacks im TableOrderService; koennen mehr als fehlende Beziehungen verbergen |
| TypeScript any / @ts-ignore / ESLint-disable | keine Treffer in app/components/lib/types/e2e |
| skipLibCheck | bereits true in tsconfig.json; unveraendert, keine neue Umgehung eingefuehrt |
| Controller-Repositoryzugriffe | keine direkten Imports; InventoryController orchestriert und mappt LAZY-Entities |
| Unsichere Typannahmen | response.json as DTO in server-auth.ts und Clientseiten, kein Laufzeitdecoder/Contract-Gate |
| Fehler-Fallbacks | viele `.json().catch(() => ({}))` bzw. leere Listen in Next-Adaptern; Netzfehler oft ohne differenzierte Abbildung |

Duplizierte Regeln: MenuService und TableOrderService loesen Standardpreise auf;
InventoryService, UI und E2E-Mocks enthalten Mengen-/Gebinde-/Verfuegbarkeitsregeln;
QR-URL-Bildung in ReservationService und ReservationMapper; Formatierung und Fetch-
Fehlerpfade in vielen Clients. Dies sind Refactoring-Kandidaten, keine pauschal
bewiesenen fachlichen Fehler.

Groesste Verantwortungsbuendel: TableOrderService 450, InventoryService 375,
MenuService 358, InventoryController 335 Zeilen. Frontend: BarAdminClient 1338,
InventoryClient 1060, TableBillingClient 674, SalesConfigurationDashboard 500 und
ShiftSettlementClient 494 Zeilen. Zeilenzahl ist ein Analyseindikator, kein Qualitaetsgate.

## 10. Build-, Test- und Reproduktionsprotokoll

| Pruefung | Erstlauf | Nach kleiner Testkorrektur |
| --- | --- | --- |
| `./mvnw test` | FAIL: 16 Tests, 1 Failure (veraltete Hard-Delete-Erwartung) | PASS: 16 Tests, 0 Failures/Errors/Skips |
| `./mvnw verify` | nach reproduziertem Erstfehler und Korrektur ausgefuehrt | PASS: 16 Tests, JAR und JaCoCo-Report erzeugt |
| `./mvnw clean verify` | zusaetzlicher Nachweis ohne alte Build-/Coverage-Daten | PASS: 16 Tests, 0 Failures/Errors/Skips, JAR und frischer JaCoCo-Report |
| `npm ci` | PASS: 715 Pakete installiert; Lockfile unveraendert | kein zweiter Installationslauf erforderlich |
| `npm run lint` | PASS: 0 Fehler, 5 Warnungen | PASS mit denselben Warnungen |
| `npm run build` | PASS: Next-Production-Build und TypeScript | PASS: Production-Build und TypeScript erneut erfolgreich |
| `npm run test:e2e:critical` | FAIL: 2 passed, 7 failed | PASS: 9 passed, 4 Worker, 43.0 s |
| kritische E2E mit `--workers=1` | Kontrolllauf nach Selektorkorrektur | PASS: 9 passed, 1.7 min |
| `npm audit --json` (zusaetzlich) | Exit 1: 20 betroffene Pakete | unveraendert als offener Security-Befund dokumentiert |

Die Erstlauf-E2E-Fehler waren vier Strict-Mode-Verletzungen durch mehrfach dargestellte
Status-/Fehlermeldungen und drei Timeouts waehrend paralleler Backend-/Browserlast.
Die vier Selektoren wurden auf main bzw. Edit-Modal eingeschraenkt. Die drei Timeout-
Faelle bestanden sowohl im seriellen Kontrolllauf als auch im normalen Wiederholungslauf.
Timeouts/Assertions/Worker-Vorgaben wurden nicht gelockert. Der laengste Standardtest
brauchte 29.4 s bei 30 s Timeout: Lastempfindlichkeit bleibt TD-035.

Geaendert wurden ausschliesslich diese zwei bestehenden Tests und die zwei Dokumente:

- `backend/src/test/java/org/thomcgn/backend/inventory/service/InventoryServiceUnitConversionTest.java`: Soft Delete samt Entknuepfung, unveraendertem Restbestand und unberuehrten Bewegungen statt physischem Loeschen pruefen.
- `frontend/e2e/bar-admin-categories.spec.ts`: vier Meldungsselektoren eindeutig eingrenzen.
- `docs/architecture/BASELINE.md`: dieses Inventar mit Reproduktionsprotokoll.
- `docs/architecture/TECH_DEBT.md`: 38 priorisierte Eintraege, davon zwei Testprobleme hier korrigiert.

Kein Test deaktiviert, keine Linter-Regel abgeschwaecht, keine produktive Fachlogik,
Dependency oder Migration geaendert. Kein Commit erstellt.

Reproduktion ab Repository-Root:

```bash
cd backend
./mvnw test
./mvnw verify
cd ../frontend
npm ci
npm run lint
npm run build
npm run test:e2e:critical
```

Chromium war in der lokalen Umgebung bereits verfuegbar. Auf frischen CI-Maschinen
installiert die bestehende Pipeline ihn mit `npx playwright install --with-deps chromium`.
Node 22 und Java 21 entsprechen der CI; lokale Toolversionen siehe Abschnitt 2.
Backend-Berichte: `backend/target/surefire-reports` und nach erfolgreichem verify
`backend/target/site/jacoco`. Frontend-Bericht: `frontend/test-results/playwright/results.xml`.
Die vom letzten Standardlauf erzeugten Playwright-Berichte liegen fuer diese Sitzung
unter `/tmp/fasswerk-phase1-playwright-final/` (JUnit: `playwright/results.xml`).
Der serielle Lauf nutzte `/tmp/fasswerk-phase1-playwright-serial/` als Output-Verzeichnis.
Die beiden bereits versionierten Dateien in `frontend/test-results` wurden nach dem
Sichern der neuen Ergebnisse auf ihren unveraenderten Ausgangsinhalt zurueckgesetzt;
sie sind daher nicht der Ergebnisnachweis dieser Sitzung.
Maven-/Frontend-Logs liegen unter `/tmp/fasswerk-phase1-maven-*.log` und
`/tmp/fasswerk-phase1-frontend-*.log`; npm-Audit unter `/tmp/fasswerk-phase1-npm-audit.json`.
Logs enthalten ggf. synthetische Auth-Testdaten und werden nicht eingecheckt.
Diese temporaeren Artefakte sind nicht dauerhaft verfuegbar; die Zusammenfassung hier
und die Reproduktionskommandos bilden den versionierbaren Nachweis.

### Abdeckung nach Clean Verify

Frisch aus `./mvnw clean verify`; keine alten JaCoCo-Ausfuehrungsdaten.
Abdeckung misst ausgefuehrten Code, nicht fachliche Vollstaendigkeit.

| Metrik | Abgedeckt / Gesamt | Anteil |
| --- | --- | --- |
| INSTRUCTION | 3324 / 9482 | 35.06% |
| BRANCH | 108 / 435 | 24.83% |
| LINE | 641 / 1887 | 33.97% |
| COMPLEXITY | 175 / 660 | 26.52% |
| METHOD | 151 / 437 | 34.55% |
| CLASS | 80 / 133 | 60.15% |

| Service | Zeilen | Branches |
| --- | --- | --- |
| `ReservationService` | 1/98 (1.02%) | 0/38 (0.00%) |
| `AuthService` | 112/144 (77.78%) | 16/30 (53.33%) |
| `MenuService` | 0/189 (0.00%) | 0/36 (0.00%) |
| `TableOrderService` | 42/247 (17.00%) | 12/68 (17.65%) |
| `InventoryService` | 105/184 (57.07%) | 24/45 (53.33%) |

Reservation und Menu sind praktisch nicht fachlich getestet; Billing deckt vorwiegend
Archivleseverhalten ab. Diese Zahlen rechtfertigen gezielte Domain-Tests vor
Strukturumbauten, keinen pauschalen Prozentwert als Ersatz fuer Invarianten.

## 11. Abgrenzung und Gate

Phase 1 dokumentiert den Ausgangsstand und stellt das bestehende Pruefverfahren her.
Die offenen P0/P1-Befunde sind keine Produktionsfreigabe. Phase 2 wird erst nach
abgeschlossenem Baseline-Gate und ausdruecklicher Freigabe begonnen.

### PHASE 1 REPORT

Geaenderte Dateien:

- `docs/architecture/BASELINE.md`
- `docs/architecture/TECH_DEBT.md`
- `backend/src/test/java/org/thomcgn/backend/inventory/service/InventoryServiceUnitConversionTest.java`
- `frontend/e2e/bar-admin-categories.spec.ts`

Behobene Probleme:

- Veralteter Hard-Delete-Test an den bestehenden Soft-Delete-Vertrag angepasst.
- Vier mehrdeutige Playwright-Meldungsselektoren auf ihren fachlichen Bereich begrenzt.
- Fehlende Architektur-Baseline und priorisiertes Debt-Inventar erstellt.

Neue/geaenderte Tests:

- Ein bestehender Backendtest fuer Soft Delete/Entknuepfung/Bestandserhalt aktualisiert.
- Vier bestehende E2E-Assertions praezisiert, keine Faelle entfernt oder deaktiviert.

Ausgefuehrte Pruefungen:

- Backend test, verify und abschliessend clean verify: jeweils 16 Tests gruen.
- Frontend npm ci, lint und Production Build erfolgreich; fuenf Lintwarnungen unveraendert.
- Kritische E2E nach Korrektur seriell und im Standardaufruf erfolgreich: je neun Tests.
- Zusaetzlicher npm-Audit mit offenen Befunden; git diff --check ohne Fehler.

Ergebnis: **PASS fuer das Phase-1-Baseline-Gate**, keine Produktionsfreigabe.

Offene Risiken:

- Bekannte Secret-Defaults, aktive Seed-Konten, versionierte .env und weitere P0/P1-Befunde.
- Npm-Audit meldet 20 betroffene Pakete einschliesslich eines critical-Eintrags.
- Kein Nachweis fuer PostgreSQL-Migrationen oder echte Backend-/DB-E2E-Integration.
- Hohe Lastempfindlichkeit einzelner Browserflows und geringe kritische Domainabdeckung.

Empfohlener naechster Schritt: **PHASE 2**, ausschliesslich nach ausdruecklicher Freigabe.
Phase 2 wurde nicht begonnen.
