# Production-Readiness Baseline – PHASE 0

Stand: 28.09.2026, analysierter `main`: `fb832a8` (zu Beginn sauber).
Auftrag: [Production-Ready Roadmap](../FASSWERK_PRODUCTION_READY_ROADMAP.md).
Diese Phase 0 gehört zur Production-Roadmap; die älteren Architektur-Phasenberichte haben eine andere Nummerierung.

Fortschreibung: [Phase 1 – Zahlungs- und Request-Idempotenz](PHASE_1_REPORT.md). Diese Baseline beschreibt weiterhin den Stand vor Phase 1.

## Ergebnis und Nachweisgrenzen

Die Auditkorrekturen einschließlich der genehmigten einmaligen Session-Sperre in V26 sind implementiert. Die TABLE-Abläufe besitzen bereits transaktionale Konsistenz, DB-Sperren, fachliche Zustandsprüfungen und teilweise persistente Idempotenz. Ein Neuaufbau dieser Mechanismen ist nicht erforderlich.

Noch keine Produktionsfreigabe: Absolute Bestandsänderungen können neueren Bestand überschreiben; die Bedienoberfläche bewahrt Idempotenzschlüssel für Wiederholungen nach unklarer Antwort nicht auf. Daneben fehlen Konflikterkennung bei Schichtänderungen und ein wiederholungssicherer manueller Tagesabschluss. Nachweise für tatsächliche Produktionsmigration, Alarmzustellung und Wiederherstellungsziele liegen nicht vor.

**Nachweisarten:** „Codebefund“ bezeichnet einen nachvollziehbaren Implementierungspfad, keine in dieser Phase neu ausgeführte Fehlerreproduktion. „Getestet“ bezieht sich auf die unten genannten vorhandenen Tests. „Offen“ bezeichnet fehlende Abnahme oder Betriebsinformation, nicht einen beobachteten Ausfall. Vor Fehlerbehebung ist jeweils ein gezielter Regressionstest erforderlich. Phase 0 verändert ausschließlich diese Dokumentation.

Quellpfade im Folgenden sind relativ zum Repository. Java-Paketwurzel ist `backend/src/main/java/org/thomcgn/backend/`, Testwurzel entsprechend `backend/src/test/java/org/thomcgn/backend/`.

## Fachliche Ausgangslage

- Bestand und Absatz werden beim Hinzufügen einer Bonposition gebucht, nicht erst beim Bezahlen. Storno kehrt die ursprüngliche Absatzbuchung um. Ein unbezahlter offener Bon mit abgezogenem Bestand ist fachlich vorgesehen.
- Umsatz entsteht aus bezahlten geschlossenen Bons. Split Payment verschiebt Mengen in einen bezahlten Teilbon und darf keinen zweiten Bestandsabzug verursachen. Es gibt keine angebundene Zahlungsanbieter-Transaktion; „bezahlt“ ist eine interne Bestätigung.
- Absatzdatum pro Position und Umsatzdatum beim Abschluss haben unterschiedliche Zwecke. V27/V28 sichern neue Datensätze; alte unbekannte Absatzdaten werden nicht erfunden.
- Archivierte Schuld und aktuelle Tischbelegung sind getrennt. Ein neuer Besuch darf den alten Deckel nicht als bezahlt markieren.
- `common/persistence/BookingMutationLock.java` verwendet einen PostgreSQL-Transaktions-Advisory-Lock. Das wirkt instanzübergreifend. Ein solcher Lock verhindert jedoch nicht, dass ein später eintreffendes Formular einen veralteten absoluten Wert schreibt.
- `common/domain/BaseEntity.java` enthält keine Versionsspalte. Zeilensperren existieren gezielt; eine allgemeine Erkennung veralteter Formulare existiert nicht.

## Kritische Workflows

### 1. Login, Refresh, Logout und Berechtigungen

**Entities/Services:** Benutzerkonto, RefreshToken/Session-Familie und Rate-Limit-Datensätze; `auth/service/AuthService.java`, AuthRateLimiter, JWT-Prüfung; `config/SecurityConfig.java`, `config/StartupSecurityConfiguration.java`; BFF unter `frontend/app/api/auth/`, `frontend/proxy.ts`.

**Endpoints/Auth:** `/api/auth/login`, `/refresh`, `/logout` sowie geschützte Sessionverwaltung. Backend arbeitet mit Bearer-Token und expliziten Rollenregeln, unbekannte Routen werden abgewiesen. Der BFF prüft bei unsicheren Methoden die genaue Origin und verwendet HttpOnly-Cookies. Datenbankgestützte Limits gelten für Login; unbekannte/inaktive Konten durchlaufen Dummy-BCrypt.

**Transaktion/Idempotenz/Concurrency:** Standard-Refresh rotiert und erkennt Replay. Browser-Sessions verwenden einen stabilen zufälligen, nur gehasht gespeicherten Refresh-Schlüssel mit absolutem Ablauf, damit mehrere BFF-Instanzen parallel auffrischen können. Account-/Session-Sperren werden serverseitig geprüft. Logout entzieht die Session; keine alleinige Abhängigkeit vom Cookie-Löschen.

