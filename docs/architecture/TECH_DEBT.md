# FassWerk: Technical Debt nach Phase 1

Stand: 2026-09-27, Ausgangscommit `4ebd166`. Inventare und Ablaufe siehe
[BASELINE.md](BASELINE.md). Keine produktiven Konfigurationen, Migrationen oder
Fachservices wurden in Phase 1 umgebaut.

Severity: P0 = Security/Data Loss/Build blocker; P1 = hohe technische Schuld;
P2 = Wartbarkeit; P3 = Nice-to-have. Die Einstufung beschreibt Prioritaet, nicht
in jedem Fall einen reproduzierten Exploit. "Statisch" bedeutet im Code belegt,
"Risiko" erfordert einen weiteren Laufzeit-/Regressionstest. Zeilenangaben beziehen
sich auf den Ausgangsstand, ausgenommen die expliziten Testkorrekturen.

## Offene Befunde

| ID | Severity | Bereich | Problem | Risiko | vorgeschlagene Loesung |
| --- | --- | --- | --- | --- | --- |
| TD-001 | P0 | Konfiguration | Statisch: JWT- und DB-Defaults in `backend/src/main/resources/application.yml:5`, `docker-compose.yml:8` und `backend/pom.xml:27`; keine validierten Production-Properties. | Start mit bekanntem Signaturschluessel/Zugang moeglich; kein fail-fast. | Phase 2: Dev/Test/Prod trennen, erforderliche Werte validieren, fehlendes/ungeeignetes JWT-Secret durch Starttest ablehnen. |
| TD-002 | P0 | Seed-Zugaenge | Statisch: V2 legt aktive ADMIN/STAFF-Benutzer mit festen Hashes an; Seed-Zugaenge in `backend/README.md` dokumentiert. | Frische Instanz kann bekannte privilegierte Konten enthalten. | Phase 2: kontrolliertes Bootstrap-Verfahren; bestehende Konten/Zugaenge bewerten. Bereits ausgefuehrte V2 nicht aendern, notwendige Datenkorrektur nur als neue Migration. |
| TD-003 | P0 | Secrets im Git | `git ls-files` bestaetigt `backend/.env`; Backend-Gitignore schliesst `.env` nicht aus. | Lokale Secrets koennen in Repository/Historie offengelegt werden; reale Gueltigkeit hier nicht verifiziert. | Phase 2: Werte sicher klassifizieren, echte Secrets ggf. rotieren, Tracking bereinigen und Ignore-Regeln ergaenzen; Dummy-Template behalten. Historie separat pruefen. |
| TD-004 | P0 | Historische Bons | Statisch: `MenuService.deleteDrink` / `deleteVariant` rufen `deleteByDrinkVariantId` ohne Bonstatusfilter auf (`MenuService.java:113,211`). | Bei erfolgreicher Transaktion verschwinden auch bezahlte Positionen und daraus berechnete Umsaetze; andere FKs koennen das Loeschen stattdessen verhindern. | Vor Aenderung Regressionstest mit bezahltem Bon; archivfaehige Stammdaten/Soft Delete und historische Positionsdaten erhalten. Phasen 4/8. |
| TD-005 | P0 | Frontend-Abhaengigkeiten | `npm ci` und `npm audit --json` melden 20 betroffene Pakete: 1 critical, 11 high, 5 moderate, 3 low; `next` direkt betroffen. | Potenzielle Sicherheitsluecken; Audit-Schweregrad allein belegt keine Ausnutzbarkeit dieser Anwendung. | Registry-Befunde pro eingesetztem Feature/Deployment pruefen, danach gezieltes Lockfile-Update mit Gates; kein ungeprueftes audit fix --force. |
| TD-006 | P1 | Autorisierung | `SecurityConfig.java:68` faellt auf authenticated zurueck; Reorder-Schreiben, POST manual-day-close/calculate-reorder besitzen keine expliziten Rollen. | STAFF bzw. signierte Tokens ohne erwartete Rolle koennen Verwaltungsoperationen erreichen. Fachlich erlaubte Rollen noch verbindlich festzulegen. | Phase 6: serverseitige Rollenmatrix fuer alle Endpunkte, Tests fuer anonym/falsche/erlaubte Rolle; Fallback restriktiv gestalten. |
| TD-007 | P1 | Refresh-Replay | `AuthService.java:74`: Sessionwiderruf und anschliessende RuntimeException in derselben @Transactional-Methode. Der Auth-Test erwartet weitere Nutzung des rotierten Tokens nach Replay. | Widerruf wird zurueckgerollt; beabsichtigte Replay-Reaktion unwirksam. | Phase 6: Commit-/Fehlergrenze korrekt gestalten; Replay-Test muss Widerruf der Tokenfamilie/Session nachweisen. |
| TD-008 | P1 | Concurrency | Keine @Version/@Lock-Absicherung bei Reservierungen, Bons, Inventar oder Refresh-Token-Rotation; offener Bon pro Tisch ohne Unique-Gate. | Risiko verlorener Updates, Doppeleroeffnung, Ueberbuchung und mehrfacher Rotation; noch nicht konkurrierend reproduziert. | Phase 3/7/8: PostgreSQL-Concurrency-Tests, gezielte atomare Updates/Sperren/Constraints und Idempotenz; neue Migrationen. |
| TD-009 | P1 | Testdatenbank | `backend/src/test/resources/application-test.yml:3`: H2/create-drop, Flyway aus. | PostgreSQL-Dialekt, Constraints, Migrationen und Locks ungeprueft. | Phase 3: DB-relevante Tests auf PostgreSQL/Testcontainers, Flyway auf leerer DB; Unit-Tests isoliert lassen. |
| TD-010 | P1 | CI-Gate | `.github/workflows/ci.yml:25` baut mit skipTests; kein backend verify, Frontend lint/build oder Release-Abhaengigkeitsgate. OpenAPI-Job definiert keine PostgreSQL-Serviceinstanz. | Fehlerhafte Builds koennen als Image erzeugt werden; OpenAPI-Export in frischer CI voraussichtlich ohne DB blockiert (hier nicht auf GitHub ausgefuehrt). | Phase 11/12: notwendige Services und verify/lint/build/E2E vor Imagebau/Publish, Reports und Scans anbinden. |
| TD-011 | P1 | Migrationen | `MIGRATION_CONSOLIDATION.md` beschreibt nachtraegliche Konsolidierung von V1-V9; V5/V7/V8 duplizieren Schema, V10-V16 fehlen im heutigen Satz. | Upgrade bestehender Datenbanken kann an Historie/Checksummen scheitern; Versionsluecken allein sind kein Fehler. | Historien und Checksummen realer Instanzen vergleichen, Upgrade-Test ergaenzen; keine alten Migrationen nachtraeglich editieren/entfernen. |
| TD-012 | P1 | Reservation | `ReservationService.java:171`: Kapazitaet nur ueber Anzahl am identischen Startzeitpunkt; guestCount ungenutzt, keine Intervallpruefung; Oeffnungsfenster ohne Nachtuebergang. | Kapazitaet/Slots entsprechen moeglicherweise nicht realer Belegung; Parallelitaet ungeprueft. | Phase 7: Invarianten fuer Dauer, Personen, Tischzuordnung, Nachtfenster und parallele Buchung festlegen und testen. |
| TD-013 | P1 | Zeitmodell | Reservation/Billing/Reports nutzen Server-LocalDate(Time), Inventar konfiguriertes Business Date; No-show-Job betrachtet nur heute. | Nachtbetrieb, Tageswechsel und verpasste Jobs liefern widerspruechliche Zuordnungen. | Phase 7/8: explizite Clock/Zone/Business-Date-Grenzen; Tageswechsel-, DST- und Nachholtests. |
| TD-014 | P1 | Verkaufszaehlung | `InventoryService.java:266` uebergibt bei jeder Bestellung BigDecimal.ONE; Restock storniert Tagesaggregate nicht. | Mehrfachmengen werden unterzaehlt, Stornos ueberzaehlt; Nachbestellbasis falsch. | Phase 8: Mengen und Gegenbuchungen explizit uebergeben, Regression fuer Menge > 1 und Einzelstorno. |
| TD-015 | P1 | Wochenaggregation | `DrinkSalesTrackingService.aggregateToWeekly` addiert bereits aggregierte Tagesdaten erneut; keine Verarbeitungsmarkierung. | Wiederholung/mehrere Instanzen verdoppeln Verbrauch. | Phase 8: idempotentes Neuberechnen/Upsert oder atomare Verarbeitungsschluessel; Wiederholungstest. |
| TD-016 | P1 | Nachbestellformel | `ReorderCalculationService.java:55`: Tagesdurchschnitt wird Wochenverbrauch genannt/verwendet; ml-Verbrauch mit Artikelbestand auch in Litern verrechnet. | Systematisch falsche Liefermengen/Reichweiten. | Phase 8: Zeitraum und Einheit vereinheitlichen, Beispielrechnungen und Liter/ml-Tests; Mindestbestand explizit beruecksichtigen. |
| TD-017 | P1 | Transaktionsfehler | InventoryService und ReorderCalculationService fangen Exception um transaktionale Tracking-/Berechnungsaufrufe. | Gefangene Fehler koennen Transaktion dennoch rollback-only setzen oder Tracking still unvollstaendig lassen. | Fehler vor Aenderung reproduzieren; bewusste atomare Verarbeitung oder dauerhafte Nachverarbeitung definieren, Rollback-Tests. |
| TD-018 | P1 | Session-Refresh | `frontend/lib/server-auth.ts:118` erneuert nur bei 401; Backend-Test fuer widerrufenes Access-Token erwartet 403. Keine Refresh-Koordination paralleler BFF-Requests. | Abgelaufene Sessions werden nicht verlaesslich erneuert; parallele Requests koennen Rotation/Replay ausloesen. | Phase 6: konsistente Auth-Statuscodes und atomare Rotation; abgelaufenes Token und parallele Requests ueber echten BFF pruefen. |
| TD-019 | P1 | API/LAZY | InventoryController mappt Entities ausserhalb konsistenter Service-Transaktionen; ReorderOrderService-Lesemethoden dereferenzieren LAZY-Relationen ohne @Transactional bei OSIV=false. | Risiko von LazyInitializationException/500 in nicht getesteten Lesepfaden. | Phase 5: DTO-Mapping innerhalb Use-Case-Transaktion oder gezielter Projektion; HTTP-Integrationstests. |
| TD-020 | P1 | Konfiguration | `SalesConfigurationService.updateConfiguration` ignoriert Lookback/Sicherheitsfaktor/Lieferzeit aus dem Request. | UI bestaetigt Speicherung, Werte bleiben Defaults. | Phase 8/9: fachliche Persistenz definieren, erforderliche neue Migration und Read-after-write-Test. |
| TD-021 | P1 | API-Platzhalter | `InventoryController.java:231` liefert bei GET consumption-metadata null; 501 nur dokumentiert. calculateReorder mappt moegliches null ebenfalls ungeschuetzt. | Leere Erfolgsmeldung bzw. 500; UI kann gespeicherte Metadaten nicht wieder laden. | Phase 5: echten Lese-Use-Case und definiertes Nicht-vorhanden-Verhalten implementieren; Contract/API-Tests. |
| TD-022 | P1 | API-Vertrag | `frontend/types/api.ts` manuell; Browsermocks eigene Wahrheit; kein Contract-Gate und kein versioniertes OpenAPI-Artefakt. | Gruene Mocks erkennen Backend-/DTO-/Statusabweichungen nicht. | Phase 10: laufende OpenAPI-Ausgabe pruefen und Generation oder Contract-Tests reproduzierbar integrieren; Springdoc/Boot-Laufzeit separat verifizieren. |
| TD-023 | P1 | Testluecken | Keine serverseitigen Reservation-Lifecycle-, Split-Payment-, Idempotenz-, Rollback- und Concurrency-Tests; Rollenmatrix nur kleiner Ausschnitt. | Kritische Invarianten sind trotz gruener 16 Tests nicht abgesichert. | Vor Strukturumbau gezielte Tests nach Phasen 3, 6, 7 und 8; realistisches Coverage-Gate erst danach. |
| TD-024 | P1 | Docker/Betrieb | Backend ohne Healthcheck, Frontend nur service_started; App-Images latest, Basisimages mutable; Backend-Dockerfile skipTests. | Start-Races und nicht reproduzierbare Releases/Rollbacks. | Phase 11: bewusster Healthcheck, service_healthy, immutable Tags/Digests und CI-Kopplung. |
| TD-025 | P1 | Logging | AuthService loggt E-Mail/Token-ID, ReservationMailService Empfaenger/Fehlertext. Im lokalen DEBUG-Testlauf erscheinen Login-DTOs inkl. Testpasswort. GlobalExceptionHandler loggt unerwartete Fehler nicht. | PII-/Credential-Leak bei Debugbetrieb und fehlende Diagnose bei generischen 500. | Phase 6/13: DTO-/Log-Redaktion, sichere Log-Level, Correlation-ID und sanitisiertes Fehlerlogging; keine sensiblen Payloads. |
| TD-026 | P2 | Architektur | Fremd-Repositoryzugriffe in Billing/Menu/Inventory/Reservation/Report/Shift; grosse Services und InventoryController mit Mehrfachorchestrierung. | Wechselseitige Kopplung und schwer isolierbare Regeln. | Phase 4/5: Domain Map, gezielte Use-Case-Grenzen und lokale Refactorings nach Tests; kein Big Bang. |
| TD-027 | P2 | Frontend | Clients mit 674-1338 Zeilen, duplizierte Fetch-/Formatierungs-/Verfuegbarkeitslogik, Typassertions ohne Laufzeitpruefung; keine zentralen Error Boundaries. | Fehlerbehandlung/Regeln driften; isolierte Tests schwierig. | Phase 9: klar geschnittene Feature-Hooks/Logik, Error Boundaries, gezielte schnelle Tests fuer nichttriviale reine Regeln. |
| TD-028 | P2 | Fehlervertrag | GlobalExceptionHandler catch-all wandelt z.B. nicht gesondert behandelte Eingabe-/Persistenzfehler in generische 500; Next-Adapter verschlucken JSON-Fehler. | Inkonsistente Statuscodes und schwer diagnostizierbare UI-Fehler. | Phase 5/10: bestehendes API-Format kompatibel schaerfen, Fehlerklassen/Statuscodes und Invalid-Input-Faelle testen. |
| TD-029 | P2 | QR/Mail | QR-URL ist Browser-GET, Scan-API POST; Compose-URL zeigt auf Frontend ohne Scan-Route. Mail innerhalb DB-Transaktion, Versandfehler verschluckt, Kulanz im Text fest 15 Minuten. | QR-Lifecycle unvollstaendig, Mailzustand kann vom DB-Zustand abweichen. | Phase 7: echten Scanweg/Autorisierung und Mail-Nachverarbeitung definieren, konfigurationsabhaengige Texte und Integrationstest. |
| TD-030 | P2 | PDF | `ReorderReportService` bricht bei y < 50 ab und nutzt Standardfont ohne Mehrseiten-/Unicodekonzept. | Lange Listen unvollstaendig, einzelne Zeichen koennen Export scheitern lassen. | PDF-Grenztests fuer lange Listen/Zeichen, gezielte Pagination/geeignete Schrift. |
| TD-031 | P2 | Bestandsdaten | packagesInStock wird bei Abbuchung nicht synchron zum Restbestand gepflegt; updateItem berechnet Restbestand erneut aus Gebinden. RECEIVED bei Lieferauftrag bucht keinen Bestand. | Versehentliches Ueberschreiben von Restmengen; Lieferstatus und Bestand divergieren. | Fachliche Update-/Wareneingangssemantik in Phase 8 klaeren und durch Tests absichern. |
| TD-032 | P2 | Konfiguration/Buildwarnungen | Redundante application.properties; ungenutzte MapStruct-Prozessoroption, deprecated OpenApiConfig-API, unchecked Testoperationen, H2Dialect-Warnung. | Uneindeutige Konfiguration, Warnungsrauschen; MapStruct-Konfiguration ohne belegte Nutzung. | Phase 2 bzw. 14 nach Referenzpruefung bereinigen; Warnungen nicht deaktivieren. |
| TD-033 | P2 | Frontend-Lint | Fuenf no-unused-vars-Warnungen in inventory-client.tsx und reorder-dashboard.tsx. Bereits vorhandenes skipLibCheck=true. | Toter/halb angeschlossener UI-Code und begrenzte Library-Typpruefung. | Phase 9/14 Nutzung klaeren und gezielt bereinigen; keine neuen Ignore-Regeln. |
| TD-034 | P2 | Monitoring | Prometheus ist exponiert konfiguriert, Registry-Dependency fehlt; Request-ID ungeprueft aus Header uebernommen, strukturierte Logausgabe nicht konfiguriert. | Betriebsdokumentation/Monitoring erwartet moeglicherweise nicht vorhandene Daten. | Phase 13: benoetigte Endpunkte/Metriken real pruefen, Registry bewusst einbinden, ID begrenzen und Logkorrelation verifizieren. |
| TD-035 | P2 | E2E-Reproduzierbarkeit | Next-Dev-Server, reuseExistingServer=true, lokal vier Worker, feste Sleeps in Tab-Test; Testreports versioniert. E2E_FIXED_DATE aendert Testdaten, nicht Browserzeit. | Port-/Last-/Datumsabhaengigkeit und Artefaktrauschen; erste Ausfuehrung hatte Timeouts. | Phase 1 Fehler isolieren; Phase 12 dedizierten Server/Lifecycle und stabile Uhr/Assertions festlegen, Reports als Artefakte statt Quellcode. |
| TD-036 | P2 | Token-Lebensdauer | Session-/Logout-all-Widerruf erfasst nicht alle bereits ausgestellten Access-Tokens; Rollen/Aktivitaet werden im Filter aus JWT statt aktuellem Benutzer gelesen. | Rechte-/Kontosperren greifen erst nach Tokenablauf, ausser gezielter Tokenwiderruf. | Phase 6: gewuenschte Sperrsemantik definieren, Tokenversion/Sessionbindung oder kurze Access-Laufzeit und Integrationstests. |

