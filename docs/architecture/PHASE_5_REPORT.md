# PHASE 5 REPORT

Datum: 2026-09-27. Ausgangsstand: sauberer Arbeitsbaum nach Phase 4.
Scope: Controller und API Layer. Keine Phase 6 begonnen.

## Loesung

Alle neun Controller geprueft. InventoryController delegiert seine bisherige
Mehrfachorchestrierung und Entity-Abbildung an InventoryInsightsApplicationService.
Die DTO-Abbildung bleibt innerhalb der Service-Transaktion; OSIV bleibt deaktiviert.
Auch die drei Nachbestellungslisten besitzen jetzt eine lesende Transaktionsgrenze.

Die zentrale MVC-Fehlerbehandlung bewahrt Statuscodes und relevante Header, entfernt
Parser-/SQLdetails und behaelt das vom Frontend verwendete JSON-Envelope.
Problem Details wurden bewusst nicht als stiller Breaking Change eingefuehrt.
Controller-Audit, Statusentscheidungen und Grenzen stehen in
[API_LAYER_REVIEW.md](API_LAYER_REVIEW.md).

## Geaenderte Dateien

- Inventory-Adapter: backend/src/main/java/org/thomcgn/backend/inventory/api/InventoryController.java.
- Neue Use-Case-Fassade: backend/src/main/java/org/thomcgn/backend/inventory/service/InventoryInsightsApplicationService.java.
- Servicekorrekturen: backend/src/main/java/org/thomcgn/backend/inventory/service/ReorderCalculationService.java,
  backend/src/main/java/org/thomcgn/backend/inventory/service/ReorderOrderService.java,
  backend/src/main/java/org/thomcgn/backend/inventory/service/SalesConfigurationService.java.
- Requestvalidierung: backend/src/main/java/org/thomcgn/backend/inventory/api/dto/ConsumptionMetadataRequest.java,
  backend/src/main/java/org/thomcgn/backend/inventory/api/dto/SalesConfigurationRequest.java,
  backend/src/main/java/org/thomcgn/backend/inventory/api/dto/ReorderOrderRequest.java.
- Fehler-/Korrelationsadapter: backend/src/main/java/org/thomcgn/backend/common/api/GlobalExceptionHandler.java,
  backend/src/main/java/org/thomcgn/backend/common/api/RequestCorrelationFilter.java.
- Gezielte Parserfehler-Uebersetzung: backend/src/main/java/org/thomcgn/backend/auth/service/AuthService.java.
- Neue Tests: backend/src/test/java/org/thomcgn/backend/common/api/ApiContractIntegrationTest.java,
  backend/src/test/java/org/thomcgn/backend/common/api/RequestCorrelationFilterTest.java.
- Dokumentation: docs/architecture/API_LAYER_REVIEW.md, docs/architecture/DOMAIN_MAP.md,
  docs/architecture/TECH_DEBT.md, docs/testing.md und dieser Bericht.

## Behobene Probleme

- LAZY-Zugriffe nach geschlossenem Persistence Context in Inventar-/Nachbestellantworten.
- GET consumption-metadata als leerer Platzhalter trotz vorhandener Daten.
- NullPointerException bei Nachbestellberechnung ohne verknuepfte Variante.
- Falsche 500 bei fehlenden/ungueltigen Parametern, fehlerhaftem JSON, falscher
  Methode bzw. falschem Content-Type; Constraintkonflikte jetzt generisch 409.
- Ungueltige Refresh-Tokens, Zeitzonen und Nachbestellstatus liefern sichere 400.
- Fehlende Requestconstraints fuer Nachbestellerstellung und negative/ungueltige
  Lookback-/Lieferzeit-/Sicherheitsfaktorwerte; invertierte Tagesabfrage und
  nichtpositive Wochen-/Historyparameter werden abgewiesen.
- QR-Scan-Fehler geben den Token nicht im gematchten Fehlerpfad zurueck.
- Request-IDs werden hinsichtlich Zeichensatz und Laenge geprueft.

## Neue Tests und Reproduktion

ApiContractIntegrationTest umfasst 30 echte HTTP-Testfaelle mit PostgreSQL/Flyway
und deaktiviertem OSIV. RequestCorrelationFilterTest umfasst fuenf isolierte Faelle.

Vor dem ersten Produktionsumbau schlugen 18 von 20 neuen HTTP-Faellen erwartungsgemaess
fehl. Nach der ersten Korrektur bestanden alle 20. Ein erweiterter fokussierter
Lauf bestand mit 33 Tests (26 HTTP, 5 Filter, 2 bestehende OpenAPI-Tests).

Anschliessend wurden die drei Nachbestellungslisten und ein leerer Erstellrequest
vor ihrer Korrektur reproduziert: vier Fehler in zehn ausgefuehrten Faellen.
Die passenden read-only-Grenzen und DTO-Constraints wurden erst danach ergaenzt.
Der abschliessende Gesamtlauf enthaelt alle 35 neuen Testfaelle.