**Fehler/Rollback:** V26 sperrt vorhandene aktive Sessions einmalig als `SECURITY_UPGRADE`. Deren spätere Verwendung liefert 401, ohne eine neue Anmeldung zu widerrufen. Gewöhnliches Replay rotierter Tokens bleibt davon getrennt. Startup-Prüfung weist unsichere Produktionskonfiguration vor Flyway zurück.

**Tests:** SecurityHardeningIntegrationTest, SecurityRoleMatrixIntegrationTest, AuditRemediationIntegrationTest (`upgradeRevokedTokenCannotInvalidateANewLogin`), AuditSessionMigrationTest, ConcurrentWritesCharacterizationTest (`concurrentRefreshHasOneSuccessorAndReplayRevokesIt`), SecretConfigurationTest, BFF-Security-E2E und reale Zwei-BFF-Abnahme.

**Rest:** Öffentliche Reservierungsanlage besitzt nicht denselben Rate-Limiter (R04). Reverse-Proxy/TLS, tatsächliche Client-IP-Strategie und sichere externe Secrets sind am Deployment zu bestätigen. Die Backend-Adresse sieht bei BFF-Verkehr zunächst den BFF; beliebige Forwarded-Header dürfen nicht als vertrauenswürdig gelten.

### 2. Reservierung, Check-in und Tischbelegung

**Entities/Services:** Reservation, ReservationTable, DiningTable, TableOrder; ReservationService, `reservation/application/ReservationRules.java`, `billing/application/ReservationBillingAdapter.java`, TableService.

**Endpoints/Auth:** Öffentliche Anlage `POST /api/reservations`; Lesen/Scan sowie `/{id}/confirm`, `/cancel`, `/check-in`, `/complete` für berechtigte Mitarbeiter gemäß SecurityConfig. Tischverwaltung unter `/api/tables` für ADMIN/BARCHEF/STAFF.

**Transaktion/Idempotenz/Concurrency:** Mutationen laufen transaktional unter dem gemeinsamen Buchungslock. Gruppen werden konservativ für den Geschäftstag auf Tische verteilt. Check-in ist zustandsgeprüft; der Adapter nimmt verpflichtend an der bestehenden Transaktion teil und erzeugt nicht erneut denselben Tischbon. Kapazitäts-/Statusänderungen berücksichtigen bestehende Belegungen. Belegung endet durch fachlichen Abschluss, nicht bloß durch Zeitablauf.

**Fehler/Rollback:** Konflikt an einem zweiten Gruppentisch rollt den gesamten Check-in zurück. Fehler beim Abschluss rollen auch die Freigabe zurück. Ungültige/mehrdeutige lokale DST-Zeiten werden abgewiesen. Reservierungsmail wird erst nach Commit versendet; SMTP-Fehler machen die Reservierung nicht rückgängig.

**Tests:** ReservationLifecycleIntegrationTest, insbesondere `conflictingSecondTableRollsBackEntireGroupCheckIn`, `paymentRollbackAlsoRollsBackTableReleaseAndReservationCompletion`, `concurrentCheckInsCreateOnlyOneBillPerGroupTable`, `archivedDebtDoesNotBlockNewGroupOrBecomePaidWhenNewGroupPays`; ConcurrentWritesCharacterizationTest (`parallelReservationsCannotConsumeTheSameLastTable`); Reservierungs-E2E.

**Rest:** R04 öffentliche Anlage, R08 Mailwiederholung. Keine eigenständige Besuchs-/Walk-in-Gästezahl-Abbildung; Namens-/Formularänderungen haben keine allgemeine Revisionserkennung. Das ist von der bereits geschützten Kapazitätszuteilung zu unterscheiden.

### 3. Position hinzufügen und stornieren

**Entities/Services:** TableOrder, TableOrderItem, BillingOperation, InventoryItem, InventoryMovement, tägliche/wöchentliche Absatzaggregate; `billing/service/TableOrderService.java`, InventoryService, DrinkSalesTrackingService, ReorderCalculationService.

**Endpoints/Auth:** `POST /api/table-orders/{id}/items`, `DELETE /api/table-orders/{id}/items/{itemId}`, Mitarbeiterrollen. Optionaler `Idempotency-Key` wird über den BFF weitergereicht.

**Transaktion/Idempotenz/Concurrency:** Buchungslock und äußere Service-Transaktion umfassen Position, Bestand, Bewegung, Absatz und Nachbestellberechnung. Persistente BillingOperation mit eindeutigem Schlüssel und Request-Fingerprint schützt Wiederholungen mit demselben Schlüssel; abweichender Payload ergibt Konflikt. Positionszusammenführung berücksichtigt das Absatzdatum. Storno korrigiert dieses Datum, auch nach Tageswechsel.