## In Phase 1 reproduziert und korrigiert

| ID | Severity | Bereich | Problem | Risiko | vorgeschlagene Loesung |
| --- | --- | --- | --- | --- | --- |
| TD-037 | P0 | Backend-Gate | Erster mvnw test: 16 Tests, 1 Failure. InventoryServiceUnitConversionTest verlangte physisches Loeschen, obwohl Code/V20 Soft Delete dokumentieren. | Rotes Gate; bisheriger Test prueft falschen Vertrag. | Umgesetzt: Test prueft Deaktivierung, beide Entknuepfungen sowie Erhalt von Bestand/Bewegungen; keine Produktionslogik geaendert. Abschliessende Testresultate in BASELINE.md. |
| TD-038 | P0 | Browser-Gate | Vier Kategorie-Tests scheiterten durch mehrdeutige Meldungsselektoren (Toast plus Seite/Dialog). | Playwright strict-mode violation trotz angezeigter korrekter Meldung. | Umgesetzt: Erfolg auf main, Fehler auf category-edit-modal eingrenzen; keine Assertions entfernt, kein Test deaktiviert. Abschliessende Resultate in BASELINE.md. |

## Grenzen dieser Bewertung

Kein Exploit gegen reale Konten/Datenbanken, kein produktiver Lasttest und keine
Pruefung einer laufenden Produktionskonfiguration. Npm-Audit ist ein Registry-Befund
zum Analysezeitpunkt; nicht jede transitive oder plattformspezifische Meldung ist
in diesem Deployment erreichbar. Windows-spezifische Next-Meldungen sind zum Beispiel
kein Nachweis einer Linux-RCE. Eine Java-/Container-Schwachstellenpruefung fehlt noch.

