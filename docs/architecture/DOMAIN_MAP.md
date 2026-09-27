# Domain Map und Modulgrenzen

Stand: Phase 4, 2026-09-27. Grundlage sind die Java-Pakete, Imports,
JPA-Beziehungen, Repository-Abfragen, REST-Adapter und Service-Transaktionen im
Repository, nicht allein die Kontextvorschlaege des Refactoring-Plans.

## Einordnung

FassWerk ist ein modular zu strukturierender Monolith: ein Spring-Boot-Prozess,
ein Maven-Modul und ein gemeinsames PostgreSQL-Schema. Paketgrenzen sind derzeit
keine vollstaendig durchgesetzten Modulgrenzen. JPA-Entities sind weitgehend
anemisch; fachliche Regeln liegen vor allem in Services, teilweise in Adaptern.

Die nachfolgenden Aggregate sind Verantwortungs-/Lebenszykluszuordnungen, keine
Behauptung bereits geschuetzter DDD-Aggregate. Fremde JPA-Referenzen und mehrere
Repository-Zugriffe innerhalb einer Transaktion bleiben dokumentierter Ist-Stand.

| Fachlicher Kontext | Paket | Entscheidung |
| --- | --- | --- |
| Identity & Access | auth | Benutzer, Tokens, Sessions, Authentifizierung |
| Reservation | reservation | Reservierung, Zeitfenster, Kapazitaet, Check-in |
| Table / Service | table | Tischstammdaten und Tischstatus |
| Catalog / Pricing | menu | Eigener Kontext, nicht pauschal Administration |
| Ordering & Billing | billing | Vorlaeufig gemeinsam: derselbe Bon samt Positionen traegt Bestellung und Zahlung |
| Inventory / Procurement | inventory | Bestand, Bewegungen, Beschaffung und Verbrauchsprojektionen |
| Shift Settlement | shift | Eigener Kontext fuer Schicht-/Lohn-/Kassenabrechnung, nicht Billing oder Reporting |
| Reporting | report | Lesende Auswertung und PDF-Adapter, keine Stammdatenhoheit |
| Administration | kein eigenes Paket | Rollen-/UI-Sicht auf Use Cases der jeweiligen Kontexte, kein Sammelkontext |

`common`, `config` und `qr` sind technische Unterstuetzung, keine zusaetzlichen
fachlichen Kontexte. Kein neuer globaler administration- oder shared-business-Ordner.

## 1. Identity & Access

- Aggregate/Entities: AppUser als Benutzerwurzel; RefreshToken als persistierte
  Session mit Benutzerreferenz; RevokedAccessToken als eigenstaendiger Sperreintrag.
  Tokenfamilien sind noch kein explizites Aggregat.
- Value Objects: UserRole ist ein Enum; Token-IDs/E-Mail bleiben Strings,
  Lebensdauern und Zeitpunkte primitive/JDK-Werte. ClientMetadata ist ein
  unveraenderlicher Eingaberecord, noch kein validiertes Domain-Value-Object.
- Application Services/Use Cases: AuthService fuer Login, Rotation, Logout,
  Sessionliste und Widerruf; AdminBootstrap fuer opt-in Erstprovisionierung;
  TokenCleanupJob fuer Bereinigung; DeviceFingerprintService fuer Geraetebezeichnung.
  AppUserDetailsService, JwtTokenService und JwtAuthenticationFilter integrieren Security.
- Repositories: AppUserRepository, RefreshTokenRepository, RevokedAccessTokenRepository.
- REST: AuthController, `/api/auth` (Login/Refresh/Logout/Sessions/Logout-all).
- Events: keine publizierten Domain Events. Fachliche Kandidaten waeren
  SessionIssued, RefreshTokenRotated und SessionRevoked; heute synchrone DB-Schreibzugriffe,
  Metriken und Logs. TokenCleanupJob ist ein Scheduler, kein Event Bus.
- Erlaubte Abhaengigkeiten: eigene Domain/Repositories, common und technische
  Security-/JWT-/Metrics-Bibliotheken. Keine Abhaengigkeit von operativen Fachkontexten.
