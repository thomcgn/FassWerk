# PHASE 7 REPORT – Direktverkauf / Barverkauf

Stand: 29.09.2026. Aufbauend auf [Phase 6](PHASE_6_REPORT.md), gemäß [Roadmap](../FASSWERK_PRODUCTION_READY_ROADMAP.md).

Status: **PASS**.

## Umsetzung

`/direct-sales` bietet einen Warenkorb ohne Tisch: Getränke auswählen, Bar/Karte wählen und den Zahlungseingang bestätigen. Der Server erstellt genau einen bezahlten, geschlossenen DIRECT-Bon. Kein künstlicher Tisch, keine Reservierung und keine Änderung der Belegung.

`POST /api/table-orders/direct` benötigt einen Idempotenzschlüssel. Der normalisierte Fingerprint umfasst Zahlungsart, Varianten, Mengen und bestätigte Einzelpreise. Ein Replay liefert den ursprünglichen Bon; eine andere Payload unter demselben Schlüssel wird abgewiesen. Auch parallele Anfragen serialisieren über den bestehenden PostgreSQL-Buchungslock. Der Browser speichert einen gesendeten Auftrag vor dem Request und bietet bei unklarem Ergebnis dieselbe Aktion nach Neuladen erneut an.

Die äußere Transaktion verwendet die bestehende Positions- und Abschlusslogik: Preisauflösung, Bestandsabzug, Sales Tracking, Nachbestellberechnung, Umsatz, Schicht und Audit. Preisabweichungen oder fehlender Bestand rollen den gesamten Warenkorb zurück. Das Frontend übernimmt neue Preise erst auf ausdrücklichen Klick nach Katalogaktualisierung. Bereits abgeschlossene Bons behalten ihre Preise.

DIRECT-Bons erscheinen im Archiv als „Barverkauf“. Archivabfragen verwenden einen LEFT JOIN, damit Bons ohne Tisch nicht versehentlich herausgefiltert werden. Die Antwort enthält `saleType`, `paymentMethod` und einen bei DIRECT leeren `tableId`; OpenAPI und generierte Frontendtypen werden gemeinsam aktualisiert.

Kartenerlöse fließen in Tagesumsatz und Schichtumsatz ein, aber nicht in den erwarteten Bargeldbestand. Das gilt sowohl für neue als auch gespeicherte Schichtabrechnungen. Historische Tischbons ohne Zahlungsart behalten ihre bisherige Bargeldbehandlung; eine Zahlungsart wird nicht nachträglich erfunden.

## Migration

V32 ergänzt Klassifikation und Zahlungsart und erlaubt einen fehlenden Tisch ausschließlich für DIRECT. Alte Bons werden TABLE; ihre Zahlungsart bleibt unbekannt (`null`). Ein verzögerter Constraint-Trigger erzwingt bei Transaktionsabschluss einen bezahlten, geschlossenen DIRECT-Bon mit Abschlussdatum. Innerhalb der Transaktion kann die vorhandene Bonlogik weiterverwendet werden.

Upgrade V31→V32, vollständiger Neuaufbau und bisherige Upgradepfade werden gegen PostgreSQL geprüft. Die laufende Docker-Vorschau wird durch diese Phase nicht umgestellt. Beim nächsten Backendstart mit dem neuen Artefakt führt Flyway V32 aus; ein Rückwechsel auf den alten Anwendungscode ist nach neuen Direktbons wegen des nun möglichen leeren Tischbezugs nicht als kompatibles Rollback anzusehen.

## Prüfungen