Die 13 bestehenden SQL-Migrationen bleiben unveraendert. Kein Secret wurde generiert,
kein Zugangswert in diese Dokumente kopiert und keine Dependency aktualisiert.
Die naechste Phase bleibt an das Baseline-Gate und die ausdrueckliche Freigabe gebunden.

## Fortschritt nach Phase 2 (2026-09-27)

Die Baseline-Tabelle oben bleibt als historischer Ausgangsbefund erhalten.
Details: [PHASE_2_REPORT.md](PHASE_2_REPORT.md) und
[Konfiguration](../configuration.md).

| ID | Status nach Phase 2 |
| --- | --- |
| TD-001 | Behoben fuer Standard-/prod-Start: keine DB-/JWT-Defaults, fruehe Validierung, Compose-Pflichtwerte, Maven-Flyway ohne Credential-Defaults. Lokale Defaults ausschliesslich bei explizitem dev. |
| TD-002 | V21 deaktiviert bei Anwendung der Migration unveraenderte bekannte Seed-Zugaenge, entfernt ihre Refresh-Sessions und ersetzt die Hashes. Expliziter Erst-Admin-Bootstrap ergaenzt. Bestehende Installationen wurden in dieser Sitzung nicht migriert. |
| TD-003 | backend/.env aus dem Git-Index entfernt, lokal erhalten und ignoriert; nur bekannte Entwicklungsdefaults in dieser Datei bestaetigt. Neue Templates enthalten keine privaten Secrets. Historie bleibt bestehen; ausserhalb Dev verwendete Werte muss der Betreiber rotieren. |
| TD-032 | Redundante application.properties entfernt; uebrige Buildwarnungen bleiben offen. |
| TD-009 / TD-023 | Ein isolierter PostgreSQL-16-Smoke-Test hat alle 14 Migrationen und den Admin-Login verifiziert. Die dauerhafte DB-Teststrategie/H2-Umstellung und Concurrency-Abdeckung sind weiterhin Phase 3. |
| TD-035 | Wiederkehrender lokaler Parallel-Timeout reproduziert; Playwright lokal an vorhandene CI-Einzel-Worker-Konfiguration angepasst, Kontrolllauf gruen. Weiter offen: echter E2E-Backendvertrag, Server-/Uhr-Isolation, feste Sleeps und versionierte Artefakte. |
| TD-005 / TD-033 | Unveraendert: npm meldet 20 betroffene Pakete; Frontend-Lint hat fuenf Warnungen. |

