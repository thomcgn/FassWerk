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
