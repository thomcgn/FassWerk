# PHASE 4 REPORT

Datum: 2026-09-27. Ausgangsstand: sauberer Arbeitsbaum, Phase 3 und CI-OpenAPI-Fix
bereits vorhanden. Keine Phase 5 begonnen.

## Loesung

Domain Map am tatsaechlichen Code erstellt und eine klare, begrenzte Grenzverletzung
beseitigt: Reporting und Schichtabrechnung verwenden keine fremden Billing-Entities
oder -Repositories mehr, sondern einen expliziten lesenden Application-Vertrag.

Die Domain Map unterscheidet acht Kontexte. Catalog/Pricing und Shift Settlement
sind eigene Verantwortungsbereiche; Ordering/Billing bleiben wegen ihres gemeinsamen
Bonmodells zusammen. Administration ist eine Rollen-/UI-Sicht, kein neuer Sammelkontext.

## Geaenderte Dateien

- `docs/architecture/DOMAIN_MAP.md`: Entities/Aggregatzuordnung, vorhandene und fehlende
  Value Objects, Use Cases, Repositories, REST, Eventkandidaten, Ist-/Zielabhaengigkeiten,
  verbleibende Zyklen und schrittweise Durchsetzung pro Kontext.
- `backend/src/main/java/org/thomcgn/backend/billing/application/BillingRevenueQueries.java`:
  lesender, frameworkunabhaengiger Modulvertrag.
- `backend/src/main/java/org/thomcgn/backend/billing/application/PaidOrderRevenue.java`:
  unveraenderliches Lesemodell ohne Entity/Repository/REST-Abhaengigkeiten.
- `backend/src/main/java/org/thomcgn/backend/billing/service/BillingRevenueQueryService.java`:
  Billing-interne read-only-Implementierung und Mapping der bestehenden Abfragen.
- `backend/src/main/java/org/thomcgn/backend/report/service/RevenueReportService.java`:
  Diagramme/Beschriftung bleiben Reporting; Daten kommen aus dem Billing-Vertrag.
- `backend/src/main/java/org/thomcgn/backend/shift/service/ShiftSettlementService.java`:
  Tagesumsatz ueber Billing-Vertrag; Lohn-/Kassenformel unveraendert bei Shift.
- `backend/src/test/java/org/thomcgn/backend/report/service/RevenueReportServicePaidOnlyTest.java`:
  vorhandener Unit-Test auf den Modulvertrag statt Persistenzdetails umgestellt.
- `backend/src/test/java/org/thomcgn/backend/billing/application/RevenueBoundaryIntegrationTest.java`:
  vier echte PostgreSQL-Regressionsfaelle fuer Konsumenten und Query-Vertrag.
- `backend/src/test/java/org/thomcgn/backend/architecture/BillingReadBoundaryTest.java`:
  zwei gezielte statische Grenzpruefungen fuer Report und Shift.
- `docs/architecture/TECH_DEBT.md`, `docs/testing.md` und dieser Bericht: Fortschritt,
  Teststrategie, Grenzen und Gates.

## Behobene Probleme

- Sechs direkte Billing-Domain-/Repositoryreferenzen in Report (4) und Shift (2)
  entfernt. Beide Kontexte referenzieren Billing jetzt nur ueber billing.application.
- Billing besitzt die Bezahlt-/CLOSED-Selektion und das Mapping aus Persistenzdaten.
  Managed Entities verlassen diese neue Modulgrenze nicht.
- Verantwortlichkeiten sind explizit: Billing liefert Daten, Reporting formatiert
  Auswertungen, Shift berechnet Arbeitskosten und erwartete Kasse.
- Bestehende Transaktionsart und synchroner Aufruf bleiben erhalten. Keine neue
  Eventual Consistency, kein Umbau der Zahlungs-/Bestands-Schreibpfade.

## Neue und geaenderte Tests

Vor dem Umbau liefen zwei neue Konsumenten-Regressionsfaelle plus der vorhandene
Reporting-Unit-Test erfolgreich gegen den Ausgangscode (3/3).

Vier neue PostgreSQL-Testfaelle im Endstand:

1. Leere Schicht: nur bezahlte CLOSED-Positionen, Folgetag-Mitternacht ausgeschlossen.
2. Reporting: Tages-/Wochen-/Monatssummen, Verbrauch und Diagrammsummen unveraendert.
3. Gespeicherte Schicht: Umsatz 12, Anfangskasse 100, Kosten 5, Loehne 20 ergeben
   erwartete Kasse 87; Speichern, Lesen und Zeitraumsliste funktionieren weiter.
4. Billing-Lesemodell: halb-offene Summengrenze und inklusive Legacy-Diagrammgrenze
   werden getrennt geprueft; keine Mutation von Bonanzahl oder Positionssummen.

Zwei neue Architektur-Testfaelle verbieten Report/Shift Referenzen auf Billing-
Implementierung, -Domain und -Repositories. Der Test liest Java-Quellen einschliesslich
voll qualifizierter Typreferenzen; keine zusaetzliche Architekturdependency.
Das ist eine gezielte statische Leitplanke, keine vollstaendige Laufzeitanalyse.

