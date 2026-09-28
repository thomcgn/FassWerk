# FassWerk — Abschluss-Audit (Phase 15)

Stand: 2026-09-28. Baseline: `4ebd166`, [BASELINE.md](BASELINE.md).
Prüfstand: HEAD `41220a1` **plus die noch nicht committeten Phase-14-Änderungen**.
CI #36 wurde vom Nutzer als bestanden bestätigt und betrifft Phase 13; ein
GitHub-Nachweis für den Phase-14-Arbeitsbaum liegt hier nicht vor.

> **Nachtrag zur Behebung:** Der historische Prüfstand unten bleibt erhalten.
> Aktuelle Fixes, Tests und noch offene Upgrade-/Betriebsgrenzen stehen in
> [AUDIT_REMEDIATION.md](AUDIT_REMEDIATION.md). FA-01 bis FA-07 sind im neuen
> Anwendungspfad bearbeitet; der einmalige Widerruf alter Sitzungen in V26
> wurde nach ausdrücklicher Zustimmung ergänzt. FA-08 und produktive Teile von FA-09 bleiben nachzuweisen.

## Ergebnis und Grenzen

**PASS für die Durchführung des Abschluss-Audits und die lokalen Refactoring-Gates.**
Die kritischen ursprünglichen Konfigurations-, Historienverlust-, Autorisierungs-
und Konkurrenzdefekte sind wesentlich reduziert bzw. gezielt behoben. Die unten
aufgeführten P1-Restbefunde verhindern eine pauschale Produktionsfreigabe.
Insbesondere sind Nachtbetrieb, Session-Widerruf, Missbrauchsschutz und
Scheduler-Verhalten noch nicht vollständig abgesichert.

Dies ist das Abschluss-Audit des Refactoring-Plans mit Phasen 1–15, nicht Phase 0
der separaten [Production-Ready-Roadmap](../FASSWERK_PRODUCTION_READY_ROADMAP.md).
Keine produktive Umgebung, Kundendatenbank, Secret-Rotation, TLS-Infrastruktur,
Branch-Protection-Einstellung oder Disaster-Recovery wurde hier verändert oder
als geprüft behauptet.

In Phase 15 wurden nur Dokumente geändert. Die unmittelbar zuvor bestandenen
Phase-14-Build-/Testgates gelten für denselben unveränderten Anwendungsstand und
wurden nicht ohne Anlass erneut ausgeführt. Scan-Daten wurden dagegen aktualisiert.

## Baseline versus Final State

| Bereich | Baseline | Final State / Grenze |
| --- | --- | --- |
| Backendtests | 16, überwiegend H2 | 206, PostgreSQL-Testcontainers/Flyway, HTTP-, Unit-, Rollen-, Migrations-, Rollback- und Konkurrenztests |
| Zeilen-/Branchabdeckung | 33,97 % / 24,83 % | 79,55 % / 65,19 %; kein Beleg für vollständige Fachabdeckung |
| Kritische Browserfälle | 9, Dev-Server, instabile Selektoren | 18, Production-Build, eigener Server, feste Zeit/Zone, keine Retries im letzten lokalen Lauf |
| Frontend | Keine isolierten Modelltests, fünf Lintwarnungen | 7 Modell- und 3 Security-Tests; Lint und zusätzliche Unused-Prüfung grün |
| Konfiguration | Nutzbare DB-/JWT-Defaults und versionierte `.env` | Pflichtwerte/fail-fast für Standard/prod, explizites dev, nur Templates getrackt |
| Seed-Zugänge | Bekannte aktive Seed-Hashes | Neue V21 deaktiviert unveränderte Seed-Konten; explizites Bootstrap |
| Historie | Kataloglöschung konnte bezahlte Positionen entfernen | Soft Delete und Regression für Umsatz-/Positionserhalt |
| Autorisierung | Authenticated-Fallback mit Rollenlücken | Explizite Rollen inkl. BARCHEF, deny-all, aktuelle Konto-/Rollenprüfung |
| Refresh | Replay-Widerruf konnte zurückrollen; zwei Nachfolger | Account-Lock, committed Replay-Widerruf, prozesslokale BFF-Koordination |
| Konkurrenz | Reproduzierte Überbuchung, Doppelbons, Split-/Bestandsfehler | PostgreSQL-Locks, Unique-Gates, ausgewählte Idempotenz und Soll-Regressionen |
| Reservierung | Zeitpunktzählung ohne Personenkapazität | Zentrale Sitzplatz-/Zeit-/Statusregeln, Nachtfenster/DST, Gruppen und Bonkopplung |
| Verkauf/Bestand | Falsche Mehrfachmengen, Stornos, Wochenwerte/Einheiten | Mengen/Gegenbuchungen am aktuellen Geschäftstag, Wochen-Neuberechnung, korrekte Einheiten; Cross-Day-Lücke bleibt |
| API | Handgeschriebene Typen, LAZY-/Fehlerlücken | Service-DTO-Grenzen, sichere Fehlerantworten, versionierter OpenAPI-Vertrag und generierte TS-Typen |
| CI/Container | Test-Skips im CI-Gate, mutable Basen, Start-Races | Verify/Frontend/E2E/Scans/Release-Gate, gepinnte Basen, versionierte Images, Non-root/Health/Read-only |
| Observability | Registry fehlte; unsichere/unvollständige Fehlerlogs | ADMIN-Prometheus, HTTP/JVM/Hikari/Mail-Metriken, prod-JSON und Request-ID; Collector/Alarmzustellung extern |
| Cleanup | Ungenutztes MapStruct, Imports/Regeln/Parserduplikate | Nach Referenzprüfung entfernt; keine spekulativen DTO-/Routenlöschungen |