**Fehler/Rollback:** Fehlbestand oder nachgelagerter Berechnungs-/DB-Fehler hinterlässt keine Teilbuchung. Storno von Legacy-Positionen ohne zuverlässig zuordenbares Absatzdatum wird abgewiesen, statt einen Tag zu erfinden.

**Tests:** BillingInventoryPhase8IntegrationTest (`repeatedAddWithSameKeyDeductsStockExactlyOnceAndRejectsChangedPayload`, `exactZeroIsAllowedAndInsufficientFollowUpRollsBackItemAndMovement`, `reorderFailureRollsBackOrderItemStockMovementAndSalesAggregate`, `cancellationAfterBusinessDayChangeReversesOriginalDay`, `litreStockPreservesOneMillilitrePrecision`).

**Rest:** R02 betrifft die tatsächliche Wiederholung über die Oberfläche. Zusammenführen einer bestehenden Position übernimmt den aktuellen Variantenpreis für die zusammengefasste Menge: gewünschte Preisänderungspolitik klären und testen (R05). R07 betrifft nicht zuordenbare Altdaten.

### 4. Split Payment, Abschluss, Deckel und Wiederöffnung

**Entities/Services:** TableOrder, TableOrderItem, BillingOperation, Reservation/Tischzuordnung; TableOrderService und transaktionale Belegungsfreigabe.

**Endpoints/Auth:** `/api/table-orders/{id}/split-payment`, `/close`, `/mark-unpaid`, `/reopen-unpaid`; offene Bons/Archiv per GET; Mitarbeiterrollen.

**Transaktion/Idempotenz/Concurrency:** Split validiert eindeutige Positions-IDs und verfügbare Mengen, verschiebt Mengen atomar in einen bezahlten Teilbon und speichert das Ergebnis zum Schlüssel. Abschluss eines bereits bezahlten geschlossenen Bons ist wirkungslos. Archivierung und Wiederöffnung prüfen Zustände; Wiederöffnung berücksichtigt neu belegte/gesperrte Tische. Der globale Lock serialisiert konkurrierende Mutationen.

**Fehler/Rollback:** Ungültiger Split hinterlässt weder Teilumsatz noch Mengenverschiebung. Zahlung und gegebenenfalls Tischfreigabe werden gemeinsam zurückgerollt. Archivieren beendet den Besuch, begleicht aber keine Schuld.

**Tests:** BillingInventoryPhase8IntegrationTest (`repeatedSplitReturnsSamePaidReceiptWithoutDuplicatingRevenue`, `invalidSplitAndRepeatedCloseCannotLeavePartialFinancialState`); ConcurrentWritesCharacterizationTest (`parallelPartialPaymentsCannotDuplicatePaidQuantity`); ReservationLifecycleIntegrationTest; Split-E2E und reale Abnahme mit verlorener Antwort und App-Neustart.

**Rest:** R02: Zwei verschiedene Schlüssel stellen unterschiedliche Befehle dar; bei ausreichender Restmenge kann eine unbeabsichtigte Wiederholung erneut wirksam sein. BillingOperation ersetzt keine vollständige Änderungshistorie mit Akteur/Grund (R05). DIRECT-Verkauf fehlt (R06); keine Zahlungsanbieter- oder Kartenintegration behaupten.

### 5. Bestand anpassen und Stammdaten bearbeiten

**Entities/Services:** InventoryItem, InventoryMovement, Getränk-/Variantenverknüpfung; `inventory/service/InventoryService.java` (`updateItem`, `applyRequest`, `synchronizePackageCount`), Repository-Zeilensperre.

**Endpoints/Auth:** CRUD und Bestandsaktionen unter `/api/inventory`; Anlage/Änderung/Löschung und manuelle Bestandsanpassung ADMIN, Lesen auch BARCHEF/STAFF gemäß SecurityConfig. Frontend: `frontend/app/inventory/inventory-client.tsx`.

**Transaktion/Idempotenz/Concurrency:** Delta-Anpassungen sperren die Bestandszeile und buchen Bewegung und Menge gemeinsam; ein Operationsschlüssel schützt identische Anpassungen. Negative Mengen werden verhindert. Auch `updateItem` sperrt die Zeile, schreibt aber absolute Formularwerte ohne erwartete Revision.

**Fehler/Rollback:** Fehler an der Bewegung rollt die Anpassung zurück. Die Sperre schützt nicht gegen bereits vor dem Request veraltete Formulardaten.

**Tests:** ConcurrentWritesCharacterizationTest (`concurrentStockAdjustmentsCannotLoseAnUpdateOrGoNegative`, `movementConstraintFailureRollsBackStockMutation`); BillingInventoryPhase8IntegrationTest (`manualAdjustmentIdempotencyPreventsDuplicateMovement`, Präzisionstest).