Keine weiteren Baseline-Befunde werden durch die Konfigurationsphase als erledigt
betrachtet. Insbesondere bleiben Replay-/Access-Token-Sperrsemantik, API-Rollen,
Billing-Datenverlust und fachliche Concurrency-Themen offen.

## Fortschritt nach Phase 3 (2026-09-27)

Details: [PHASE_3_REPORT.md](PHASE_3_REPORT.md) und
[PostgreSQL-Teststrategie](../testing.md). Die urspruengliche Tabelle bleibt als
historische Baseline erhalten.

| ID | Status nach Phase 3 |
| --- | --- |
| TD-009 | Behoben: H2 entfernt, PostgreSQL-16-Testcontainers fuer saemtliche DB-Integrationstests; Flyway aktiv, Hibernate validate statt create-drop. Unit-Tests bleiben isoliert. |
| TD-008 | Nicht behoben, jetzt deterministisch an echten Services reproduziert: zwei Reservierungen bei einem Tisch, zwei offene Bons, Teilzahlung erzeugt 4 bezahlte plus 1 offene aus 3 Positionseinheiten, Bestand 4 trotz zweimal -6 von anfangs 10, zwei aktive Refresh-Nachfolger. Fuenf ausdrueckliche Charakterisierungstests, keine Sicherheitsgarantie. Korrektur in Phasen 6/7/8. |
| TD-011 | Frischinstallation aller 14 Migrationen, wiederholtes migrate ohne Aenderung, V17-Legacy-bytea-Upgrades und V20->V21 nachgewiesen. Fremde historische Checksummen/konsolidierte Deployments weiterhin ungeprueft. |
| TD-023 | DB-/Constraint-/Repository-/Transaktionsabdeckung erweitert. Kein vollstaendiger Domain-, Last- oder echter Frontend-Backend-E2E-Nachweis. |
| TD-010 | Unveraendert: lokale PostgreSQL-Gates vorhanden, CI fuehrt noch kein Backend-verify aus. |
| TD-005 / TD-033 | Unveraendert: 20 npm-Audit-Befunde, fuenf Frontend-Lint-Warnungen. |