Reservierungsbelegung folgt der tatsächlich implementierten konservativen Policy:
unaufgelöste Reservierungen blockieren ihre zugeteilten Tische am Geschäftstag;
Zahlung oder explizites unbezahltes Archivieren beendet den physischen Besuch.
Archivierung tilgt keine Schuld. Keine frei erfundene Aufenthaltsdauer und keine
behauptete physische Nachbarschaft gleich benannter Tischbereiche.

## P0-Baseline: Behebungen und Restrisiken

| Baseline-ID | Finaler Status / Nachweis |
| --- | --- |
| TD-001 | Im Anwendungscode behoben: `StartupSecurityConfiguration`, Pflichtvariablen in Compose/Maven, negative Starttests. Dev-Werte nur im expliziten dev-Profil; keine Garantie gegen externe Fehlkonfiguration. |
| TD-002 | Code-/Migrationslösung vorhanden: V21 und Bootstrap-Tests. Bestehende Deployments müssen die Migration tatsächlich ausführen; kein Nachweis einer Produktivmigration. |
| TD-003 | Tracking behoben: `git ls-files '*env*'` enthält nur `.env.example` und `backend/.env.example`; private Datei ignoriert. Historische Werte bleiben in Git. Etwaige außerhalb Dev verwendete Werte benötigen Betreiber-Rotation; kein vollständiger Historien-Secret-Scan/Rotationsnachweis. |
| TD-004 | Behoben: Soft Delete statt Entfernen historischer Bonpositionen; PostgreSQL-Regression in `BillingInventoryPhase8IntegrationTest`. |
| TD-005 | Ursprüngliche Dependency-Blocker behoben: aktuelles npm-Audit 0, aktueller Java-Paketscan 0. Frühere Image-Scans ohne high/critical; verbleibende OS-Meldungen und zeitliche Grenzen siehe unten. |
| TD-037 / TD-038 aus Phase 1 | Baseline-Testfehler behoben: korrekter Soft-Delete-Vertrag und eindeutige UI-Selektoren; Tests nicht deaktiviert. |

Im geprüften Anwendungsstand wurde kein neuer reproduzierter P0 festgestellt.
Das schließt unbekannte Schwachstellen oder kompromittierte Betreiber-Secrets
nicht aus und ersetzt keine Produktionsprüfung.

## P1-Baseline: Abschlusszuordnung

**IDs sind historisch nicht eindeutig:** Phase 6 verwendet TD-035/036/037 erneut
für Auth-Abuse/Access-Revocation/BFF-Concurrency. Hier heißen neue Restbefunde
`FA-01` bis `FA-09`; historische Bezeichnungen bleiben mit Phasenangabe erhalten.