**Rest – R01, Codebefund:** `applyRequest` berechnet Gesamtbestand aus `packagesInStock * contentPerPackage`. Die Variantenverknüpfung sendet dafür zwischengespeicherte Bestandswerte erneut per PUT. Zwischenzeitlicher Verbrauch kann dadurch überschrieben werden. Zusätzlich wird die Packungszahl auf zwei Stellen gerundet: 9,999 Liter bei 10 Liter/Packung werden als 1,00 Packungen zurückgegeben; erneutes Speichern kann daraus 10 Liter machen. Dieser Pfad erzeugt keine entsprechende Bestandskorrekturbewegung. Vorhandene Delta-/Präzisionstests testen diesen PUT-Roundtrip nicht. Erforderlich: separate Tests für Metadatenänderung nach Verbrauch und nach Rundung; anschließend kleinste Trennung von Stammdatenänderung und expliziter Bestandskorrektur plus Konflikterkennung.

### 6. Lieferung, Scheduler und Geschäftstagskonfiguration

**Entities/Services:** ReorderOrder, InventoryItem/Movement, Absatzaggregate, InventoryBusinessSettings; ReorderOrderService, InventoryService, Scheduler mit Einzelverarbeitung, SalesConfigurationService.

**Endpoints/Auth:** `/api/reorder/orders`, `PUT /api/reorder/orders/{id}/status?status=RECEIVED`, Lieferantenverwaltung; `GET/PUT /api/inventory/configuration`, `POST /api/inventory/configuration/manual-day-close`. Nachbestell-Schreibrechte und manueller Tagesabschluss ADMIN/BARCHEF; Konfigurations-PUT ADMIN.

**Transaktion/Idempotenz/Concurrency:** Wareneingang erhöht Bestand und setzt Endstatus atomar unter Sperre; erneuter Empfang erzeugt keine zweite Bewegung. Ungültige Einheiten werden abgewiesen. Scheduler verarbeitet Einträge in getrennten Transaktionen (`REQUIRES_NEW`), Wochenjobs sind gegen Konkurrenz geschützt und berechnen alte Wochen nach. Konfiguration ist persistent und validiert.

**Fehler/Rollback:** Fehler eines Scheduler-Eintrags verhindert nicht die gesunden Einträge; ungültige Lieferung verändert weder Status noch Bestand. Nicht zuordenbare Legacy-Absätze werden erhalten und gemeldet.

**Tests:** BillingInventoryPhase8IntegrationTest (`concurrentDeliveryReceiptsIncreaseStockExactlyOnceAndCannotBeReverted`, `invalidDeliveryUnitLeavesStockAndStatusUntouched`, `schedulerCommitsHealthyItemAfterAnotherItemDatabaseFailure`, `concurrentWeeklyJobsCatchUpOldWeeksAndPreserveUnattributableLegacyRows`), BusinessDate/DST-Tests.

**Rest:** R12: `closeBusinessDayManually` erhöht das Datum bei jedem erfolgreichen Aufruf erneut; kein Schlüssel/erwartetes Ausgangsdatum schützt den Retry. Konfigurationsformulare haben keine Revision. Initiales Anlegen einer Nachbestellung besitzt ebenfalls keine persistente Request-Idempotenz; Wiederholungsabsicht prüfen. Historische Wochenberechnung bei großen Datenmengen messen (R11).

### 7. Schichtabrechnung

**Entities/Services:** ShiftSettlement, ShiftWorkerEntry; `shift/service/ShiftSettlementService.java`, lesende Umsatzschnittstelle zur Billing-Domain.

**Endpoints/Auth:** GET `/api/shift-settlements?from=…&to=…`, GET/PUT `/api/shift-settlements/{date}` für ADMIN/BARCHEF/STAFF.

**Transaktion/Idempotenz/Concurrency:** `saveByDate` ist transaktional. Ein eindeutiges Datum verhindert doppelte Schichtzeilen; identischer Inhalt ist als Zustand erneut speicherbar. Bestehende Einträge werden beim Speichern vollständig ersetzt (`clear`, orphanRemoval). Keine Version und keine Konflikterkennung gegenüber einem früher gelesenen Stand.

**Fehler/Rollback:** DB-Fehler rollen die gesamte Speicherung zurück; konkurrierendes erstmaliges Anlegen kann einen Konflikt erzeugen. Das verhindert keinen stillen Last-Writer-Wins-Verlust an einer bestehenden Schicht.

**Tests:** RevenueBoundaryIntegrationTest (`savedShiftKeepsWagesAndCashCalculationInShiftContext`) und Umsatz-/Geschäftstagsgrenzen. Kein gezielter Zwei-Geräte-Test für veraltetes PUT vorhanden.

**Rest:** R03: `frontend/app/shift-settlement/shift-settlement-client.tsx` übermittelt Formularstände ohne Revision. R05: keine unveränderliche Abschluss-/Korrekturhistorie. Umsatz wird aus den bezahlten Bons abgeleitet; formalen Schichtabschluss und gegebenenfalls Zahlungsarten erst fachlich definieren.

### 8. Katalog, Umsatzberichte und PDF

**Entities/Services:** Drink, DrinkVariant, VolumePrice, Bons/Positionen, Reorder-Daten; MenuService, RevenueReportService, PDF-Service, Billing-Leseschnittstellen.

