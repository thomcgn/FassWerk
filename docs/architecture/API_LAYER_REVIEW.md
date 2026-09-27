# API Layer Review (Phase 5)

Stand: 2026-09-27. Alle neun REST-Controller geprueft; Grundlage ist die
[Domain Map](DOMAIN_MAP.md). Keine neue fachliche Paketstruktur erzwungen.

## Controller-Inventar

| Controller | Befund / Entscheidung |
| --- | --- |
| AuthController | Duenn: Request-DTO, Clientmetadaten/Bearer-Extraktion, AuthService, Response. JWT-Parserfehler werden im Service als ungueltiger Refresh-Request behandelt; Rotation/Replay unveraendert. |
| TableOrderController | Duenn: DTO-Validierung und Billing-Use-Cases. Keine Preis-, Zahlungs- oder Bestandsberechnung im Adapter. |
| InventoryController | Entity-Mapping und Mehrfachorchestrierung entfernt. InventoryInsightsApplicationService besitzt die elf Insights-/Konfigurations-Use-Cases und mappt DTOs innerhalb der Transaktion. Bestandskommandos bleiben InventoryService; Actor-Aufloesung bleibt im Adapter. |
| ReorderOrderController | Duenn: DTO-/Parameterannahme und ReorderOrderService. Lesendes Entity-Mapping benoetigt eine Service-Transaktion bei OSIV=false. Pflichtfelder des Erstellrequests validieren, unbekannten Status mit 400 ablehnen. |
| MenuController | Duenn: Catalog-Use-Cases und DTOs. Historische Bonloeschung ist ein Service-/Domainproblem, keine Controllerberechnung (Phase 8). |
| ReportController | Duenn: DTO-Auswertung bzw. PDF-Bytes mit Content-Type/Disposition. Query-/PDF-Logik bleibt im Service. |
| ReservationController | DTO-Mapping ueber ReservationMapper, Datumsfallback und QR-Response sind Adapteraufgaben. Mapper liest skalare Felder, keine LAZY-Relationen. Lifecycle-/Kapazitaetsregeln bleiben im Service fuer Phase 7. |
| ShiftSettlementController | Duenn: Datum/DTO, Query-/Save-Use-Case, DTO-Antwort. Keine Kassen-/Lohnrechnung im Controller. |
| TableController | Duenn: validierte Requests und Service-DTOs. |

Kein Controller greift direkt auf Repositories zu, deklariert Transaktionen oder
gibt Entities als HTTP-Response zurueck. Lange Mappingbloecke im Inventory-Adapter
waren die wesentliche gefundene Orchestrierungsverletzung. Die Gesamtservices sind
damit nicht automatisch klein oder vollstaendig entkoppelt.

## Transaktionsgrenzen

Inventory Insights gehoert zum Kontext Inventory. DTO-Mapping erfolgt vor Ende
des Persistence Context, nicht durch Wiedereinschalten von Open Session in View.
Lesende Tages-, Metadaten- und Berechnungsabfragen sind read-only. Wochenabfragen
und Konfigurationslesen bleiben schreibfaehig, weil der vorhandene
SalesConfigurationService fehlende Business Settings initialisiert.
Die drei Nachbestellungslisten mappen innerhalb read-only-Service-Transaktionen.

Nachbestellformeln, ml/Liter-Umrechnung, Aggregationsidempotenz und konkurrierende
Bestandsaenderungen werden hier nicht fachlich veraendert. N+1-Abfragen und fehlende
Obergrenzen/Pagination sind durch die Transaktionskorrektur nicht beseitigt.

## Fehlervertrag

Das bestehende JSON-Format bleibt kompatibel:
timestamp, status, error, message, path, requestId.

GlobalExceptionHandler erweitert ResponseEntityExceptionHandler und behaelt damit
Spring-MVC-Statuscodes und relevante Header (z.B. Allow bei 405). Frameworktexte,
Parserdetails und abgelehnte Werte werden nicht als Fehlermeldung weitergereicht.
DTO-Validierung liefert Feldnamen und Constraint-Texte ohne rejectedValue.

| Fall | HTTP / Verhalten |
| --- | --- |
| Fehlende Parameter, Typkonvertierung, kaputtes JSON, ungueltige DTOs | 400 |
| Ungueltiger Refresh-Token, Zeitzone, Nachbestellstatus | 400 mit kuratierter Meldung, kein Echo des Eingabewerts |
| Nicht vorhandene Ressource/Route | 404 |
| Nicht erlaubte Methode | 405, Allow bleibt erhalten |
| Nicht akzeptiertes Format / Content-Type | 406 / 415 |
| Persistenz-Constraint-Konflikt | 409, keine SQL-/Constraintdetails |
| Unerwartete Exception / explizite Serverfehler | 500 bzw. urspruenglicher 5xx, generische Meldung |

Request-IDs sind auf 1-128 Zeichen aus A-Z, a-z, 0-9, Punkt, Unterstrich und
Bindestrich begrenzt; ungueltige IDs werden ersetzt. Bei gematchten QR-Scanrouten
enthaelt error.path die Vorlage mit {token}, nicht den QR-Token.
Das ist keine umfassende URL-/Logging-Redaktion unbekannter Routen.

Bewusste Entscheidung gegen eine stille RFC-9457-Umstellung: Frontendkonsumenten
lesen bereits message; ein Wechsel auf detail/application-problem+json waere ein
Vertragswechsel. Das Format ist **kein RFC-9457 Problem Detail**. Eine koordinierte
Migration samt Client-/OpenAPI-Vertrag bleibt Phase 10.

Security-Filterfehler laufen nicht durch MVC-Advice. 401/403, EntryPoint/AccessDenied,
Rollenmatrix sowie Auth-Logging werden in Phase 6 geprueft. Die Tests dieser Phase
verwenden einen signierten ADMIN-Token und belegen keine vollstaendige Autorisierung.

## Erfolgscodes und Kompatibilitaet

- Bestehende GET/Update/Aktionsantworten bleiben 200.
- Catalog-, Tisch- und Inventarerstellung behalten 201, Loeschungen 204.
- Reservation-Erstellung, Boneroeffnung und Lieferanten-/Nachbestellerstellung
  behalten ihre bisherigen 200. Eine Normalisierung auf 201 erfolgt nicht still.
- Standardpreis-Upsert im Catalog behaelt 201 auch beim Ersetzen; Vertragsbereinigung
  muss in Phase 10 mit Konsumenten abgestimmt werden.
- Bestehender Artikel ohne Metadaten/Berechnung liefert weiterhin leeres 200;
  fehlender Artikel bei Metadaten und letzter Berechnung liefert 404.
- calculate-reorder ohne verknuepfte Variante liefert nun leeres 200 statt NPE/500.
- GET consumption-metadata liest echte Daten; die irrefuehrende 501-Dokumentation
  des bisherigen leeren Platzhalters wurde entfernt.

Nicht jeder Request besitzt damit bereits eine vollstaendige fachliche Validierung.
Beispielsweise Lieferanten-Patchsemantik, erlaubte Einheiten, Statusuebergaenge und
fachliche Werteobergrenzen gehoeren zu den anschliessenden Domain-/Contractphasen.