| Baseline-ID | Status |
| --- | --- |
| TD-006 | Behoben: serverseitige Rollenmatrix und restriktiver Fallback; dynamische Rollenproben plus positive Fachtests. |
| TD-007 | Behoben: Replay-Widerruf committed; allgemeine Persistenzfehler rollen weiterhin zurück. |
| TD-008 | Identifizierte Konkurrenzfehler behoben: Booking/Billing/Bestand/Refresh, DB-Unique und ausgewählte Idempotenz. Kein pauschaler Schutz aller zukünftigen oder Scheduler-Operationen; FA-07. |
| TD-009 | Behoben: kein H2-Testpfad mehr, echte PostgreSQL-Migrationen/Constraints/Locks. |
| TD-010 | Repo-Gates umgesetzt und CI #36 nutzerbestätigt. Externe Branch Protection, Veröffentlichungsregeln und neuer Arbeitsbaum noch separat nachzuweisen. |
| TD-011 | Teilweise: leere DB und gezielte Upgrades getestet; fremde historische Checksummen/Bestandsdaten fehlen, FA-08. |
| TD-012 | Für die oben beschriebene Reservierungspolicy behoben; Kapazität/Personen/Belegung/Zeiten getestet. |
| TD-013 | Teilweise: Reservierung explizit zoniert; Billing/Reports/Schichten weiterhin uneinheitlich, FA-06. |
| TD-014 | Teilweise: Mengen und Same-Day-Storno korrigiert; Cross-Day-Storno offen, FA-05. |
| TD-015 | Sequentielle Wiederholung korrigiert: Wochenwerte werden neu berechnet. Parallelität, NULL-Legacydaten und Nachholen nicht vollständig abgesichert, FA-07. |
| TD-016 | Behoben: Tages-/Wochenbasis, Liter/ml und Mindestbestand vereinheitlicht; Beispielrechnung getestet. |
| TD-017 | Bon-/Bestandsrollback behoben und getestet. Scheduler-Transaktionsgrenze bleibt offen, FA-07. |
| TD-018 | 401/Refresh und lokales Coalescing behoben; Cluster-/Logout-Rennen offen, FA-03. |
| TD-019 | Identifizierte LAZY-HTTP-Pfade behoben, OSIV bleibt aus; Performance/N+1 separat. |
| TD-020 | Offen: numerische Konfigurationsfelder werden weiterhin ignoriert, FA-04. |
| TD-021 | Behoben: Metadaten-Lesen, fehlender Artikel vs. fehlende Metadaten, null-sichere Berechnung. |
| TD-022 | Behoben als versionierter Schema-/Generierungsvertrag; Verhalten über alle Schichten bleibt FA-09. |
| TD-023 | Deutlich verbessert; verbleibende echte Full-Stack-/Recovery-Lücken FA-09. |
| TD-024 | Versionierung und Runtime-Härtung umgesetzt; keine bitidentischen Offline-Builds oder Restore-Sicherheit behauptet. |
| TD-025 | Sensible eigene Logstellen bereinigt, sichere Fehlerdiagnose ergänzt und getestet. DEBUG/TRACE, Proxy-Logs und Deployment-Overrides bleiben Betriebsverantwortung. |

## Offene P1: priorisierte Maßnahmen

„Statisch“ bezeichnet am aktuellen Quellcode belegtes Verhalten; „Risiko“ eine
abgeleitete Auswirkung ohne neue gezielte Laufzeitreproduktion in Phase 15.