**Endpoints/Auth:** Katalog unter `/api/drinks`, `/api/drink-variants`, `/api/volume-prices`; `/api/reports/revenue-overview` für Mitarbeiter, `/api/reports/reorder-list.pdf` ADMIN. Genaue CRUD-Rollen gemäß SecurityConfig.

**Transaktion/Idempotenz/Concurrency:** Katalogänderungen sind transaktional; Soft Delete erhält historische Belege. Berichte lesen über die Billing-Schnittstelle. Umsatzdatum und Tagesgrenzen sind zentralisiert. PDF paginiert und bettet eine Unicode-Schrift ein. GET erzeugt keine Buchung; Preisformulare besitzen keine Revisionskontrolle.

**Fehler/Rollback:** Validierungsfehler verhindern Katalogänderungen; PDF-Ausgabe verändert keine Geschäftsdaten. Alte Abschlusszeitpunkte werden über die bestehende Zeitkonvention interpretiert; keine geratenen Backfills.

**Tests:** RevenueReportServicePaidOnlyTest, RevenueBoundaryIntegrationTest, ReorderReportPaginationTest (160 Einträge/Unicode), BillingReadBoundaryTest, `deletingCatalogDrinkPreservesPaidBillAndRevenue`, manuelle Geschäftstags-/DST-Tests.

**Rest:** Preis-/Korrekturhistorie und klare Preisbindung offener Positionen (R05), reale Altdaten/Zeitkonvention (R07), große Berichtsmengen (R11).

### 9. Browser, BFF und Wiederanlauf

**Daten/Services:** Autoritative Bons, Bestände, Sessions und Reservierungen liegen im Backend. BFF-Routen unter `frontend/app/api/`; `frontend/app/table-billing/table-billing-client.tsx` hält zusätzlich Ansichtszustand und Archivcache in sessionStorage.

**Endpoints/Auth:** Die Oberfläche verwendet gleichnamige `/api/table-orders/…`-Routen; Origin-Prüfung, serverseitige Cookies und Backend-Rollen bleiben aktiv. Offene Bons und gespeicherte IDs können nach Reload erneut gelesen werden.

**Transaktion/Idempotenz/Concurrency:** Der BFF ersetzt keine DB-Transaktion. `addItem`, `removeItem` und `splitPayment` erzeugen `crypto.randomUUID()` innerhalb jeder Handler-Ausführung. Ein erneut gestarteter Handler bekommt einen neuen Schlüssel, auch wenn die vorherige Wirkung unbekannt ist.

**Fehler/Recovery:** Es gibt keinen dauerhaften Pending-Befehl mit wiederverwendbarem Schlüssel und keinen durchgängigen automatischen Zustandsabgleich nach verlorener Antwort in diesen Handlern. Der Archivcache ist keine alleinige Datenquelle, kann aber eine veraltete Ansicht liefern. Reale Abnahme beweist Serverwiederanlauf und Retry mit bewusst beibehaltenem Schlüssel; sie beweist nicht den normalen UI-Retry nach verlorenem Response.

**Tests:** 18 kritische Playwright-Tests, Unit-/BFF-Security-Tests, `frontend/scripts/fullstack-acceptance.mjs` und `scripts/fullstack-acceptance.sh`.

**Rest:** R02 und R10. Oberflächen-Test muss die Antwort nach erfolgreicher Backend-Verarbeitung verlieren lassen und anschließend die tatsächliche Benutzeraktion auslösen. Ein Test, der selbst denselben Schlüssel setzt, reicht dafür nicht.

## Betrieb, Datenbank und Lieferkette