- Offene Grenzen: Replay-Widerruf wird bei Exception zurueckgerollt, parallele
  Rotation erzeugt zwei Nachfolger, Access-Tokens lesen Kontosperren nicht erneut.
  Behebung Phase 6; Phase 3 liefert Charakterisierungstests.

## 2. Reservation

- Aggregate/Entities: Reservation als Reservierungswurzel mit optionaler
  TableEntity-Referenz; OpeningHour und BookingSlotConfig als getrennte Konfiguration.
- Value Objects: ReservationStatus und BookingIntervalMode sind Enums. Slot,
  Gastanzahl, Kontakt und QR-Token sind noch keine validierten Value Objects;
  LocalDate/LocalTime/LocalDateTime tragen die aktuelle Zeitdarstellung.
- Application Services/Use Cases: ReservationService fuer Erstellen, Tagesliste,
  Lesen, Bestaetigen, Ablehnen, Check-in, QR/Scan und No-show-Markierung.
  ReservationMapper bildet REST-DTOs; ReservationMailService ist der SMTP-Adapter.
- Repositories: ReservationRepository, OpeningHourRepository, BookingSlotConfigRepository.
- REST: ReservationController, `/api/reservations`, einschliesslich Entscheidungen,
  `/scan/{token}` und `/{id}/qr-code`.
- Events: ReservationCreated/Confirmed/Rejected/CheckedIn/NoShow waeren fachliche
  Kandidaten. Heute keine Eventklassen/Publikation; Bestaetigung/Ablehnung rufen Mail
  synchron, No-show wird minuetlich geplant.
- Erlaubte Abhaengigkeiten: eigene Persistenz; lesender Tischkapazitaetsvertrag
  aus Table / Service; QR-/Mail-Infrastruktur. Ist-Ausnahme: direkter TableRepository-
  Zugriff und TableEntity-Beziehung, spaeter durch passende Grenze ersetzen.
- Offene Grenzen: Kapazitaetsmodell, Ueberlappungen, Zeitzonen und Parallelitaet
  sind Phase 7; Gastanzahl wird im Kapazitaetszaehler nicht beruecksichtigt.

## 3. Table / Service

- Aggregate/Entities: TableEntity als Tischwurzel (Name, Bereich, aktiv, Status).
- Value Objects: TableStatus-Enum; Tisch-ID/Name/Bereich bleiben skalare Werte.
- Application Services/Use Cases: TableService fuer Liste, Anlage und Aenderung.
- Repository: TableRepository, einschliesslich aktiver Tischanzahl.
- REST: TableController, `/api/tables`.
- Events: TableCreated/Changed und TableOccupancyChanged waeren Kandidaten;
  heute keine Publikation, Belegung wird aus TableOrderService direkt geschrieben.
- Erlaubte Abhaengigkeiten: eigene Persistenz und common. Tischstatuswechsel
  gehoeren diesem Kontext; Billing darf sie kuenftig ueber einen expliziten
  Application-Vertrag anfordern, nicht selbst fremde Entities speichern.
- Offene Grenze: TableOrderService nutzt TableRepository und aendert OCCUPIED/FREE
  innerhalb der Bontransaktion. Nicht in Phase 4 beilaufig auf asynchrone Events umstellen.

## 4. Catalog / Pricing

- Aggregate/Entities: Drink mit DrinkVariant als fachlicher Variantenstruktur;
  DrinkCategory und VolumePrice als eigenstaendige Stammdaten. Die technische
  Persistenz nutzt vier Repositories, also noch keine gekapselte Drink-Aggregatwurzel.
- Value Objects: Preise als BigDecimal, Menge als Integer volumeMl; kein Money-
  oder Volume-Value-Object und keine explizite Waehrung. Use-standard-price ist ein Flag.
- Application Services/Use Cases: MenuService verwaltet Kategorien, Getraenke,
  Varianten, Standardpreise und deren Synchronisierung.