| ID / Herkunft | Befund und konkrete Auswirkung | Nächster belastbarer Nachweis / Maßnahme |
| --- | --- | --- |
| FA-01 — Phase-6 TD-035 | Statisch: `AuthService.login` beendet unbekannte Konten vor BCrypt; kein gemeinsames Login-/Refresh-Limit im Repo. Timingunterschied und Missbrauchsrisiko; externe Gateway-Policy unbekannt. | Vor öffentlichem Betrieb vertrauenswürdige IP-/Account-Limits und gleichwertigen Passwortprüfpfad; Last-/Negativtests ohne fremd auslösbare permanente Sperre. |
| FA-02 — TD-036 | Statisch: Logout-/Session-/Replay-Widerruf beendet nicht alle bereits ausgestellten Access-JTIs; Standardlaufzeit 120 Minuten. Aktive-Konto-/Rollenprüfung verbessert Sperren, ersetzt keine Sessionbindung. | Session-/Tokenversionierung; Test: zwei Access-Tokens aus einer Sitzung, Widerruf, beide sofort unbrauchbar. |
| FA-03 — TD-018 / Phase-6 TD-037 | Statisch: `refresh-coordinator.ts` ist prozesslokal, 3 Sekunden/256 Einträge. Risiko zusätzlicher Rotation/Replay und Logout-Rennen über mehrere Instanzen oder verzögerte Antworten. | Zwei BFF-Instanzen sowie verlorene/verspätete Refresh-Antworten und Logout-Race testen; gemeinsame Sitzungskoordination vor Skalierung. |
| FA-04 — TD-020 | Statisch: `SalesConfigurationService.updateConfiguration` speichert Zone/Tagesgrenze/manuelles Datum, ignoriert Lookback/Sicherheitsfaktor/Lieferzeit; GET liefert weiter Property-Defaults. Die API kann erfolgreiche Speicherung suggerieren. | Read-after-write-Test für alle drei Felder; dann Persistenz mit neuer Migration oder explizit schreibgeschützter Vertrag/UI. |
| FA-05 — TD-014 | Statisch: `DrinkSalesTrackingService.reverseSale` sucht den **aktuellen** Geschäftstag. Ein gestern gebuchtes Item kann heute nicht korrekt dem Ursprungstag gegengebucht werden; fehlender Tagesdatensatz führt zu 409, vorhandener kann den falschen Tag belasten. | Ursprungszuordnung je Verkauf/Position unveränderlich speichern; Regression für Bon über Tagesgrenze, Teilstorno und Retry. |
| FA-06 — TD-013 | Statisch: Billing verwendet `LocalDateTime.now`, Reports `LocalDate.now`, Schicht Kalendertage; Lager konfigurierten Geschäftstag. Reportdiagramme selektieren inklusive Endgrenze, Summen `[start,end)`. Risiko abweichender Mitternachts-/Nachtumsätze. | Gemeinsames fachliches Zeitmodell; exakter Endzeitpunkt, DST, Nachtbetrieb und gleiche Schicht-/Report-/Sales-Zuordnung prüfen. |
| FA-07 — TD-015/017 + Legacyaggregate | Statisch: Reorder-Scheduler ist insgesamt `@Transactional`, ruft Berechnung intern auf und fängt Fehler; **keine isolierte Transaktion pro Artikel**. Risiko rollback-only trotz Erfolgszähler. Wochenjob wählt nur gestern, dereferenziert nullable Legacy-Variante und hat keinen ausgewiesenen Cluster-Lock. Catch-up/Parallelität/NULL-Daten nicht durch den sequentiellen Wiederholungstest bewiesen. | PostgreSQL-Tests mit zweitem Artikel nach DB-Fehler, NULL-Variante, verpasstem Wochenwechsel und zwei Scheduler-Läufen; danach klare atomare oder separat transaktionale Verarbeitung/Koordination. |
| FA-08 — TD-011 | Verifikationslücke: echte Produktions-Flyway-Historien/Checksummen fehlen. V23/V24 erfordern zudem bewusste Kapazitäts-/Legacybelegungsprüfung; vorhandene doppelte OPEN-Bons werden nicht still gelöscht. | Anonymisierte Restore-Kopie inklusive `flyway_schema_history` gegen Upgradeplan prüfen; Abbruch-/Rollback-/Backupverfahren belegen. Keine alte Migration ändern. |
| FA-09 — TD-023 / Betrieb | Kritische Fach-Browsertests mocken APIs. Backend und BFF werden zusätzlich separat getestet; das beweist nicht Browser→BFF→Backend→DB samt Zahlung/Bestand nach verlorener Antwort oder Neustart. Restore/Alarmzustellung ebenfalls nicht praktisch nachgewiesen. | Isolierte Full-Stack-Abnahme mit echten Services, Wiederanlauf und Retry/Idempotenz; Restore-Ziel/RPO/RTO messen und Alarm bis zum Empfänger auslösen. |