| Bereich | Vorhanden | Noch offen / Grenze |
| --- | --- | --- |
| Docker/Compose | Digest-gepinnte Basen, mehrstufige Builds, nicht privilegierte Prozesse, read-only/tmpfs, reduzierte Capabilities, Healthchecks, persistentes PostgreSQL-Volume | Tatsächlicher TLS-/Proxy-Aufbau, Ressourcenbedarf und Fehlerbetrieb nicht belegt. Frontend-Port ist veröffentlicht; DB/Backend sind lokal gebunden. `unless-stopped` startet einen nur als unhealthy markierten laufenden Container nicht automatisch neu. |
| Produktionskonfiguration | Startup-Prüfung von JWT/DB/Bootstrap, keine produktiven Default-Zugangsdaten; Prod ohne SQL-Logging, JDBC UTC | Externe Origins/QR-URL statt localhost setzen; Mail-Konfiguration über Deployment ergänzen. Mehrere Instanzen müssen dieselbe fachliche Zeitkonfiguration verwenden. |
| Flyway | 21 Migrationen V1–V9 und V17–V28; gezielte V25→V26-Abnahme und vollständige frische Testdatenbanken | Nummernlücken sind kein Anlass zum Umnummerieren. Reale Flyway-Historie und Altbestand vor Rollout prüfen; weder blind baseline/repair noch alte Migrationen ändern. V26 bewirkt genehmigte einmalige Neuanmeldung. |
| CI/Verträge | Backend verify/OpenAPI-Vergleich, generierte Frontend-Typen, Lint/Build/Tests, Dependency- und Imagescans, Image-Smoke, Release-Gate; echte Abnahme nutzt gebautes Backend-JAR | Aktuelle lokale Prüfung ist kein Nachweis eines neuen GitHub-Laufs oder aktivierter Branch-Protection. Image-Build überspringt Maven-Tests und ist auf das vorgeschaltete Gate angewiesen. |
| Logging/Actuator | Strukturierte Prod-Logs, Request-Korrelation, sichere Fehlerantworten, Auth-/Mail-/Scheduler-Metriken; Health ohne Details öffentlich, Metrics geschützt | Dedizierter Scraper-Zugang/Tokenrotation, Alarmlieferung und Störungsprobe nicht belegt. Swagger/OpenAPI ist öffentlich freigegeben; gewünschte Produktionspolitik festlegen. |
| Backup/Restore | [Runbook](../operations/backup-restore.md), synthetischer pg_dump/pg_restore mit Zeilen-/Flyway-Vergleich | Kein Nachweis für verschlüsselte externe Backups, Aufbewahrung, echte Datenmenge, gemessene Produktions-RPO/RTO und zuständige Person. |
| Monitoring | [Runbook](../operations/monitoring-alerting.md) und ObservabilityIntegrationTest | Kein Ende-zu-Ende-Nachweis bis zum tatsächlich erreichbaren Alarmempfänger. |
| Datenlebenszyklus | Historische Belege durch Soft Delete geschützt, Session-Sperren serverseitig | Keine vollständig abgenommene Retention-/Lösch-/Archivstrategie für Personen-, Session-, Bewegungs- und Operationsdaten. |

Historische Scanergebnisse und frühere Audit-Abnahmen stehen in [Audit Remediation](../architecture/AUDIT_REMEDIATION.md) und [Security Dependency Remediation](../architecture/SECURITY_DEPENDENCY_REMEDIATION.md). Sie sind keine in Phase 0 neu durchgeführten Scans.

## Priorisierte verbleibende Befunde

| ID | Priorität / Evidenz | Konsequenz und nächste Abnahme |
| --- | --- | --- |
| R01 | **P0, Codebefund** | Absoluter Inventory-PUT kann Verbrauch überschreiben oder Rundungsrest zurückbuchen. Regression mit zwei Clients sowie 9,999-Liter-Metadaten-Roundtrip; Stammdaten und Bestandskorrektur sauber trennen. Phase 2/4. |
| R02 | **P0, bedingte finanzielle/bestandsbezogene Wirkung aus Codepfad** | UI-Retry mit neuem Schlüssel kann erneute Position/Storno/Teilzahlung auslösen, sofern der aktuelle Bon das noch zulässt. Kein in dieser Phase reproduzierter Doppelzahlungsfall. Schlüssel pro fachlichem Befehl bewahren und echten UI-Timeout-Test ergänzen. Phase 1. |
| R03 | **P1, Codebefund** | Veraltetes Schichtformular ersetzt neuere Mitarbeiter-/Kassenangaben ohne Konflikt. Zwei-Geräte-Test und Revision/gezielte Konfliktstrategie. Phase 2. |
| R04 | **P0, fehlender Anwendungsschutz; Exposition deploymentabhängig** | Öffentliche Reservierungsanlage ohne entsprechendes Rate-Limit kann missbraucht werden. Kein beobachteter Angriff; vorhandenen Gateway-Schutz ermitteln, Grenzen und Ablehnung testen. Phase 10. |
| R05 | **P1, fehlende Nachvollziehbarkeit / Fachentscheidung offen** | Bestandsbewegungen und Idempotenzbelege existieren, aber keine vollständige Akteur-/Grund-Historie für Preise, Wiederöffnung, Schicht-/Finanzkorrekturen. Preisbindung offener Positionen definieren. Phase 6. |
| R06 | **P1, Roadmap-Funktionslücke** | DIRECT-Verkauf und eigene Walk-in-/Besuchsabbildung fehlen. TABLE funktioniert; dies ist kein nachgewiesener Ausfall bestehender Tischbons. Phasen 7–9. |
| R07 | **P1, reale Daten unbekannt** | Unzuordenbare Legacy-Absätze blockieren Storno; reale Migrationshistorie/Zeitzonen müssen vor Rollout geprüft werden. Anonymisierte/restaurierte reale Kopie und dokumentierter Abgleich. Phase 13. |
| R08 | **P2, Codebefund** | Mail nach Commit ist korrekt entkoppelt, aber ohne dauerhafte Outbox/Retry kann eine Nachricht bei SMTP-Ausfall dauerhaft fehlen. Wiederholung und Betriebsanzeige festlegen. |
| R09 | **P2, Betriebsnachweis fehlt** | Restore-Ziele, Alarmzustellung, TLS/Secrets/Origins und unhealthy-Verhalten im tatsächlichen Betrieb abnehmen. Phasen 11–15/21. |
| R10 | **P2, Codebefund** | Unklarer Requestausgang wird nicht durchgängig verständlich angezeigt/abgeglichen; offene Befehle über Reload nicht wiederherstellbar. Phasen 5/16/17. |
| R11 | **P3, Messung fehlt** | Globaler Buchungslock, wiederholte Belegungsabfragen und historische Wochenberechnung sind Lasttest-Kandidaten, keine bewiesenen Performancefehler. Phase 19. |
| R12 | **P1, Codebefund** | Wiederholung von `manual-day-close` schiebt den Geschäftstag erneut weiter. Erwartetes Ausgangsdatum oder persistenter Operationsschlüssel; verlorene Antwort plus Retry testen. Phase 1/11. |