- Repositories: DrinkCategoryRepository, DrinkRepository, DrinkVariantRepository,
  VolumePriceRepository.
- REST: MenuController unter `/api`, mit `/drink-categories`, `/drinks`,
  `/drink-variants` und `/volume-prices`.
- Events: VariantPriceChanged/StandardPriceChanged und CatalogItemRetired sind
  moegliche kuenftige Vertraege. Heute erfolgen Nachpreisung und Entknuepfung direkt
  innerhalb von MenuService, ohne Eventmechanismus.
- Erlaubte Abhaengigkeiten: eigene Persistenz/common. Kuenftige Auswirkungen auf
  offene Bons bzw. Inventar nur ueber deren fachliche Schnittstellen; die Entscheidung
  ueber bestehende Bonpreise gehoert Billing, nicht Catalog.
- Ist-Verletzungen: direkte Billing- und Inventory-Repositories/Entities;
  Loeschen einer Variante kann bezahlte Bonpositionen loeschen (TD-004).
  Das ist keine erlaubte Zielabhaengigkeit. Nicht durch eine blosse Wrapper-Klasse
  kaschieren; Phase 8 muss Historie/Retirement und Preissemantik gemeinsam absichern.

## 5. Ordering & Billing

- Aggregate/Entities: TableOrder mit TableOrderItem als Bon-/Positionsaggregat.
  Die Entitaeten werden getrennt gespeichert; TableOrder referenziert TableEntity
  und Reservation, Positionen referenzieren die aktuelle DrinkVariant.
  Ein eigenes Invoice-, Payment- oder PaymentAttempt-Aggregat existiert nicht.
- Value Objects: TableOrderStatus-Enum, BigDecimal-Preise und -Volumina,
  Integer-Mengen; kein Money-/PaymentId-/IdempotencyKey-Value-Object.
  PaidOrderRevenue ist ab Phase 4 ein unveraenderliches **Lesemodell**, keine Entity
  und kein neues Domain-Value-Object.
- Application Services/Use Cases: TableOrderService fuer Oeffnen, Positionen buchen/
  entfernen, Schliessen, Unbezahlt-Markierung, Wiedereroeffnung, Teilzahlung und Archiv.
  BillingRevenueQueryService implementiert den lesenden BillingRevenueQueries-Vertrag.
- Repositories: TableOrderRepository und TableOrderItemRepository; ausschliesslich
  Billing entscheidet im neuen Umsatzvertrag ueber CLOSED/paid und persistente Auswahl.
- REST: TableOrderController, `/api/table-orders`.
- Events: OrderOpened, ItemAdded/Removed, OrderClosed, PaymentSplit und
  OrderReopened sind Kandidaten, derzeit nicht implementiert. Bestand wird synchron
  ueber InventoryService geaendert, nicht durch asynchrone Eventverarbeitung.
- Erlaubte Abhaengigkeiten: eigene Persistenz/common; Katalogauskunft, Reservierungs-
  referenz, Tischstatusvertrag und Bestandsbefehle als Application-Grenzen.
  Ist-Ausnahmen: direkte Menu-/Reservation-/Table-Repositories und fremde JPA-Entities;
  InventoryService erhaelt ebenfalls eine DrinkVariant statt eines unabhaengigen Befehls.
- Bewusste Entscheidung: Ordering und Billing jetzt nicht kuenstlich trennen.
  Ein Bonstatuswechsel ist noch eng mit Positionen, Bestand und Tischstatus verbunden.
  Erst Invarianten und Transaktionen in Phase 8 klaeren, dann gegebenenfalls teilen.

## 6. Inventory / Procurement

- Aggregate/Entities: InventoryItem als Bestandswurzel; InventoryMovement als
  Bewegungsjournal; ConsumptionMetadata als 1:1-Planungsdaten. Supplier und ReorderOrder
  sind Beschaffungswurzeln mit Inventar-/Lieferantenreferenzen. InventoryBusinessSettings
  ist Konfiguration; DrinkSalesDaily/Weekly und ReorderCalculation sind persistierte
  Verbrauchs-/Planungsprojektionen, keine unabhaengigen Verkaufswahrheiten.