Ein erster neuer Grenztest verwendete JDBC-Fixtures mit anderer Kalenderbindung
als Hibernate (UTC) und zeigte eine Stundenverschiebung. Zeitbezogene Bon-Fixtures
werden jetzt ueber dieselben JPA-Repositories wie die Anwendung erzeugt. Die
Produktions-Zeitkonfiguration und die Assert-Grenzen wurden nicht veraendert.
Der korrigierte fokussierte Lauf vor dem zusaetzlichen gespeicherten Schichttest
bestand mit 6/6 Faellen.

## Kontextzuordnung geaenderter Funktionen

| Funktion / Typ | Fachlicher Owner |
| --- | --- |
| BillingRevenueQueries, PaidOrderRevenue | Billing, exportierter lesender Vertrag |
| BillingRevenueQueryService.revenue / consumedVolumeMl / paidOrdersClosedBetweenInclusive | Billing, Persistenzselektion und Lesemodell-Mapping |
| RevenueReportService.getOverview / sumForRange / consumedMlForRange / buildMonthPoints | Reporting, Auswertungs-/Darstellungslogik |
| ShiftSettlementService.resolveDailyRevenue | Shift Settlement, Tagesauswahl und Rundung des bezogenen Umsatzes |

Damit ist das Phase-4-Gate fuer jede neu geaenderte Funktion explizit nachvollziehbar.

## Offene Risiken

- Catalog/Billing/Inventory besitzen weiter Schreibzyklen und fremde Repositoryzugriffe.
  TD-004 (historische Bonpositionen bei Catalog-Loeschen) und die fuenf bekannten
  Konkurrenzdefekte sind nicht behoben. Keine Behauptung vollstaendiger Modularitaet.
- Das vorhandene unterschiedliche Intervallende von Summen und Diagrammen bleibt
  bestehen und ist im Vertrag/Regressionstest bewusst sichtbar. Die fachliche
  Zeit-/Business-Date-Vereinheitlichung folgt in Phasen 7/8.
- N+1-Abfragen werden innerhalb Billing gekapselt, nicht optimiert. Reporting bleibt
  eine Live-Abfrage, Schichten erhalten keinen neuen Umsatzsnapshot.
- Events der Domain Map sind explizite Kandidaten, keine implementierte Infrastruktur.
  Aggregate sind Verantwortungszuordnungen, nicht nachtraeglich behauptete DDD-Sicherheit.
- Bekannte Frontend-Lint-/Audit-Befunde und API-Mocks in Playwright bleiben bestehen.

## Ausgefuehrte Pruefungen

| Pruefung | Ergebnis |
| --- | --- |
| Regression vor Refactoring | PASS: 3/3 Tests |
| Fokussierter Lauf nach Umbau und Fixture-Korrektur | PASS: 6/6 Tests |
| `./mvnw -B clean verify` | PASS: 76 Tests, 0 Failures, 0 Errors, 0 Skips; JAR/JaCoCo erstellt, 1:13 min |
| `npm ci` | PASS, unveraendert 20 Audit-Befunde (1 critical, 11 high, 5 moderate, 3 low) |
| `npm run lint` | PASS, 0 Fehler und 5 bekannte Warnungen |
| `npm run build` | PASS |
| `npm run test:e2e:critical` | PASS: 9/9 beim ersten Phase-4-Lauf, 56 Sekunden |
| `git diff --check` | PASS |
| Migrationen / POM / REST-Adapter und DTOs | Unveraendert |

Backend und Browser-Gate wurden nacheinander ausgefuehrt. Keine Timeouts erhoeht,
Tests deaktiviert, Assertions abgeschwaecht oder Warnungen unterdrueckt.
Generierte Browserartefakte sind unter `/tmp/fasswerk-phase4-playwright-final`
gesichert; bereits versionierte Reportdateien wurden auf den sauberen Ausgangsstand
zurueckgesetzt. Keine Frontend-Quellcodeaenderung. Testcontainer sind beendet.

Nachweise: `/tmp/fasswerk-phase4-before.log`,
`/tmp/fasswerk-phase4-after-corrected.log`, `/tmp/fasswerk-phase4-backend-verify.log`,
`/tmp/fasswerk-phase4-npm-ci.log`, `/tmp/fasswerk-phase4-lint.log`,
`/tmp/fasswerk-phase4-build.log` und `/tmp/fasswerk-phase4-e2e.log`.
Kein GitHub-Run, Commit oder Push in dieser Phase ausgefuehrt.

## Ergebnis

**PASS.** Domain Map erstellt; alle neu geaenderten Funktionen besitzen einen
expliziten fachlichen Owner. Die begrenzte Billing-Lesegrenze ist implementiert,
durch Regressionstests und eine statische Leitplanke abgesichert. Alle Phasengates
erfolgreich; keine vollstaendige Entkopplung der verbleibenden Schreibkontexte behauptet.

## Empfohlener naechster Schritt

**PHASE 5 -- Controller und API Layer**, nach ausdruecklicher Freigabe.
Die Domain Map als Grundlage fuer DTO-/Mapping-/Use-Case-Grenzen nutzen; besonders
InventoryController und die noch aus Application Services exponierten Entities
betrachten. Phase 5 wurde nicht automatisch begonnen.
