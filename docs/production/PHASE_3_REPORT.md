# PHASE 3 REPORT – Atomare Geschäftsprozesse

Stand: 28.09.2026. Ausgangspunkt: `c80400c`, abgeschlossene [Phase 2](PHASE_2_REPORT.md). Grundlage: [Production-Roadmap](../FASSWERK_PRODUCTION_READY_ROADMAP.md).

## Ergebnis

Status: **PASS**.

Die untersuchten TABLE-Prozesse besitzen bereits gemeinsame PostgreSQL-Transaktionen. Die Änderungen dieser Phase ergänzen Fehlernachweise; es wurde keine zusätzliche Atomaritätslücke reproduziert. Es gibt keine neue Migration, keinen API-Wechsel und keine Änderung der produktiven Buchungslogik.

Bestand und Absatz werden beim Hinzufügen einer Bonposition gebucht, nicht erst bei Zahlung. Ein offener oder ausdrücklich unbezahlt archivierter Bon kann deshalb bereits verbrauchten Bestand repräsentieren. Zahlung darf diesen Bestand nicht nochmals reduzieren. Umsatz entsteht aus bezahlten, geschlossenen Bons; Schicht und Bericht lesen dieselbe Umsatzquelle.

## Transaktionsgrenzen und vorhandener Schutz

| Vorgang | Zusammengehörige Änderungen / Verhalten |
| --- | --- |
| `TableOrderService.addItem` | Position, Bestand/Revision, Bestandsbewegung, Tagesabsatz, Nachbestellberechnung/-empfehlung und BillingOperation innerhalb einer Transaktion. |
| `TableOrderService.removeItem` | Mengenreduktion/Löschung, Rückbestand, Bewegung, Absatzkorrektur am ursprünglichen Geschäftstag, Nachbestellberechnung und Operationsbeleg gemeinsam. |
| `TableOrderService.close` | Bezahlt-/Geschlossenstatus, Abschlusszeit/Geschäftsdatum, Tischfreigabe, gegebenenfalls Reservierungsabschluss und Operationsbeleg gemeinsam. Bereits gebuchter Bestand/Absatz bleibt unverändert. |
| `TableOrderService.splitPayment` | Bezahlter Teilbon und seine Positionen, Restpositionen, gegebenenfalls Abschluss des leeren Ursprungsbons, Tisch-/Reservierungsfreigabe und Ergebnisbeleg gemeinsam. Kein zweiter Warenverbrauch. |
| Archivierung / Wiederöffnung | Bonstatus, Zeit-/Datumsfelder, Tischstatus und Operationsbeleg gemeinsam; Archivierung verbucht keinen bezahlten Umsatz. |
| `ReorderOrderService.updateReorderStatus` | Empfangsstatus/-menge/-zeit und Bestandsanpassung samt Bewegung in einer Transaktion. Der interne `saveAndFlush` des Bestands ist kein Commit. |
| Schichtumsatz / Reporting | `BillingRevenueQueryService` leitet Umsatz aus bezahlten Bons ab. Keine zusätzliche asynchrone Umsatzkopie, deren Nachführung beim Abschluss ausfallen könnte. |

Die öffentlichen schreibenden Service-Einstiege sind transaktional. Aufgerufene Services treten mit dem normalen REQUIRED-Verhalten derselben Transaktion bei. Der synchrone `ReservationService.onTableVisitEnded` verlangt mit MANDATORY eine bestehende Transaktion. PostgreSQL-BookingMutationLock und gezielte Bestandszeilensperren bleiben erhalten und wirken auch über Backend-Instanzen hinweg.

Im geprüften Zahlungs-/Bestandsprozess liegen keine externen Zahlungsanbieter- oder Mailaufrufe. Reservierungsentscheidungs-Mails laufen bereits AFTER_COMMIT; SMTP kann weiterhin die Antwort verzögern, hält dabei aber nicht die Buchungssperre bis zur Mailzustellung. Eine dauerhafte Outbox/Wiederholung ist nicht vorhanden und wird hier nicht behauptet. Der Nachbestell-Scheduler verwendet bewusst eine eigene Transaktion je Artikel; seine Isolation ersetzt nicht die gemeinsame Transaktion einer Bonbuchung.

## Neue Failure-Injection-Tests

`BillingInventoryPhase8IntegrationTest` erhält neun zusätzliche Testfälle:

- Sieben parametrisierte Fälle: Hinzufügen, Storno, vollständige Zahlung, Teilzahlung, vollständige Split-Zahlung, unbezahlte Archivierung und Wiederöffnung. Ein nur im Test erzeugter PostgreSQL-Constraint-Trigger löst den Fehler erst beim COMMIT des Operationsbelegs aus, nachdem die SQL-Änderungen ausgeführt wurden.
- Ein echter SQL-Fehler beim Speichern einer Nachbestellberechnung nach Mengenänderung einer bestehenden Position.
- Ein Fehler beim abschließenden Empfangsstatus einer Lieferung nach bereits geflushtem Bestand und angelegter Bewegung.