- Value Objects: ContentUnit, PackageType, InventoryMovementType,
  InventoryReferenceType und ReorderStatus sind Enums. Mengen, Verpackungsgroessen,
  Sicherheitsfaktoren und Einheiten sind noch keine zusammengesetzten Value Objects.
- Application Services/Use Cases: InventoryService fuer Pflege, Soft Delete,
  Korrektur, Bestandspruefung, Abbuchung/Rueckbuchung und Vorschlaege;
  ReorderOrderService fuer Lieferanten und Lieferbestellungen;
  SalesConfigurationService fuer Betriebseinstellungen;
  DrinkSalesTrackingService fuer Verbrauch und Wochenaggregation;
  ReorderCalculationService fuer Planungsdaten und Neuberechnung.
- Repositories: InventoryItemRepository, InventoryMovementRepository,
  ConsumptionMetadataRepository, SupplierRepository, ReorderOrderRepository,
  InventoryBusinessSettingsRepository, DrinkSalesDailyRepository,
  DrinkSalesWeeklyRepository, ReorderCalculationRepository.
- REST: InventoryController (`/api/inventory`) und ReorderOrderController (`/api/reorder`).
- Events: StockAdjusted, StockDeducted, StockRestocked und DeliveryReceived waeren
  fachliche Kandidaten; heute direkte Methoden/Transaktionen. Verbrauchsaggregation
  um 05:15 und Nachbestellberechnung um 05:30 Europe/Berlin sind Scheduler-Aufrufe.
- Erlaubte Abhaengigkeiten: eigene Persistenz/common sowie lesende Catalog-Vertraege.
  Ist-Ausnahmen: Menu-Repositories und JPA-Referenzen auf Drink/DrinkVariant.
  Inventory darf keine Billing-Positionen selbst veraendern.
- Offene Grenzen: verlorene Updates, Einheiten, Idempotenz und Aggregationsschluessel
  (NULL-Varianten) bleiben Phase 8. Beschaffung bleibt vorerst internes Teilgebiet,
  kein neuer Maven-/Microservice-Schnitt.

## 7. Shift Settlement

- Aggregate/Entities: ShiftSettlement mit ShiftWorkerEntry; Cascade ALL und
  orphanRemoval bilden hier bereits eine konkrete Lebenszyklusgrenze.
- Value Objects: Arbeitszeit via LocalTime, Lohn-/Kassenwerte via BigDecimal;
  noch keine expliziten ShiftInterval-/HourlyRate-Value-Objects.
- Application Services/Use Cases: ShiftSettlementService fuer Lesen je Tag,
  Zeitraumsliste, Speichern der Mitarbeiterzeiten und Berechnung von Stunden,
  Lohnkosten und erwartetem Kassenbestand.
- Repositories: ShiftSettlementRepository, ShiftWorkerEntryRepository.
- REST: ShiftSettlementController, `/api/shift-settlements`.
- Events: ShiftSettlementSaved waere ein Kandidat; derzeit keine Events und
  kein eingefrorener Abschluss/Umsatzsnapshot.
- Erlaubte Abhaengigkeiten: eigene Persistenz/common und **BillingRevenueQueries**.
  Ab Phase 4 keine direkte Billing-Entity-/Repository-Abhaengigkeit mehr.
- Verantwortung: Billing liefert bezahlten abgeschlossenen Umsatz; Shift besitzt
  Tageswahl, Lohnrundung und Kassenformel. Umsatz wird weiterhin live gelesen.

## 8. Reporting

- Aggregate/Entities: keine eigenen Entities und keine Schreibaggregate.
- Value Objects: keine Domain-Value-Objects; RevenueOverviewResponse und
  RevenueDayPointResponse sind REST-Projektionen. Externe Lesemodelle sind keine Entities.
- Application Services/Use Cases: RevenueReportService bildet Tages-/Wochen-/Monats-
  summen, Verbrauchswerte und Diagramme; ReorderReportService rendert eine PDF-Liste.