Zusaetzlicher PostgreSQL-Befund: Die Unique Keys der taeglichen/woechentlichen
Verkaufsaggregate erlauben mehrere Zeilen mit NULL drink_variant_id. Nicht-NULL-
Varianten sind eindeutig. In Phase 8 gewuenschte Aggregatidentitaet festlegen und
gegebenenfalls vorhandene Duplikate vor einem neuen Constraint migrieren.

Die neuen knownGap-Tests fixieren ausschliesslich den Ist-Befund. Bei Behebung der
Invarianten muessen sie auf Soll-Verhalten umgestellt werden; ihr gruener Status
darf weder als erledigtes TD-008 noch als Freigabe konkurrierender Zahlungen gelten.

Neu reproduziert und korrigiert: `TableOrderRepository.searchArchive` scheiterte
unter PostgreSQL bei NULL-Suchtext mit `function lower(bytea) does not exist`.
Explizite String-Casts fuer den Suchparameter in beiden Archivabfragen korrigieren
die Typisierung; vier echte Repository-Testfaelle pruefen NULL, Gross-/Kleinschreibung,
fehlenden Treffer und Zahlungsfilter. Kein Schema- oder API-Wechsel erforderlich.


## CI-OpenAPI-Korrektur nach Phase 3 (2026-09-27)

TD-010 teilweise behoben: Der Job backend-openapi fuehrt nun clean verify aus und
startet eine separate PostgreSQL-16-Exportdatenbank mit zufaelligen DB-/JWT-Werten.
Der fruehere Export konnte ohne DB und Pflichtvariablen nicht starten; die Schleife
wartete trotzdem rund 40 Sekunden. Zusaetzlich reproduziert: Health 503 wegen des
fuer den Schemaexport nicht benoetigten SMTP-Servers sowie HTTP 403 fuer die bislang
nicht oeffentlich freigegebene YAML-Adresse. SMTP-Health wird nur im CI-Export
ausgenommen; DB-Health und Produktionsvalidierung bleiben aktiv. JSON/YAML werden
durch zwei neue echte HTTP-Tests abgesichert.

Exportfehler zeigen jetzt das Backend-Log, CI laedt es als Fehlerartefakt hoch.
Prozess-/Container-Cleanup ist auf eigene Ressourcen begrenzt; Exporte werden erst
nach erfolgreichem Download und YAML-Kopfpruefung atomar veroeffentlicht.
Kein erfolgreicher GitHub-Lauf behauptet: Die Korrektur wird lokal mit echtem JAR,
PostgreSQL und prod-Profil geprueft. Vollstaendige Frontend-/Release-/Image-Gates
bleiben offen. Details und Pruefergebnisse: [CI_OPENAPI_FIX.md](CI_OPENAPI_FIX.md).

## Fortschritt nach Phase 4 (2026-09-27)

Die [Domain Map](DOMAIN_MAP.md) ordnet Entities/Aggregate, Value-Object-Luecken,
Use Cases, Repositories, REST-Adapter, Eventkandidaten und Abhaengigkeiten acht
fachlichen Kontexten zu. Administration ist eine Rollen-/UI-Sicht, keine neue
Sammeldomaene; Ordering und Billing bleiben vorerst gemeinsam. Catalog und Shift
Settlement sind eigenstaendige Verantwortungsbereiche.

| ID | Status nach Phase 4 |
| --- | --- |
| TD-026 | Teilweise behoben: Report und Shift greifen nicht mehr direkt auf Billing-Entities/-Repositories zu. BillingRevenueQueries/PaidOrderRevenue bilden eine lesende Modulgrenze; BillingRevenueQueryService besitzt die Persistenzselektion. Zwei gezielte Architekturtests sichern die Grenze ab. Uebrige Fremd-Repositoryzugriffe und Schreibzyklen bleiben offen. |
| TD-023 | Vier neue PostgreSQL-Integrationstests pruefen Umsatz-/Verbrauchsprojektionen, gespeicherte/leere Schichten, Zahlungs-/Statusfilter und Intervallgrenzen. Vor dem Umbau wurden bestehende Konsumenten durch Regressionstests charakterisiert. |
| TD-013 | Nicht behoben: Summen nutzen [start,end), die bestehende Diagrammauswahl [start,end]. Die neue Schnittstelle benennt die inklusive Legacy-Grenze explizit; unterschiedliche Tages-/Business-Date-Modelle und N+1-Abfragen bleiben fachlich zu bereinigen. |
| TD-004 / TD-008 | Unveraendert offen: Catalog-Loeschen kann bezahlte Positionen betreffen; fuenf reproduzierte Konkurrenzfehler werden durch die neue Lesegrenze nicht behoben. |