- CASH/CARD: geschlossener Bon, genau ein Bestandsabzug, gemeinsame Tages- und Schichtumsätze, korrekte Bargeldberechnung, Sales Tracking, Archiv und Audit.
- Identische parallele Requests: ein Bon; abweichende Wiederholung: Konflikt.
- Preis-/Bestandsfehler im zweiten Artikel sowie spätes Scheitern des Operationsbelegs: Rollback ohne Teilbon oder Auditrest.
- Bezahlte Direktbons können über die bestehenden Endpunkte nicht umgebucht, unbezahlt gesetzt oder wieder geöffnet werden.
- HTTP: ADMIN/BARCHEF/STAFF erlaubt; anonym abgewiesen; ungültige Menge vor der Buchung abgewiesen.
- V32 erhält vorhandene offene und bezahlte Tischbons; die Datenbank weist unfertige DIRECT-Bons zurück.
- Reale Browserabnahme: Kartenzahlung, verlorene Antwort nach Commit, Neuladen, Replay mit demselben Schlüssel, exakter Bestand und unveränderte Tische.

## Befunde während der Abnahme

Der zusätzliche Navigationspunkt deckte einen Überlauffehler auf: Die rechts ausgerichtete Flex-Navigation schob bei knapper Breite den ersten Link in einen nicht erreichbaren Bereich. Der vorhandene Inventory-E2E reproduzierte dies durch abgefangene Klicks. Die Navigation beginnt jetzt am scrollbaren Anfang; mobil bleiben neun Einträge in zwei Reihen. Dies ist eine kleine notwendige Korrektur, kein vorgezogener Rehaul.

Ein wiederholter Lintlauf bezog zuvor generierte Playwright-Trace-Assets ein. `playwright-report` und `test-results` sind jetzt vom Quellcode-Lint ausgeschlossen. Der HTTP-Rollentest setzt außerdem seinen eigenen Preis explizit, statt unbeabsichtigt einen globalen Standardpreis aus anderen Tests zu verwenden.

## Gates

- Backend: **PASS**, vollständiges `verify` nach sauberem isoliertem Aufbau; 278 Tests, keine Fehler oder übersprungenen Tests. Zusätzlich nach der letzten `@Size(min=1)`-Vertragsannotation: 44 HTTP-/OpenAPI-Tests grün.
- Frontend Unit/Security: **PASS**, 16 Unit- und 3 Securitytests.
- Reale Fullstack-Abnahme: **PASS**, einschließlich neuem Direktverkauf, Antwortverlust/Replay, Recovery nach Neustart und exakt verglichenem PostgreSQL-Dump/Restore (11 Sekunden im synthetischen Test).
- Frontend Lint, Produktionsbuild einschließlich TypeScript und Critical E2E: **PASS**, 18 E2E-Tests ohne Retries nach der Navigationskorrektur.
- OpenAPI: **PASS**, aus isoliertem Backend exportiert, zwei identische Exporte verglichen; Frontendtypen neu generiert und `api:check` grün.

Logs: `/tmp/fasswerk-phase7-backend.log`, `/tmp/fasswerk-phase7-contract-tests.log`, `/tmp/fasswerk-phase7-frontend.log`, `/tmp/fasswerk-phase7-frontend-final.log`, `/tmp/fasswerk-phase7-fullstack.log`. Lokale Abnahme, kein behaupteter CI-Lauf. Tests verwenden ausschließlich isolierte Datenbanken.

## Grenzen und nächste Phase

Die Zahlungsart dokumentiert eine bereits erhaltene Zahlung. Kein Karten-Terminal oder Zahlungsanbieter wird angebunden; keine automatische Abbuchung oder Erstattung. Ein ungesendeter Warenkorb bleibt ein flüchtiger Entwurf ohne Bestandsreservierung. Bereits gesendete unklare Abschlüsse sind über den vorhandenen Session-Speicher wiederholbar. Die Anwendung beansprucht damit keine geräteübergreifende Wiederherstellung eines noch ungesendeten Warenkorbs.

Nächster Schritt: Phase 8 – Verkauf und Belegung trennen. Ein optionaler Sitzplatz nach einem Direktverkauf wird noch nicht hinzugefügt.

**Nutzerentscheidung:** Der umfassende UI- und Flow-Rehaul kommt ganz zum Schluss. Die Roadmap hält dies ausdrücklich fest und erweitert Phase 17 entsprechend. Diese Phase ergänzt nur den notwendigen Barverkaufsflow.