Abgedeckt sind insbesondere 400/404/405/409/415/500, Allow-Header, Envelope und
Request-ID, Parser-/SQL-/Tokenredaktion, geladene LAZY-Relationen, Metadaten
Read-after-write, fehlende Artikel/Metadaten sowie Nullberechnung.
Der unerwartete 500 wird gezielt per Service-Spy ausgeloest; die anderen fachlichen
Mapping-/Persistenzpfade verwenden die realen Services und Repositories.

## Ausgefuehrte Pruefungen

| Pruefung | Ergebnis |
| --- | --- |
| Fehlerreproduktion vor Umbau | 18/20 initiale HTTP-Faelle rot, anschliessend 20/20 gruen |
| Erweiterter fokussierter Lauf | PASS: 33 Tests inkl. JSON-/YAML-OpenAPI |
| Nachbestellungsreproduktion vor gezieltem Fix | Vier erwartete Fehler, sechs bereits gruene Faelle |
| ./mvnw -B clean verify | PASS: 111 Tests, 0 Failures, 0 Errors, 0 Skips; JAR und JaCoCo erstellt |
| npm ci | PASS; 20 bekannte Audit-Befunde (1 critical, 11 high, 5 moderate, 3 low) |
| npm run lint | PASS: 0 Fehler, 5 bekannte Warnungen |
| npm run build | PASS |
| npm run test:e2e:critical | PASS: 9/9 beim ersten Phase-5-Lauf, 54.8 Sekunden |
| git diff --check | PASS |

Keine Tests deaktiviert, Timeouts erhoeht, Warnungen unterdrueckt oder Assertions
abgeschwaecht. Kein Schema-/Dependency-/Frontend-Quellcodewechsel.
Die Browserpruefung startet erst nach den abgeschlossenen Backend-/Frontend-Builds.
Kein GitHub-Lauf, Commit oder Push in dieser Phase.

Nachweise: /tmp/fasswerk-phase5-before.log, /tmp/fasswerk-phase5-after.log,
/tmp/fasswerk-phase5-api-focused.log, /tmp/fasswerk-phase5-reorder-before.log,
/tmp/fasswerk-phase5-backend-verify.log, /tmp/fasswerk-phase5-npm-ci.log,
/tmp/fasswerk-phase5-lint.log, /tmp/fasswerk-phase5-build.log und
/tmp/fasswerk-phase5-e2e.log.

## Offene Risiken

- Security-Filterfehler/401/403 und Rollenmatrix sind noch nicht vereinheitlicht;
  diese HTTP-Tests arbeiten bewusst mit ADMIN-Token. Security Review folgt Phase 6.
- Fuenf bekannte Konkurrenzdefekte, Refresh-Replay-Rollback und historische
  Bonloeschung bleiben offen. Gruene knownGap-Tests belegen weiterhin den Defekt.
- Nachbestellformeln, Einheiten, Aggregationsidempotenz und ignorierte numerische
  globale Konfigurationswerte sind nicht behoben; zustaendig sind Phasen 7/8.
- API-Format ist bewusst nicht RFC 9457. Legacy-Erfolgscodes und leere 200-Antworten
  bleiben dokumentiert; koordinierte Vertragsbereinigung folgt Phase 10.
- Request-/DTO-Validierung ist keine vollstaendige fachliche Invariantenpruefung.
  Nicht alle 81 MVC-Mappings haben individuelle neue End-to-End-Tests.
- Sensible DEBUG-Logs und fehlendes serverseitiges Logging unerwarteter Fehler
  bleiben TD-025; sichere HTTP-Fehlertexte loesen diese Betriebsrisiken nicht.
- N+1-Abfragen, fehlende Obergrenzen/Pagination und weitere Servicekopplungen bleiben.
- Bekannte Frontend-Audit-/Lintbefunde unveraendert; Playwright nutzt API-Mocks.

## Ergebnis

**PASS.** Alle neun Controller geprueft, Inventory-Adapter ausgeduennt und relevante
API-Fehler durch echte HTTP-Tests abgesichert. Saemtliche Phasengates erfolgreich.

Browserartefakte sind unter /tmp/fasswerk-phase5-playwright-final gesichert.
Die beiden zuvor sauberen versionierten Browserreportdateien wurden ausschliesslich
von den eigenen Testaenderungen auf den Ausgangsstand zurueckgesetzt.

## Empfohlener naechster Schritt

PHASE 6 -- Security Review, erst nach erfolgreichem Abschluss und ausdruecklicher
Freigabe. JWT/Rotation/Replay, serverseitige Rollenmatrix, Filterfehler, CORS/CSRF,
Security Headers, sensible Logs und Actuator untersuchen.