Keine Paket-Gesamtverschiebung, neue Dependency, neue Flyway-Migration, Event-Bus-
Einfuehrung oder REST-Vertragsaenderung. Details und Gate-Ergebnisse:
[PHASE_4_REPORT.md](PHASE_4_REPORT.md).

## Fortschritt nach Phase 5 (2026-09-27)

| ID | Status nach Phase 5 |
| --- | --- |
| TD-019 | Behoben fuer die identifizierten HTTP-Pfade: Inventory-DTOs werden innerhalb InventoryInsightsApplicationService-Transaktionen abgebildet; drei ReorderOrderService-Listen besitzen read-only-Transaktionen. Echte HTTP-Tests mit PostgreSQL und OSIV=false reproduzierten zuvor 500 und pruefen jetzt geladene Relationsdaten. N+1-Optimierung bleibt offen. |
| TD-021 | Behoben: Verbrauchsmetadaten werden gelesen und nach Update wieder ausgegeben; fehlende Metadaten bleiben leeres 200, fehlender Artikel 404. Berechnung ohne Variante ist null-sicher. |
| TD-028 | MVC-seitig verbessert: konsistentes bestehendes JSON-Envelope, korrekte 400/404/405/415 und generischer Constraint-Konflikt 409; keine Parser-/SQLdetails. Security-Filter und Next-Adapter bleiben Phasen 6/10. Keine stille RFC-9457-Migration. |
| TD-026 | InventoryController-Orchestrierung/Entity-Mapping in Inventory-Use-Case-Fassade verlagert. Alle neun Controller geprueft; keine direkten Repository-/Transaktionsaufrufe oder Entity-Responses. Uebrige Servicekopplungen bleiben offen. |
| TD-023 | Neue HTTP-Contracttests fuer Fehler, Lazy-Mapping, Metadaten und Nullpfade sowie isolierte Request-ID-Filtertests. Domaininvarianten/Rollenmatrix sind damit nicht vollstaendig abgedeckt. |
| TD-034 | Request-ID-Zeichensatz und Laenge begrenzt; MDC-Cleanup und Header/Attribut-Korrelation getestet. Monitoring/strukturierte Logs bleiben offen. |
| TD-006 / TD-007 / TD-008 / TD-020 / TD-025 | Nicht behoben: Rollenluecken, Refresh-Replay/Parallelitaet, ignorierte numerische Konfigurationsfelder und sensible DEBUG-Logs/fehlendes Serverfehlerlogging. JWT-Parserfehler werden jetzt korrekt als Eingabefehler behandelt, nicht als Loesung der Token-Policy behauptet. |

Details: [API_LAYER_REVIEW.md](API_LAYER_REVIEW.md),
[PHASE_5_REPORT.md](PHASE_5_REPORT.md). Keine neuen Dependencies oder Migrationen.

## Fortschritt nach Phase 6 (2026-09-27)

| ID | Status nach Phase 6 |
| --- | --- |
| TD-006 | Explizite serverseitige Rollenmatrix und deny-all-Fallback. Nutzerentscheidung umgesetzt: neue Rolle BARCHEF neben ADMIN fuer Lieferanten-/Nachbestellverwaltung, Berechnung und manuellen Tagesabschluss. Dynamischer HTTP-Test prueft die registrierten Fach-Endpunkte fuer anonym, unbekannte Rolle, STAFF, BARCHEF und ADMIN. |
| TD-007 | Replay-Widerruf wird durch dedizierte Auth-Exception mit enger noRollbackFor-Regel committed; Nachfolgetoken nach Replay unbrauchbar. Andere Persistenzfehler rollen Rotation weiterhin zurueck. |
| TD-008 | Refresh-Konkurrenzdefekt behoben: accountbezogener PostgreSQL-Lock, ein Nachfolger, Replay widerruft ihn. Der fruehere knownGap-Test ist ein Soll-Regressionstest. Die vier anderen Konkurrenzdefekte bleiben offen. |
| TD-018 | Backend liefert jetzt 401 bei ungueltiger Authentifizierung; BFF reagiert darauf und koordiniert Refresh pro Prozess. Mehrere Instanzen/verspaetete Requests/Logout-Rennen bleiben offen; keine clusterweite Garantie. |
| TD-025 | Secret-/Session-DTO-toString redigiert, Replay ohne E-Mail/JTI und Mailfehler ohne Empfaenger/Exceptiontext. Beliebige DEBUG-/TRACE-/Bind-/Proxy-Logs und fehlende sichere Serverfehlerdiagnose bleiben Betriebsrisiken. |
| TD-023 | JWT-Negativfaelle, Rollen inkl. BARCHEF, aktives Konto/Rollenwechsel, Sessionownership, Replay/Rollback, Actuator/CORS, BFF-CSRF/Header und neue Rollen-Migration abgedeckt. |
| TD-034 | Request-ID laeuft jetzt vor Security und steht auch in 401/403/503 zur Verfuegung. Prometheus-Registry/strukturierte Logs bleiben offen. |

Neue explizite Restbefunde aus dem Security Review:

| ID | Severity | Bereich | Problem | Risiko | Vorgeschlagene Loesung |
| --- | --- | --- | --- | --- | --- |
| TD-035 | P1 | Auth-Abuse | Kein instanzuebergreifender Rate Limiter fuer Login/Refresh; frueher Unknown-User-Pfad zeigt Timingunterschied. | Credential Stuffing, Ressourcenverbrauch und Kontenaufklaerung. | Vor oeffentlichem Betrieb IP-/Account-Limits, progressive Verzoegerung und Monitoring am vertrauenswuerdigen Gateway bzw. gemeinsamen Store; keine fremd ausloesbare permanente Kontosperre. |
| TD-036 | P1 | Access-Revocation | Andere bereits ausgegebene Access-JTIs sind nicht an die widerrufene Session gebunden. | Session-/Logout-all-/Replay-Widerruf beendet Refresh, aber nicht sofort jedes Access-Token (Default 120 Minuten). | Sessionbindung/Tokenversionierung und Tests fuer sofortige Gesamtwiderrufe; Policy fuer Lebenszeiten festlegen. |
| TD-037 | P1 | BFF-Concurrency | Refresh-Coalescing nur pro Prozess, 3 Sekunden, maximal 256 Eintraege. | Cluster-/verspaetete Requests und Logout/Refresh-Rennen koennen erneute Anmeldung erfordern. | Vor horizontaler Skalierung gemeinsame Sessionkoordination; keine Lockerung der Backend-Replay-Pruefung. |

BFF-Herkunftspruefung und Sicherheitsheader wurden ergaenzt; APP_ORIGIN muss hinter
Reverse Proxys zur oeffentlichen HTTPS-Origin passen. Alte Migrationen unveraendert;
nur V22 neu. Keine produktiven Benutzerzuweisungen vorgenommen.
Details: [SECURITY_REVIEW.md](SECURITY_REVIEW.md), [PHASE_6_REPORT.md](PHASE_6_REPORT.md).

## Phase 7 Follow-up

- Reservation concurrency, whole-stay overlap, missed no-shows and terminal-state
  mutation gaps are addressed by centralized rules and PostgreSQL-backed tests.
- Operations prerequisite: seats must be configured; future legacy reservations
  need explicit revalidation. V23 deliberately does not guess capacity or time zone.
- Reservation allocation uses a coarse per-database advisory lock and loads active
  reservations. Future multi-venue/high-throughput support needs scoped locking and
  bounded interval queries.
- Same-area table grouping does not establish physical adjacency.
- Reservation/billing/walk-in occupancy integration remains Phase 8 work.
- Decision mail is after-commit best effort; durable outbox/retries remain open.
- Previously printed backend-origin QR URLs may require reverse-proxy redirects.
- Existing dependency vulnerabilities and five unrelated frontend lint warnings
  are not resolved by this reservation phase.

## Payment-bound Occupancy Follow-up

Supersedes the Phase 7 reservation/billing/walk-in integration gap: check-in now
opens bills and full payment releases each table atomically. Duplicate-open and
parallel split-payment characterization tests now assert correctness. The remaining
stock-adjustment lost-update characterization is still Phase 8 work.
V24 rejects historical duplicate OPEN bills without deleting data. Legacy future
turnover bookings and checked-in groups lacking linked bills require operator review.
A conservative one-unresolved-booking-per-business-date policy replaces guessed
stay lengths. Global serialization now includes all bill mutations; scalability
and independent inventory adjustment concurrency remain explicit limitations.


## Archived Deckel Clarification

Explicit unpaid archiving ends physical occupancy, not debt. TableVisitEnded
replaces the payment-only event, and reservation COMPLETED denotes an ended visit.
Only OPEN bills block new groups. Older OCCUPIED flags from the previous archive
behavior need explicit reconciliation, not an automatic rewrite of financial data.

## Fortschritt nach Phase 8 (2026-09-28)

| ID | Status nach Phase 8 |
| --- | --- |
| TD-004 | Behoben: Drink-/Varianten-Loeschung ist Soft-Delete; bezahlte Positionen und Umsatz bleiben erhalten. |
| TD-008 | Billing/Reservation und manuelle Bestandsmutationen sind serialisiert; offene Bons besitzen DB-Unique-Gate, Add/Split/Korrektur Idempotenz. Globaler Lock bleibt Skalierungsrisiko. |
| TD-014 | Behoben fuer den aktiven Geschaeftstag: echte Mehrfachmenge und Volumen werden gebucht, Einzelstorno gegengebucht. Cross-Day-Zuordnung braucht kuenftig ein unveraenderliches Sales-Ledger. |
| TD-015 | Behoben: Wochenwerte werden aus der gesamten Woche neu berechnet; Scheduler-Wiederholung verdoppelt nichts. |
| TD-016 | Behoben: Tages-ml werden in Wochenverbrauch der Artikel-Lagereinheit umgerechnet; Lead Time, Safety Stock, Mindestbestand und Reichweite verwenden dieselbe Einheit. |
| TD-017 | Behoben im Bon-/Bestandspfad: Tracking-/Reorderfehler werden nicht verschluckt; ein Integrationstest beweist vollstaendigen Rollback. Scheduler verarbeitet Artikel weiterhin isoliert und protokolliert Einzelfehler. |
| TD-023 | Phase-8-Matrix mit Rundung, Split, Retry, Nullbestand, Unterbestand, Rollback, Concurrency, Migration und Historienerhalt ergaenzt. |
| TD-031 | Teilweise behoben: Paketbestand wird bei Abbuchung, Rueckbuchung und Korrektur synchronisiert. Wareneingangsbuchung bleibt separat offen. |

Details: [BILLING_INVENTORY_RULES.md](BILLING_INVENTORY_RULES.md),
[PHASE_8_REPORT.md](PHASE_8_REPORT.md). TD-020, Cross-Day-Sales-Attribution,
Legacy-NULL-Aggregate, globale Lock-Skalierung und Wareneingangssemantik bleiben offen.

## Fortschritt nach Phase 9 (2026-09-28)