Nach Rückkehr aus dem fehlgeschlagenen Service-Aufruf werden vollständige Tabellenzeilen direkt per JDBC verglichen: Bons/Positionen/Operationsbelege, Tische, Inventar einschließlich Revision, Bewegungen, täglicher/wöchentlicher Absatz, Nachbestellberechnungen und Lieferungen sowie Reservierungs- und Schichtdaten. PostgreSQL-Sequenzen sind ausdrücklich ausgenommen, da verbrauchte Sequenzwerte auch bei korrektem Rollback nicht zurückgesetzt werden.

Anschließend wird die Fehlerinjektion entfernt und derselbe Befehl mit demselben Schlüssel wiederholt. Die Wiederholung muss erfolgreich sein und ein weiterer Retry darf keine zusätzliche Änderung bewirken. Erwartete Bestandsmengen und Umsätze in Schicht und Bericht werden zusätzlich geprüft. Die Tests besitzen keine umschließende Testtransaktion; Service-Commit und anschließendes Lesen sind echte getrennte Vorgänge.

`ReservationLifecycleIntegrationTest` erhält zwei weitere Fälle für vollständige Zahlung und vollständige Split-Zahlung: Ein Fehler beim Operationsbeleg lässt Reservierung CHECKED_IN, Tisch OCCUPIED und Bonpositionen unverändert. Erst der erfolgreiche Retry setzt COMPLETED/FREE; ein weiterer Retry bleibt wirkungslos.

Bestehende Tests für frühe Nachbestellfehler, unzureichenden Bestand, ungültige Splitmengen, Tagesabschluss-Rollback und konkurrierende Buchungen bleiben Bestandteil des vollständigen Gates. Test-Constraints/-Trigger werden in `finally` entfernt und existieren ausschließlich in Wegwerf-Testdatenbanken.

## Prüfgates

| Prüfung | Ergebnis |
| --- | --- |
| Gezielte Billing-/Inventory-Integration | **PASS** – 30 Tests, keine Fehler/Fehlschläge/übersprungen |
| Backend Build / vollständige Tests | **PASS** – `clean verify`, 252 Tests, keine Fehler/Fehlschläge/übersprungen; JAR und JaCoCo-Report erzeugt |
| Frontend Lint | **PASS** |
| Frontend Unit / Security | **PASS** – 15 Unit- und 3 Security-Tests |
| Generierte API-Typen | **PASS** – `npm run api:check`; API unverändert |
| Frontend Build | **PASS** – Produktionsbuild einschließlich TypeScript |
| Critical E2E | **PASS** – 18 Tests gegen Produktionsbuild mit festen Testdaten und `--retries=0` |

Der erste lokale Teststart scheiterte vor Ausführung an der Docker-Desktop-Socket-Einbindung von Testcontainers/Ryuk. Mit `DOCKER_HOST=unix:///home/tom/.docker/desktop/docker.sock` und `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` läuft die isolierte Testdatenbank; Ryuk bleibt aktiviert. Kein Produktfehler und kein Überspringen von Tests.

Im ersten vollständigen Lauf unterschieden zwei neue Testvergleiche ausschließlich die BigDecimal-Skalierung (`500` gegenüber `500.0000`). Der Vergleich verwendet jetzt vor und nach dem Fehler frisch persistierte Daten. Der anschließende vollständige Lauf ist grün.

Temporäre Nachweise: `/tmp/fasswerk-phase3-targeted.log`, `/tmp/fasswerk-phase3-backend.log`, `/tmp/fasswerk-phase3-e2e.log`. Die Backend-Kopie unter `/tmp/fasswerk-phase3/backend` vermeidet konkurrierende IDE-Ausgaben im Buildverzeichnis. Die laufende Docker-Vorschau und deren Daten bleiben unverändert. Diese Ergebnisse sind lokale Prüfungen, kein behaupteter GitHub-CI-Lauf.

## Grenzen und nächster Schritt

Der Nachweis betrifft die vorhandenen TABLE- und Wareneingangsprozesse. DIRECT-Verkauf, externe Kartenzahlung, unveränderliche Schichtabschlüsse und vollständige Korrekturhistorie sind damit nicht eingeführt. Ein nicht zugestelltes HTTP-Ergebnis nach erfolgreichem Commit wird weiterhin durch die Idempotenz-/Recovery-Mechanismen aus Phase 1 behandelt. SQL-Fehlerinjektionen sind kein Nachweis für produktive Backup-Ziele, Datenbank-Failover oder reale Altdatenmigration.

Nächster Schritt: Phase 4 – Inventory Concurrency, insbesondere konkurrierende Verkäufe und Bestandskorrekturen. Keine pauschale Produktionsfreigabe.