FA-07 korrigiert ausdrücklich die frühere Formulierung im Phase-8-Debt-Fortschritt,
der Scheduler verarbeite Artikel „isoliert“. Die Annotationen belegen nur eine
umfassende Scheduler-Transaktion; Logging eines Einzelfehlers schafft keine
Transaktionsisolation. In dieser Auditphase wurde dafür kein neuer Laufzeitfehler
reproduziert und keine Fachänderung vorgenommen.

## P2/P3 Technical Debt und Betriebsgrenzen

| Herkunft | Finaler Stand / Priorität |
| --- | --- |
| TD-026 — Architektur | P2: Report/Shift besitzen Billing-Lesegrenze, Inventory-Controller eine Fassade. Andere Fremd-Repositoryzugriffe, große Services und Schreibkopplungen bleiben; gezielte Grenzen statt Big Bang. |
| TD-027 — Frontend | P2: Feature-Modelle, Runtimeparser und Error-/Loading-Boundaries vorhanden; große Screen-Komponenten und sekundäre ungeprüfte JSON-Fallbacks bleiben. |
| TD-028 — Fehlervertrag | P2 teilweise: MVC/Security konsistent; zahlreiche Next-Adapter verwenden `.json().catch(() => ({}))`. Upstream-Fehler nicht überall differenziert. |
| TD-029 — QR/Mail | QR-Scanroute und After-Commit-Mail vorhanden. P2: best effort ohne Outbox/Retry, `sent` keine Zustellgarantie; alte gedruckte QR-URLs ggf. umleiten. |
| TD-030 — PDF | P2 offen: `ReorderReportService` bricht bei `y < 50` ab; Standardfont ohne verifiziertes Unicode-/Mehrseitenkonzept. Lange Listen können unvollständig sein. |
| TD-031 — Wareneingang | Paket-/Restbestand synchronisiert. P2 offen: `updateReorderStatus(RECEIVED)` setzt nur Status, keine Lagerbewegung. UI-/Fachsemantik klären und atomaren Wareneingang testen. |
| TD-032 — Konfiguration | MapStruct/Nullable-Warnungen und redundante Properties bereinigt. Mockito-Agent-/Node-Modulerkennungswarnungen sichtbar; keine Unterdrückung. |
| TD-033 — Lint | Identifizierte Warnungen behoben. P2: bestehendes `skipLibCheck=true` bleibt; strengere Bibliotheksprüfung ist nicht durch den Unused-Check ersetzt. |
| TD-034 — Monitoring | Anwendungssignale/Request-ID/JSON/Registry umgesetzt. P2: externer Collector/Scraper, Alarmzustellung und Scheduler-Heartbeat fehlen als Nachweis; Scrape-Zugang nutzt bisher expiring ADMIN-Token. |
| TD-035 aus Baseline — E2E | Identifizierte Reproduzierbarkeitsprobleme behoben: Production-Server, keine Wiederverwendung, feste Uhr und zustandsbasierte Assertions. Full-Stack-Lücke separat FA-09. |
| TD-036 aus Baseline — Access | Aktuelle Konto-/Rollenprüfung vorhanden; verbleibender Widerruf siehe FA-02. |
| Globale Sperre / Performance | P2: `BookingMutationLock` nutzt eine DB-weite Advisory-ID; Reservierung lädt aktive Datensätze. Kein Throughput-/Mehrmandanten- oder N+1-Benchmark. |
| Lieferkette / Images | P2: Basen per Digest, Lockfile und Release-Tags. Maven-/npm-Netzwerk und nicht vollständig gepinnte Action-Versionen bleiben; Registry-Tag-Unveränderlichkeit extern. Keine bitidentische Reproduzierbarkeit behauptet. |
| Dokumentation | Historische Berichte bleiben historische Nachweise; widersprüchliche Reststatus werden durch dieses Audit aufgelöst. P3: spätere sprachliche Vereinheitlichung ohne Fachwirkung. |