- Repositories: keine eigenen. Ab Phase 4 keine direkten Billing-Repositories mehr.
- REST: ReportController, `/api/reports` (Umsatzuebersicht und Nachbestell-PDF).
- Events: keine publizierten oder konsumierten Events; synchrone Live-Abfragen.
  Ein kuenftiges Reporting-Read-Model darf Events konsumieren, aber nicht die
  fachliche Schreibhoheit fuer Bons oder Lager uebernehmen.
- Erlaubte Abhaengigkeiten: **BillingRevenueQueries** und lesende Inventory-Auskunft.
  ReorderReportService verwendet vorlaeufig InventoryService/ReorderSuggestionResponse;
  dies ist eine dokumentierte Altgrenze (REST-DTO als interner Vertrag), kein neues Muster.
- Verantwortung: deutsche Beschriftungen, Zeitraumwahl und Diagrammaufbereitung
  bleiben Reporting; Bezahlt-/Bonstatus- und Persistenzwissen bleiben Billing.

## Administration und technische Unterstuetzung

Administration ist eine Rolle/Bedienoberflaeche, keine Datenhoheit: Benutzerinitialisierung
gehoert Identity, Oeffnungszeiten/Slotkonfiguration Reservation, Preise Catalog,
Betriebseinstellungen/Lieferanten Inventory und Schichtabrechnung Shift. Es gibt
kein allgemeines Administration-Aggregat, -Repository oder -Event und keinen
zentralen AdminController. Vorhandene Fachcontroller bleiben die REST-Adapter.

`common` liefert BaseEntity, Exceptions, ApiErrorResponse, GlobalExceptionHandler
und RequestCorrelationFilter. `config` verdrahtet Security/OpenAPI/Startvalidierung;
seine Abhaengigkeit auf auth ist technische Komposition. `qr` bietet QrCodeService
und QrProperties ohne Repository/REST-Controller. Mail bleibt Reservationsadapter.
Diese Pakete duerfen nicht zu Sammelstellen fuer kontextuebergreifende Domainlogik werden.

## Kontextbeziehungen: Ist und Ziel

Pfeilrichtung: aufrufender/abhaengiger Kontext -> anbietender Kontext.

| Beziehung | Stand nach Phase 4 | Zielregel |
| --- | --- | --- |
| Report -> Billing | Application-Interface und immutable PaidOrderRevenue | Nur lesender Vertrag, keine Entities/Repositories |
| Shift -> Billing | Application-Interface | Nur Umsatzabfrage; keine Bonmutationen |
| Report -> Inventory | InventoryService plus REST-DTO | Spaeter eigenstaendige Query-Grenze |
| Reservation -> Table | Repository und JPA-Beziehung | Kapazitaets-/Referenzvertrag |
| Billing -> Table / Reservation / Catalog | Direkte Repositories und Entities | Use-Case-/Query-Vertraege und klare Transaktionshoheit |
| Billing -> Inventory | Synchroner Service mit fremder Entity | Expliziter Bestandsbefehl; atomare Invarianten erhalten |
| Inventory -> Catalog | Repositories und Entities | Lesender Katalogvertrag |
| Catalog -> Billing / Inventory | Fremde Repository-Schreibzugriffe | Historie schuetzen; explizite Preis-/Retirement-Regeln statt direkter Mutationen |
| Auth -> operative Kontexte | Keine | Unabhaengig halten |

Noch bestehende Zyklen: Catalog <-> Billing und Catalog <-> Inventory,
einschliesslich des indirekten Billing -> Inventory -> Catalog -> Billing.
Die neue Umsatzgrenze beseitigt keine dieser Schreibzyklen; eine vollstaendig
azyklische Modulstruktur wird hier ausdruecklich **nicht** behauptet.

## Phase-4-Schnitt: Umsatz lesen

Exportiertes Package: `billing.application`. Oeffentlicher Vertrag:

- `BillingRevenueQueries.revenue(start, end)`: bezahlte CLOSED-Positionen,
  Intervall [start, end), Ergebnis BigDecimal.
- `BillingRevenueQueries.consumedVolumeMl(start, end)`: gleiche Auswahl und Grenze,
  Ergebnis in Millilitern; keine implizite Einheitenkonvertierung.
- `BillingRevenueQueries.paidOrdersClosedBetweenInclusive(start, end)`: bestehende
  Diagrammauswahl [start, end], als Liste von PaidOrderRevenue(closedAt, total).

Die unterschiedliche Endgrenze der Diagrammabfrage ist bestehendes Verhalten und
bewusst explizit benannt, nicht nebenbei geaendert. Korrektur von Tages-/Business-Date-
Semantik folgt mit eigener fachlicher Regression. Ebenso bleibt das bisherige
N+1-Muster innerhalb Billing erhalten; dieser Schritt ist kein Performance-Refactor.

BillingRevenueQueryService besitzt Repository-Auswahl und Entity-zu-Lesemodell-
Abbildung, jeweils read-only. Die bestehenden read-only-Transaktionen der Konsumenten
bleiben erhalten; alle Aufrufe sind synchron und nehmen am vorhandenen Spring-
Transaktionskontext teil. Keine neue Eventual Consistency, keine Schema-/REST-Aenderung.

## Durchsetzung und naechste Schritte

- Regressionstests vor dem Umbau sichern Reporting und Shift auf PostgreSQL ab.
- Ein gezielter JUnit-Quelltexttest erlaubt Report/Shift nur Referenzen auf
  billing.application, nicht auf Billing-Services, Domain oder Repositories.
  Das ist eine enge statische Leitplanke, keine vollstaendige Bytecode-/Reflection-
  oder Laufzeitarchitekturpruefung. Keine neue Testdependency erforderlich.
- Neue kontextuebergreifende Funktionen brauchen einen benannten Owner und einen
  expliziten Application-Vertrag. REST-DTOs, Repositories und JPA-Entities sind
  keine neu einzufuehrenden Modul-APIs. Bestehende Ausnahmen stehen oben.
- Events sind in diesem Dokument Kandidaten, nicht bereits implementierte Klassen.
  Keine Event-Bus-/Outbox-Dependency oder asynchrone Bestands-/Zahlungsabwicklung
  ohne vorher geklaerte Transaktions- und Idempotenzregeln.
- Phase 5 bearbeitet Controller/API-Grenzen; Phasen 6/7/8 bearbeiten die dokumentierten
  Auth-/Reservation-/Billing-Invarianten. TD-004 und die fuenf Konkurrenzdefekte bleiben
  offen. Keine globale Paketverschiebung und kein Microservice-Umbau in Phase 4.

## Phase-5-Fortschritt: REST-/Use-Case-Grenze

InventoryInsightsApplicationService gehoert ausschliesslich zu Inventory. Er besitzt
die Orchestrierung und transaktionale DTO-Abbildung der Tages-/Wochenverkaeufe,
Nachbestellberechnungen, Verbrauchsmetadaten und Business-Konfiguration.
InventoryController bleibt HTTP-/Auth-Adapter; Berechnungsformeln bleiben in den
vorhandenen fachlichen Services. Die drei lesenden ReorderOrderService-Listen
behalten ihren Owner Inventory und mappen innerhalb read-only-Transaktionen.

GlobalExceptionHandler und RequestCorrelationFilter sind technische Webadapter,
keine neue Domaene. AuthService uebersetzt lediglich ungueltige JWT-Parsergebnisse
in kuratierte Eingabefehler; Security-Policy/Rotation bleiben Identity & Access.
Neue DTO-Constraints gehoeren zum Inventory-API-Vertrag.

Keine neue kontextuebergreifende Entity- oder Repositoryabhaengigkeit eingefuehrt.
Details zu allen neun Controllern, Erfolgscodes und bewusst beibehaltenem
Fehlerformat: [API_LAYER_REVIEW.md](API_LAYER_REVIEW.md).