| ID | Status nach Phase 9 |
| --- | --- |
| TD-027 | Teilweise behoben: Fachberechnungen und Laufzeitparser fuer Reservation, Billing, Inventory und Catalog liegen in Feature-Modellen; zentrale API-Fehlerauswertung sowie App-Router Error-/Loading-Boundaries sind vorhanden. Grosse Screen-Composers bleiben fuer eine spaetere panelweise Zerlegung; Assertions in sekundaeren Bereichen bleiben bis zur Phase-10-Vertragsentscheidung offen. |
| TD-033 | Behoben: tote Inventory-/Reorder-Symbole entfernt; ESLint meldet 0 Fehler und 0 Warnungen, ohne neue Ignore-Regeln. `skipLibCheck=true` bleibt separat bestehen. |
| TD-020 | Unveraendert: ignorierte numerische Sales-Konfigurationsfelder sind ein Backend-/API-Vertragsproblem, nicht durch UI-Umschichtung behoben. |
| TD-005 | Unveraendert: kein `npm audit fix --force`; die in Phase 8 dokumentierten Abhaengigkeitsbefunde muessen gezielt bewertet werden. |

Details: [FRONTEND_ARCHITECTURE.md](FRONTEND_ARCHITECTURE.md) und
[PHASE_9_REPORT.md](PHASE_9_REPORT.md). Sieben schnelle Fachmodelltests und die
bestehenden drei Security-Tests ergaenzen Build, TypeScript, Lint und 18 kritische
Browserfaelle.


## Fortschritt nach Phase 10 (2026-09-28)

| ID | Status nach Phase 10 |
| --- | --- |
| TD-022 | Behoben: Laufender prod-Profil-Export gegen PostgreSQL erzeugt die versionierte kanonische OpenAPI-Datei; lockfile-gepinnte Generierung ersetzt die manuell gepflegten Frontend-DTO-Felder. CI lehnt veralteten OpenAPI-Snapshot und veraltete TypeScript-Typen ab. |
| TD-028 | Transportvertrag verbessert: Request-/Response-Schemas dokumentieren Required/Nullable explizit; ein HTTP-Contracttest prueft Struktur und zentrale Zahlen-/Nullvertraege. Die bereits dokumentierte semantische Sales-Konfigurationsluecke TD-020 bleibt bewusst offen. |
| TD-027 | Compile-time DTO-Doppelpflege beseitigt. Die Phase-9-Laufzeitparser bleiben Sicherheitsgrenzen; ungepruefte JSON-Assertions in sekundaeren Bereichen sind weiterhin Runtime-Hardening, aber keine zweite Typwahrheit. |
| TD-005 | Unveraendert: Der Generator ist lockfile-gepinnt; bestehende npm-Audit-Befunde wurden nicht mit `--force` veraendert und muessen separat bewertet werden. |

Details: [API_CONTRACT.md](API_CONTRACT.md) und
[PHASE_10_REPORT.md](PHASE_10_REPORT.md).

## Fortschritt nach Phase 11 (2026-09-28)

| ID | Status nach Phase 11 |
| --- | --- |
| TD-024 | App-Healthchecks und gesunde Startreihenfolge, Digest-gepinnte Basisimages, explizite Release-Tags, Nicht-Root, Read-only-Root, tmpfs, Capability-Drop und Graceful Shutdown umgesetzt und im isolierten Compose-Smoke-Test geprueft. Registry-Tag-Unveraenderlichkeit bleibt Betriebsaufgabe. |
| TD-010 | Imagebau ist an Backend-/OpenAPI- und Frontend-/E2E-Gates gekoppelt; Container-Smoke-Test ergaenzt. Kein automatisches Publish, kein vollstaendiges Release-/Vulnerability-Gate; GitHub-Ausfuehrung lokal nicht nachgewiesen. |
| TD-005 | Bestehende Abhaengigkeitsbefunde bleiben offen; Digest-Pinning ersetzt keine Sicherheitsupdates. |

Details: [PHASE_11_REPORT.md](PHASE_11_REPORT.md) und
[Docker Deployment](../deployment-docker.md).

## Fortschritt Phase 12 (2026-09-28)

- TD-010: CI um gemeinsame Release-Freigabe, Dependency-/Container-Scans,
  Maven-Cache, Test-/Coverage-Artefakte und Dependabot erweitert. GitHub-Ausfuehrung
  und verpflichtender Branch-Protection-Check bleiben extern nachzuweisen.
- TD-005: npm-Audit erneut bestaetigt: 19 betroffene Pakete (1 critical, 10 high,
  5 moderate, 3 low). Das neue Gate blockiert diese Befunde; nicht behoben oder
  unterdrueckt. Daher keine Release-Freigabe und kein Start von Phase 13.
- E2E-Reproduzierbarkeit: kritische Suite gegen Production-Build mit eigenem
  Server, fester Browserzeit/Zeitzone und zustandsbasierten Tab-Assertions gruen;
  generierte Reports werden als Artefakte behandelt.

Details: [PHASE_12_REPORT.md](PHASE_12_REPORT.md),
[CI_RELEASE_GATES.md](CI_RELEASE_GATES.md).

## Phase-12 Security Follow-up (2026-09-28)

TD-005 / initial dependency blockers resolved: npm audit 0, packaged Java scan 0,
no high/critical findings in either final runtime image. Spring Boot 4.0.8,
Tomcat 11.0.25, Next.js/eslint-config-next 16.3.6 and compatible transitive fixes;
frontend runtime package managers and their system-zlib dependency removed.
201 backend tests, 18 critical E2E cases, frontend gates, unchanged OpenAPI and
extended native-library/Compose smoke all pass. Local Phase-12 status now PASS.
Medium/low OS advisories remain tracked; GitHub execution and branch protection
remain external verification. See
[SECURITY_DEPENDENCY_REMEDIATION.md](SECURITY_DEPENDENCY_REMEDIATION.md).