P0 bedeutet gemäß Roadmap: mögliche falsche Zahlung, falscher Bestand oder Auth-/Security-Risiko. Ein grüner vorhandener Testlauf schließt diese nicht abgedeckten Pfade nicht aus. Keine der offenen Produktionsinformationen wird durch eine Annahme als erledigt markiert.

## Abgleich sämtlicher Folgephasen

| Phase | Stand | Noch erforderlich |
| --- | --- | --- |
| 1 Idempotenz | Teilweise | Backend-Schlüssel/Zustände vorhanden; UI-Wiederholung und manueller Tagesabschluss R02/R12. DIRECT erst nach dessen Einführung. |
| 2 Concurrent Editing | Teilweise | Locks schützen Transaktionen; stale Inventory-/Shift-Formulare R01/R03 erkennen. |
| 3 Atomare Prozesse | Für bestehende TABLE-Pfade weitgehend getestet | Stock-at-add beibehalten; spätere neue Verkaufs-/Korrekturpfade gesondert abnehmen. Keine pauschale Bestandsbuchung erst bei Zahlung einführen. |
| 4 Bestandskonkurrenz | Teilweise | Delta-/Wareneingangssperren vorhanden; absoluter PUT R01 offen. |
| 5 Server-Recovery | Teilweise | Persistente Bons und Neustart geprüft; Pending-Befehle/unklare Antworten R10. |
| 6 Audit/Korrekturen | Teilweise | Bewegungen/Operationsbelege vorhanden; vollständige Änderungsgründe/Akteure und Korrekturregeln R05. |
| 7 DIRECT | Nicht umgesetzt | Eigenständiger fachlicher Ablauf ohne künstlichen Tisch, einschließlich Zahlung/Bestand/Retry. |
| 8 Verkauf/Belegung | Teilweise | TABLE und Archivschuld getrennt; explizite Besuchs-/Walk-in-Abbildung bewerten. |
| 9 Kapazität | Teilweise | Gruppenreservierungen und Tischänderungen geschützt; Walk-in-Gästezahl/Überbelegungsregeln offen. |
| 10 Security | Weitgehend technisch abgesichert, nicht vollständig abgenommen | Öffentliche Reservierungsanlage R04, tatsächliches Deployment, Dokumentationszugriff. |
| 11 Konfiguration | Teilweise | Persistenz/Validierung vorhanden; Revision/Tagesabschluss, echte Origins und Betriebswerte. |
| 12 Docker | Weitgehend umgesetzt | Mehrstufige sichere Images und CI vorhanden; tatsächliches Deployment/Störungsprobe offen. |
| 13 Migration | Teilweise | Frischaufbau und gezieltes Upgrade getestet; reale Historie und Altdaten R07. |
| 14 Backup | Teilweise | Runbook und synthetischer Restore; reale RPO/RTO, externe Ablage und Zuständigkeit. |
| 15 Observability | Teilweise | Logs/Metriken vorhanden; Scraper und Alarmzustellung nachweisen. |
| 16 Fehler-UX | Teilweise | Fehlerverträge vorhanden; Konflikt-/Offline-/Timeout-Recovery R10. |
| 17 Mitarbeiter-UX | Nicht abschließend abgenommen | Reale Geräte, Stressbedienung und Ablaufprüfung durch Betriebspersonal. |
| 18 Tests | Breite vorhandene Basis | Stale PUT, tatsächlicher UI-Retry, Tagesabschluss-Retry, spätere DIRECT-Pfade ergänzen. |
| 19 Performance | Keine belastbare Lastabnahme | Repräsentatives Datenvolumen, parallele Geräte und Antwortzeitziele messen. |
| 20 Datenlebenszyklus | Teilweise | Historische Belege bleiben erhalten; Löschung/Aufbewahrung/Archivierung definieren und testen. |
| 21 Runbook | Teilweise | Backup/Monitoring/Deployment dokumentiert; vollständige Bedien- und Notfallprobe offen. |
| 22 Repository/Docs | Teilweise | Zahlreiche Berichte/Verträge vorhanden; historische und aktuelle Phasen klar einordnen, Release-Dokumentation konsolidieren. |
| 23 Release Candidate | Nicht erreicht | Offene P0/P1 behandeln, echte Betriebsnachweise und explizite Gesamtfreigabe. |

