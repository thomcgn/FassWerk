# PHASE 1 REPORT – Zahlungs- und Request-Idempotenz

Stand: 28.09.2026. Grundlage: [Production-Baseline](PRODUCTION_READINESS_BASELINE.md), Ausgangscode `fb832a8`. Die Freigabe erfolgte nach dem Phase-0-Bericht.

## Problem und resultierendes Verhalten

Ein erfolgreich verbuchter Request konnte seine Antwort verlieren. Add/Remove/Split erzeugten beim nächsten Bedienversuch einen neuen Schlüssel und konnten denselben beabsichtigten Vorgang erneut ausführen. Der Browser speichert jetzt den genauen offenen Befehl samt Schlüssel vor dem Senden in sessionStorage. Bei verlorenem, fehlerhaftem oder nicht eindeutigem Ergebnis erscheint „Offene Aktion wiederholen“. Wiederholungen verwenden denselben Inhalt und Schlüssel, auch nach Reload. Während ein Befehl läuft oder ungeklärt ist, werden weitere Buchungsmutationen dieses Workflows gesperrt.

Beim Tagesabschluss konnten zwei identische HTTP-Requests den Geschäftstag zweimal erhöhen. Der Request enthält jetzt den angezeigten Ausgangstag und einen verpflichtenden Idempotenzschlüssel. Datum und Operationsbeleg werden unter dem vorhandenen globalen PostgreSQL-Buchungslock in derselben Transaktion gespeichert. Derselbe Schlüssel bewirkt nichts erneut; abweichender Inhalt oder ein inzwischen geänderter Ausgangstag liefert 409. Dadurch kann auch ein zweites Gerät mit anderem Schlüssel den alten Tag nicht erneut weiterschalten.

Bei Zurückstellen und Wiederöffnen reicht die bisherige reine Zustandsprüfung für verspätete Wiederholungen nach einem weiteren Zustandswechsel nicht aus. Diese Aktionen sowie Bezahlen unterstützen jetzt die bestehende persistente `BillingOperation`. Ein alter Schlüssel liefert den aktuellen Bonzustand zurück, ohne einen neueren Zustandswechsel rückgängig zu machen.

## Änderungen

- `frontend/lib/pending-mutation.ts`: persistenter offener Befehl, synchroner Schutz gegen parallele Aufrufe, 20-Sekunden-Request-Timeout, Wiederholung mit unverändertem Schlüssel/Payload. Ein eindeutig abgelehnter erster Request kann korrigiert werden; ein Fehler nach einem bereits unklaren Ausgang verwirft den offenen Auftrag nicht.
- `frontend/lib/use-pending-mutation.ts` und `components/pending-mutation-notice.tsx`: Hook und Anzeige; bei nicht verfügbarem Speicher wird keine ungesicherte Buchung gesendet. Fehlerhafte gespeicherte Aufträge werden nicht still gelöscht.
- Tischabrechnung und Tagesabschluss verwenden diese Wiederholung. Erfolgreiche Bon-Antworten aktualisieren den aktuellen Zustand und die Tisch-/Archivansicht. Neue bestätigte Bedienaktionen erhalten neue Schlüssel.
- `TableOrderService`, Controller und drei BFF-Routen: Idempotenz auch für Abschluss, Zurückstellen und Wiederöffnung, einschließlich erfolgreicher zustandsbedingter No-ops. Bestehende Add-/Remove-/Split-Belege und transaktionale Bestandsbuchung bleiben erhalten.
- `SalesConfigurationService`: persistenter Tagesabschluss-Beleg; Konfigurationsänderung und Tagesabschluss verwenden denselben Buchungslock, damit sich diese Schreibvorgänge nicht überholen.
- OpenAPI und generierte Frontend-Typen werden mit dem neuen Vertrag synchronisiert.

## Migration und API-Kompatibilität

**V29 `business_day_close_operations`** ergänzt eine Tabelle mit eindeutigem `operation_key`, dem angeforderten `business_date` und Erstellzeitpunkt. Keine Änderung vorhandener Geschäftsdaten oder Sessions. V26–V28 bleiben unverändert. Die Belege müssen mitgesichert werden und dürfen während eines möglichen Wiederholungszeitraums nicht gelöscht werden.

`POST /api/inventory/configuration/manual-day-close` benötigt jetzt:

```http
Idempotency-Key: <stabiler Schlüssel für diesen Abschluss>
Content-Type: application/json

{"expectedBusinessDate":"2035-06-01"}
```

Die Antwort enthält die aktuelle Konfiguration. Bei späterer Wiederholung nach weiteren Abschlüssen oder einer Konfigurationskorrektur wird kein historischer Konfigurationsstand zurückgeschrieben. Fehlender Header oder fehlendes Ausgangsdatum ergibt 400. Backend und BFF/Frontend gemeinsam ausrollen; alte Clients ohne diese Angaben müssen aktualisiert werden.

Die drei Bon-Zustandsendpoints akzeptieren den Header optional, damit bestehende Aufrufer weiter funktionieren. Die Oberfläche sendet ihn immer. Externe Clients müssen denselben Schlüssel für dieselbe fachliche Aktion wiederverwenden, wenn sie auch über zwischenzeitliche Zustandswechsel hinweg sichere Wiederholung benötigen.

## Fehlerreproduktion und Tests

Vor der Korrektur:

- `ApiContractIntegrationTest.manualDayCloseRetryCannotAdvanceTwice`: zwei gleiche HTTP-Requests erzeugten **2035-06-03 statt 2035-06-02**. Log: `/tmp/fasswerk-phase1-red-backend.log`.
- Erweiterte echte Fullstack-Abnahme: Backend verbuchte eine Position, die Antwort wurde nach `route.fetch()` verworfen. Die normale Bedienoberfläche bot keine gespeicherte Wiederholung. Log: `/tmp/fasswerk-phase1-red-ui.log`.

Neue Regressionen:

- Browsertransport: verlorene Antwort, erneutes Laden des gespeicherten Auftrags, gleiche Schlüssel/Payloads und genau eine Wirkung; neue bewusste Aktion bekommt einen neuen Schlüssel.
- Parallelaufruf/Double-Tap, blockierter anderer Befehl bei unklarem Ausgang, 5xx/ungültiges JSON, definitive erste Ablehnung gegenüber späterer Ablehnung, Speicherausfall und beschädigter gespeicherter Auftrag. Eine verspätete Antwort einer verlassenen Seite darf keinen neueren gespeicherten Auftrag löschen.
- Backend: gleiche Tagesabschluss-Requests parallel; veraltetes Datum eines zweiten Geräts; Schlüssel mit verändertem Datum; altes Replay nach weiterem Tagesabschluss und nach Datumskorrektur; DB-Fehler am Operationsbeleg rollt die Datumsänderung zurück.
- Backend: verspätete Archiv-/Wiederöffnungs-Replays nach weiterem Zustandswechsel; Schlüsselkonflikt zwischen Aktionstypen und wiederholter Abschluss.
- Reale Fullstack-Abnahme: normale UI für Hinzufügen/Storno/Teilzahlung/Tagesabschluss, jeweils erfolgreiche Backend-Verarbeitung mit anschließend verlorener Antwort, expliziter Retry; Reload bei Hinzufügen und Tagesabschluss. Bestehende Zwei-BFF-, Neustart-, Checkout-, Logout- und Restore-Prüfungen bleiben erhalten.
- Bestehende Tests für Check-in, Bestandsabzug, parallele Teilzahlung und atomaren Rollback werden erneut ausgeführt. DIRECT existiert noch nicht und wird erst mit Phase 7 abgenommen.

## Prüfgates

| Prüfung | Ergebnis |
| --- | --- |
| Backend Build / Tests | **PASS** – `clean verify`, 235 Tests, 0 Fehler/Fehlschläge/übersprungen; isolierte Build-Kopie mit PostgreSQL/Testcontainers |
| Frontend Unit / Security | PASS – 15 Unit- und 3 Security-Tests |
| Frontend Lint / TypeScript | PASS |
| Frontend Build | PASS |
| Critical E2E | **PASS** – 18 Tests gegen Produktionsbuild, ohne Retries |
| Reale Fullstack-/Restore-Abnahme | **PASS** – echte UI-Replays, Zwei-BFF-Betrieb, Neustart, Checkout/Logout; PostgreSQL-Dump/Restore mit exaktem Daten-/Flyway-Vergleich einschließlich Tagesabschluss-Belegen. Synthetischer Restore mit Vergleich: 6 Sekunden, keine Produktions-RPO/RTO-Aussage |
| OpenAPI / generierte Typen | **PASS** – zwei identische Exporte auf verschiedenen Ports, `api:generate`, `api:check`, TypeScript-Prüfung |

Temporäre lokale Logs: `/tmp/fasswerk-phase1-backend.log`, `/tmp/fasswerk-phase1-e2e.log`, `/tmp/fasswerk-phase1-fullstack.log`, `/tmp/fasswerk-phase1-openapi.log`. Kein neuer GitHub-CI-Lauf wurde damit behauptet.

**Phasenergebnis: PASS.** Die bestehende TABLE-Funktionalität und der manuelle Tagesabschluss sind für Wiederholungen mit gleichem Schlüssel abgesichert. Die unten aufgeführten Grenzen verhindern weiterhin eine pauschale Produktionsfreigabe.

## Grenzen und verbleibende Risiken

- sessionStorage schützt Reloads desselben Tabs, nicht das Löschen des Browserspeichers oder einen Gerätewechsel. Die serverseitigen Operationsbelege bleiben erhalten. Keine automatische Wiederholung nach Anmeldung; der Benutzer löst die sichtbare offene Aktion ausdrücklich aus.
- Unterschiedliche Geräte können unterschiedliche beabsichtigte Vorgänge nicht allein anhand identischer Mengen unterscheiden. Idempotenz bezieht sich auf denselben Schlüssel; Bestands-/Mengenlimits und DB-Sperren schützen weiterhin die verfügbaren Mengen. Veraltete Formulare sind Gegenstand von Phase 2.
- Nach einem unklaren Ausgang wird ein späterer Fehler nicht als Beweis für fehlende Verbuchung interpretiert. Dauerhafte fachliche Konflikte oder beschädigte Aufträge benötigen Zustandsabgleich/Support; erweiterte Recovery gehört zu Phase 5/16. Offene Aufträge nicht durch Löschen des Speichers umgehen.
- R01 (absoluter Bestands-PUT), R03 (veraltete Schichtformulare) und R04 (öffentliche Reservierungsanlage) aus der Baseline bleiben offen. Keine Produktionsfreigabe.

Nächster Schritt: Phase 2 – Concurrent Editing / Lost Updates, beginnend mit dem reproduzierenden Test für R01.