TLS/HSTS/Proxyvertrauen, Secret-Rotation, Datenaufbewahrung, Backup-Retention,
Wiederherstellung und Rollenvergabe müssen am tatsächlichen Deployment geprüft
werden. Betriebshandbücher sind Anleitungen, keine Durchführungsnachweise.

## Testabdeckung und überprüfte Artefakte

Frische JaCoCo-/Surefire-Artefakte stammen aus dem unmittelbar vorangegangenen
Phase-14-`clean verify`, nicht aus akkumulierten alten Testläufen.

| Metrik | Baseline | Final: abgedeckt / gesamt | Final |
| --- | --- | --- | --- |
| Instructions | 35,06 % | 10193 / 12689 | 80,33 % |
| Branches | 24,83 % | 573 / 879 | 65,19 % |
| Lines | 33,97 % | 1871 / 2352 | 79,55 % |
| Complexity | 26,52 % | 632 / 1006 | 62,82 % |
| Methods | 34,55 % | 446 / 558 | 79,93 % |
| Classes | 60,15 % | 144 / 157 | 91,72 % |

| Service | Zeilen abgedeckt / gesamt | Branches abgedeckt / gesamt |
| --- | --- | --- |
| ReservationService | 145 / 152 | 92 / 118 |
| AuthService | 128 / 151 | 19 / 34 |
| TableOrderService | 268 / 297 | 79 / 114 |
| InventoryService | 161 / 218 | 39 / 63 |
| MenuService | 48 / 190 | 9 / 36 |
| ReorderCalculationService | 60 / 79 | 13 / 23 |
| SalesConfigurationService | 26 / 39 | 7 / 12 |
| ReorderReportService | 28 / 46 | 4 / 18 |

Menu, Konfigurationspersistenz und PDF-Ränder verdienen gezielte Fachtests.
Coverage enthält nicht automatisch sinnvolle Assertions für jede ausgeführte
Zeile. Rollenproben mit absichtlich unvollständigen Requests können 400/404 als
Nachweis einer passierten Autorisierung nutzen; sie sind keine vollständigen
positiven Geschäftsabläufe. Kein willkürlicher Coverage-Mindestwert wurde ergänzt.

| Prüfung | Nachweis / Ergebnis |
| --- | --- |
| Backend Build/Tests | Phase 14: `clean verify`, 206 Tests, 0 Fehler/Failures/Skips, JAR und JaCoCo |
| Flyway | PostgreSQL-Testcontainers: frischer Schemaaufbau plus gezielte Legacy-/V21–V25-Upgrades. `git diff 4ebd166 -- .../db/migration` zeigt ausschließlich die fünf neuen V21–V25-Dateien; alle 13 alten SQL-Dateien unverändert. |
| Frontend | Phase 14: API-Typecheck, 7 Unit-/3 Security-Tests, Lint, `tsc --noEmit --noUnusedLocals --noUnusedParameters`, Production-Build bestanden |
| Kritische Browserflows | Phase 14: 18/18 Chromium, Production-Server, `--retries=0` |
| API-Vertrag | Phase 14: Production-JAR mit frischer DB auf zwei Ports; Export byte-identisch und unverändert zum Commit, TS-Typen unverändert |
| npm | Phase 15 erneut: `npm audit --json`, **0 Befunde** |
| Java-Paket | Phase 15 erneut: Grype v0.119.0 `dir:backend/target --fail-on high`, **0 Matches** |
| Container | Phase-11/12-Smoke/Scans und nutzerbestätigte CI #36 als bisherige Nachweise; **keine neuen Images für den Phase-14-Arbeitsbaum in Phase 15 gebaut/gescannt** |
| Statische Kontrolle | Legacy-Migrationsdiff, getrackte Env-Dateien/Ignore, Gate-Abhängigkeiten, Test-Skips, Codepfade und `git diff --check` geprüft |