Keine komplette Folgephase wird allein aufgrund ähnlicher älterer Phasennamen als erledigt gewertet.

## Konkrete nächste Umsetzung: Phase 1

1. Zunächst verlorene Antwort nach erfolgreich verarbeitetem Add/Remove/Split über die echte Oberfläche reproduzieren. Derselbe fachliche Befehl muss bei Retry denselben Schlüssel und Payload verwenden.
2. Schlüssel und Befehlsstatus mindestens über den Retry-Lebenszyklus erhalten; neuen Schlüssel erst für eine ausdrücklich neue Aktion erzeugen. Ein HTTP-Fehler und ein unbekannter Ausgang benötigen unterschiedliche Behandlung.
3. Double-Tap und parallele Geräte mit realem Backend prüfen; vorhandene DB-Constraints, BookingMutationLock und BillingOperation wiederverwenden. Konflikt bei gleichem Schlüssel und anderem Payload beibehalten.
4. Abschluss, Check-in, Archivierung/Wiederöffnung gegen wiederholte Zustandsübergänge regressionsprüfen; vorhandene Zustandsidempotenz nicht unnötig durch neue Modelle ersetzen.
5. Manuellen Tagesabschluss gegen verlorene Antwort und Wiederholung absichern (R12). Danach Vertragsprüfung, Builds und relevante Tests ausführen.

R01 bleibt unabhängig davon ein Produktionsblocker und muss spätestens in Phase 2/4 behoben werden; bis dahin keine Produktionsfreigabe. Falls die Reihenfolge geändert werden soll, zuerst den reproduzierenden R01-Test und dessen begrenzte Korrektur vorziehen, nicht nebenbei während Phase 0 implementieren.

Für spätere Betriebsabnahme fehlen insbesondere: tatsächliche Domains/Proxy-Topologie, Geräte-/Lastprofil, reale Flyway-Historie und Altdaten, gewünschte Preis-/Zahlungs-/Korrekturregeln, Backup-Ziele und Alarmempfänger sowie Aufbewahrungsvorgaben. Diese Angaben blockieren die vorliegende Bestandsaufnahme nicht.

## PHASE 0 REPORT

**Phasenergebnis: PASS** für die geforderte Bestandsaufnahme und vorhandenen Prüfgates; Produktionsfreigabe weiterhin offen.

Änderungen: ausschließlich diese Baseline. Keine Produktivcodeänderung.

Migrationen: keine; vorhandene V26–V28 unverändert.

| Prüfung | Ergebnis |
| --- | --- |
| Backend Build | **PASS** – isolierte Kopie des analysierten Backends, `env -u DEBUG -u TRACE ./mvnw -B clean verify` mit Testcontainers/PostgreSQL. |
| Backend Tests | **PASS** – 230 Tests, 0 Fehler, 0 fehlgeschlagen, 0 übersprungen. Enthält V26-Upgrade-/Sessionregression. |
| Frontend API-Vertrag | **PASS** – `npm run api:check`. |
| Frontend Unit/Security | **PASS** – 7 Unit- und 3 Security-Tests. |
| Frontend Lint/TypeScript | **PASS** – `npm run lint`, `npx tsc --noEmit --noUnusedLocals --noUnusedParameters`. |
| Frontend Build | **PASS** – `npm run build`. |
| Critical E2E | **PASS** – 18 Tests gegen Produktionsbuild, `--retries=0`, feste Testzeit. |
| Reale Fullstack-/Restore-Abnahme | **PASS** – Browserlogin, zwei BFFs, Check-in, verlorene Antwort/Retry mit gleichem Schlüssel, Split, Bestand, Neustart, Zahlung/Freigabe und Logout. Temporäre PostgreSQL-Datenbank per pg_dump/pg_restore wiederhergestellt; exakter Vergleich der ausgewählten Geschäfts- und Flyway-Daten. Restore einschließlich Vergleich: 6 Sekunden, kein Produktions-RPO/RTO-Nachweis. |

Fullstack-Aufruf: `ACCEPTANCE_JAR=/tmp/fasswerk-production-baseline/backend/target/backend-0.0.1-SNAPSHOT.jar scripts/fullstack-acceptance.sh`; Log: `/tmp/fasswerk-production-phase0-fullstack.log`.

Backend-Log dieser lokalen Ausführung: `/tmp/fasswerk-production-phase0-backend.log` (temporär, kein versioniertes CI-Artefakt). Die isolierte Build-Kopie verhindert Konflikte mit dem parallel arbeitenden IDE-Compiler. Vorhandene Tests wurden ausgeführt; neue Fehlerreproduktion gehört in die jeweilige Umsetzungsphase.

Verbleibende Risiken: R01–R12 wie oben priorisiert. Die Bestandsaufnahme ist keine Produktionsfreigabe.

Nächster Schritt: Phase 1 nach Freigabe. Die Roadmap verlangt ausdrücklich: „Danach stoppen und auf Freigabe für PHASE 1 warten.“