Letzter dokumentierter OS-Imagebefund aus der Dependency-Remediation: Backend
118 medium/6 low, Frontend 2 medium, jeweils keine high/critical. Das sind
historische Imagewerte, nicht Resultate eines neuen Image-Scans in Phase 15.
Siehe [Dependency Remediation](SECURITY_DEPENDENCY_REMEDIATION.md).

Keine deaktivierten Tests/knownGap-Erwartungen in den geprüften Testquellen
gefunden. Der Backend-Docker-Build überspringt Tests bewusst; **direkter Imagebau
allein ist kein Qualitätsgate**. Die CI führt `clean verify` davor aus und das
abschließende `release-gate` fordert sämtliche Tests und Scans erfolgreich ein.
Branch Protection muss dieses Gate extern verbindlich machen.

Lokale Nachweise: `backend/target/surefire-reports`,
`backend/target/site/jacoco/jacoco.xml`, `/tmp/fasswerk-phase14-*.log`,
`/tmp/fasswerk-phase15-npm-audit.json`, `/tmp/fasswerk-phase15-java-audit.json`.
Temporäre Artefakte sind nicht dauerhaft versioniert. CI archiviert Berichte;
konkrete Ausführung/Git-SHA müssen bei Veröffentlichung zusammengeführt werden.

## Definition of Done des Refactoring-Plans

| Kriterium | Bewertung |
| --- | --- |
| Backend baut, Tests grün | PASS lokal für geprüften Arbeitsbaum |
| Flyway auf leerem PostgreSQL | PASS; reale fremde Historien separat FA-08 |
| Frontend lintet und Production-Build | PASS lokal |
| Kritische E2E grün | PASS innerhalb dokumentierter Mock-/BFF-Grenzen |
| Keine produktionsfähigen Default-Secrets | PASS für Standard-/prod-Konfiguration; Historie/Rotation extern |
| Security serverseitig | PASS Rollen/Authentifizierung; keine Behauptung vollständiger Abuse-/Widerrufssicherheit |
| Schemaänderungen nur Flyway | PASS; ausschließlich neue Migrationen |
| Kritische Regeln automatisiert getestet | PASS für dokumentierte Invarianten; Restlücken FA-04 bis FA-09 transparent |
| Images reproduzierbar versionierbar | PASS Mechanismus/CI; keine Bitidentität oder finale neue Imagefreigabe behauptet |
| Architektur dokumentiert | PASS: Domain Map, API-, Frontend-, Billing-/Reservation- und Betriebsdokumente |

## Empfohlene nächste Schritte

1. Phase-14/15-Arbeitsstand reviewen und durch CI validieren; verpflichtendes
   Release-Gate/Branch Protection prüfen. Kein Commit/Push hier durchgeführt.
2. Separate Production-Roadmap **Phase 0** mit diesem Audit als Eingang beginnen:
   reale Workflows, Deploymentannahmen, Datenbestand und Betriebsnachweise erfassen.
   Ihre Phasennummern ersetzen nicht nachträglich diesen Refactoring-Plan.
3. Vor öffentlichem Betrieb FA-01/02 priorisieren; vor horizontaler Skalierung
   zusätzlich FA-03/07. FA-04/05/06 vor verlässlicher Nachbestell-/Nachtabrechnung
   schließen. Jeweils zuerst den konkret beschriebenen Fehler-/Grenztest schreiben.
4. Vor produktiver Datenübernahme FA-08 und vor belastbarer Betriebsfreigabe FA-09
   nachweisen. Offene Risiken nicht allein durch grüne Mock-E2E oder Coverage schließen.

## PHASE 15 REPORT

Geänderte Dateien: `FINAL_AUDIT.md`, Abschlussverweis/-status in `TECH_DEBT.md`.
Vorhandene Phase-14-Änderungen bleiben erhalten. Neue/geänderte Tests: keine;
keine neue Anwendungscodeänderung. Behobenes Dokumentationsproblem: uneindeutige
Debt-IDs und fälschlich behauptete Scheduler-Transaktionsisolation eingeordnet.
Ergebnis: **PASS Audit, keine pauschale Produktionsfreigabe**.
Nächster Schritt: separate Production-Roadmap Phase 0 nach Freigabe, nicht
automatisch eine neue Implementierungsphase.
