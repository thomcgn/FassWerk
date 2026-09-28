# PHASE 2 REPORT – Concurrent Editing / Lost Updates

Stand: 28.09.2026. Ausgangspunkt: `af1aab0`, abgeschlossene [Phase 1](PHASE_1_REPORT.md). Grundlage: [Production-Roadmap](../FASSWERK_PRODUCTION_READY_ROADMAP.md) und Befunde R01/R03 der [Baseline](PRODUCTION_READINESS_BASELINE.md).

## Ergebnis und Verhalten

Veraltete Bestands- und Schichtformulare dürfen neuere Änderungen nicht mehr überschreiben. Die API liefert eine `revision`; schreibende Formulare senden die zuvor gelesene `expectedRevision`. Ein Konflikt ergibt HTTP 409 und keine Teiländerung.

Stammdatenänderungen übernehmen keine absolute Menge aus der gerundeten Packungsanzeige mehr. Ein Lagerartikel mit 9,999 Litern bei 10 Litern je Gebinde behält daher auch nach einer Umbenennung/Verknüpfung exakt 9,999 Liter. Eine andere Packungszahl im PUT wird abgewiesen; Bestandskorrekturen erfolgen über den vorhandenen Anpassungsendpoint mit Begründung und Bewegung. Einheitenwechsel werden ebenfalls abgewiesen, statt den vorhandenen Bestand umzudeuten. Geänderte Gebindegröße bei gleicher Einheit verändert lediglich die daraus berechnete Packungsanzeige, nicht die physische Menge.

Bei einem Schichtkonflikt bleiben Kassenangaben und Mitarbeiterentwurf im Browser erhalten. Der aktuelle Serverstand wird separat geladen und angezeigt. Erst „Serverstand übernehmen und Entwurf verwerfen“ ersetzt den Entwurf ausdrücklich. Anschließend können Änderungen auf Basis des aktuellen Stands neu eingetragen werden. Solange ein Konflikt ungeklärt ist, bleibt Speichern gesperrt.

## Umsetzung

| Bereich | Mechanismus |
| --- | --- |
| InventoryItem | JPA `@Version` auf `revision`; sämtliche ORM-Schreibpfade, einschließlich Verbrauch, Storno, Lieferung, Katalogentkopplung und Nachbestellberechnung, nehmen am Versionsschutz teil. Bestehende Zeilensperren bleiben erhalten. |
| Inventar-PUT | Versionsvergleich nach Zeilensperre; Stammdaten und initiale Bestandsanlage getrennt. `saveAndFlush` liefert die aktualisierte Revision in der Antwort. |
| Inventar-DELETE | Benötigt die gelesene Revision als Queryparameter. Ein veralteter oder fehlender Wert deaktiviert den Artikel nicht. |
| Bestandsanpassung | Bleibt eine gesperrte Delta-Buchung mit Bewegungsbeleg und optionalem Idempotenzschlüssel. Die Antwort enthält nach Flush die aktuelle Revision. |
| ShiftSettlement | Revision wird bei jedem vollständigen Speichern unter dem vorhandenen PostgreSQL-BookingMutationLock erhöht, auch bei ausschließlich geänderten Mitarbeiterzeilen. Der Lock schützt auch die erstmalige Anlage, wenn noch keine Zeile für eine Zeilensperre existiert. |
| Optimistische DB-Konflikte | GlobalExceptionHandler übersetzt Spring-OptimisticLockingFailureException in den bestehenden sicheren 409-Fehlervertrag. |
| Inventaroberfläche | Sendet Revision bei Verknüpfung und Löschung. Bei 409 wird die Liste neu geladen; die gewählte Verknüpfung bleibt erhalten und muss erneut bestätigt werden. Fehlermeldungen beim Löschen sind auch im Dialog sichtbar. |
| Schichtoberfläche | Separater Serverstand für Konfliktvergleich, Entwurfserhalt, Schutz gegen parallele Speicherklicks und gegen verspätete Leseantworten eines vorher gewählten Datums. Während des Speicherns sind Eingaben gesperrt. |

Die Schichtrevision ist bewusst eine explizite Aggregatrevision unter einem DB-Lock: Änderungen an der inversen Mitarbeiterkollektion müssen auch dann eine neue Revision erzeugen, wenn die skalaren Kassenfelder gleich bleiben. Dafür wird nicht auf implizite JPA-Versionierung von Kindänderungen vertraut.

Relevante Dateien: `InventoryService`, `InventoryItem`, `InventoryController`, `ShiftSettlementService`, `ShiftSettlement`, deren Request-/Response-DTOs, `GlobalExceptionHandler`, die beiden Frontend-Clients sowie die Inventory-DELETE-BFF-Route. OpenAPI und generierte Frontend-Typen werden gemeinsam fortgeschrieben.

## Prüfung der übrigen Phase-2-Bereiche

| Bereich | Befund / Entscheidung |
| --- | --- |
| Table Order | Kein Endpoint zum Ersetzen eines vollständigen veralteten Bons. Mutationen verwenden fachliche Befehle unter dem gemeinsamen PostgreSQL-Buchungslock. Die vorhandene Sperre wird wiederverwendet. |
| Order Items | Hinzufügen/Storno sind serverseitige Mengenänderungen; Split prüft verfügbare Mengen innerhalb derselben gesperrten Transaktion. Kein vom Browser zurückgeschriebener absoluter Positionsbestand. |
| Payment State | Zustandsprüfungen und die Operationsbelege aus Phase 1 schützen Wiederholungen einschließlich späterer Zustandswechsel. Zwei unterschiedliche Schlüssel bleiben zwei fachliche Befehle; eine gemeinsame Benutzerabsicht über Geräte hinweg wird nicht geraten. |
| Inventory | Zeilensperren allein schützten nicht vor alten Formularwerten; Revision plus bestandserhaltender PUT beheben diese Lücke. |
| Shift State | Der bisherige eindeutige Datumsschlüssel schützte nur vor doppelten Zeilen. Revision und transaktionaler Lock verhindern jetzt das stille Ersetzen einer vorhandenen Schicht und ihrer Mitarbeiter. |

Bereits vorhandene Tests für konkurrierendes Bonöffnen, Teilzahlung, Check-in, Delta-Bestand und Rollback werden erneut ausgeführt. Eine allgemeine Versionsspalte in BaseEntity oder ein neuer Ersatz für den vorhandenen Buchungslock ist hierfür nicht erforderlich.

## Migration und Kompatibilität

**V30 `inventory_and_shift_revisions`** ergänzt jeweils `revision bigint not null default 0` in `inventory_items` und `shift_settlements`. Vorhandene Mengen, Buchungen, Schichtinhalte und Sessions werden nicht verändert. Frühere Migrationen bleiben unverändert.

API:

- `GET /api/inventory` und Inventar-Schreibantworten enthalten `revision`.
- `PUT /api/inventory/{id}` erwartet `expectedRevision` im JSON. Ohne passende Revision erfolgt 409. POST für neue Artikel benötigt diesen Wert nicht.
- `DELETE /api/inventory/{id}?expectedRevision=…` schützt auch eine zwischenzeitlich geänderte Verknüpfung oder Menge.
- Schichtantworten enthalten `revision`. `PUT /api/shift-settlements/{date}` verlangt `expectedRevision`; fehlende/negative Werte ergeben 400, ein veralteter Wert 409. GET einer noch nicht gespeicherten Schicht liefert Revision 0.

**Backend und Frontend gemeinsam aktualisieren; kein Mischbetrieb alter und neuer Backend-Schreiber.** Alte Backend-Versionen erhöhen die Revision nicht und könnten den Schutz umgehen. Auch manuelle SQL-Korrekturen liegen außerhalb der ORM-Versionierung und benötigen einen geregelten Wartungsablauf. Alte Frontends ohne Revisionsfelder können bestehende Datensätze nicht weiter unverändert speichern.

## Reproduktion und Abnahme

Am unveränderten Ausgangscode wurden drei Fehler per HTTP gegen PostgreSQL nachgewiesen:

1. Verbrauch von 10 auf 9 Liter, danach altes Formular: PUT antwortete 200 und schrieb wieder 10 Liter; erwartet wird 409.
2. Stammdaten-Roundtrip eines Artikels mit 9,999 Litern: danach 10,0000 Liter; erwartet wird unverändert 9,999.
3. Zweites Schichtformular nach erster Speicherung: 200 mit überschriebenen Kassenangaben; erwartet wird 409.

Nachweis: `/tmp/fasswerk-phase2-red.log`. Der Testclient verwendet wie die Oberfläche den vorhandenen Inventarlisten-GET.

Sechs neue Backend-Regressionen in `ApiContractIntegrationTest` prüfen diese Fälle sowie:

- Zwei konkurrierende Inventar-Clients: genau ein 200 und ein 409, Gewinner bleibt erhalten, physische Menge bleibt gleich.
- Zwei konkurrierende Schicht-Clients: sowohl erstmalige Anlage als auch anschließende reine Mitarbeiteränderungen; genau ein Gewinner und eine Revisionserhöhung, keine vermischten Mitarbeiterzeilen.
- Absolute Bestandsänderung per Stammdaten-PUT sowie fehlende/veraltete Revision beim Löschen: keine ungewollte Bestandsänderung/Deaktivierung.

Die reale Fullstack-Abnahme wurde um zwei BFF-Geräte erweitert: Gerät B bucht Verbrauch, Gerät A versucht eine alte Verknüpfung; anschließend explizite Wiederholung nach aktuellem Lesen. Zwei Browser bearbeiten außerdem dieselbe Schicht, prüfen den erhaltenen Entwurf und übernehmen den Serverstand ausdrücklich. Schichtrevision und Kassenstand werden nach App-Neustart erneut geprüft. Der Restore-Vergleich umfasst jetzt auch Schicht- und Mitarbeiterdaten; Inventarrevisionen sind Bestandteil der vollständigen Inventarzeilen.

## Prüfgates

| Prüfung | Ergebnis |
| --- | --- |
| Backend Build / Tests | **PASS** – `clean verify`, 241 Tests, keine Fehler/Fehlschläge/übersprungen; isolierte Build-Kopie mit PostgreSQL/Testcontainers |
| Frontend Unit / Security | **PASS** – 15 Unit- und 3 Security-Tests |
| Frontend Lint | **PASS** |
| OpenAPI | **PASS** – zwei identische Exporte auf verschiedenen Ports |
| Generierte Typen / TypeScript / Frontend Build | **PASS** – `api:generate`, `api:check`, `tsc --noEmit --noUnusedLocals --noUnusedParameters`, `npm run build` |
| Critical E2E | **PASS** – 18 Tests gegen Produktionsbuild, `--retries=0` |
| Reale Fullstack-/Restore-Abnahme | **PASS** – zwei BFFs, Inventar-/Schichtkonflikte mit echter Oberfläche, Phase-1-Replays, App-Neustart und exakter PostgreSQL-Dump/Restore-Vergleich. Synthetischer Restore inklusive Vergleich: 6 Sekunden; keine Produktions-RPO/RTO-Aussage |

Lokale temporäre Logs: `/tmp/fasswerk-phase2-backend.log`, `/tmp/fasswerk-phase2-openapi.log`, `/tmp/fasswerk-phase2-e2e.log`, `/tmp/fasswerk-phase2-fullstack.log`. Dies sind lokale Prüfungen, kein behaupteter neuer GitHub-CI-Lauf.

**Phasenergebnis: PASS.**

## Verbleibende Grenzen und nächster Schritt

R01 und R03 sind durch die beschriebenen Schreibpfade und Regressionen adressiert. Verknüpfungsänderungen dürfen keine Bestandskorrektur ersetzen. Größere Einheitenumstellungen und die fachliche Preisbindung offener Bons benötigen gesonderte Regeln.

Ein Schichtentwurf bleibt während der Konfliktauflösung im geöffneten Browser erhalten; eine dauerhafte Entwurfsablage über Reload/Gerätewechsel gehört weiterhin zu Phase 5/16. Historische Schichtumsätze werden wie bisher aus bezahlten Bons berechnet; ein eingefrorener Kassenabschluss wurde nicht eingeführt. Öffentliche Reservierungsabsicherung, echte Produktionsmigration, Backup-Ziele und Alarmzustellung bleiben gemäß Baseline offen. Keine pauschale Produktionsfreigabe.

Nächster Schritt: Phase 3 – Atomare Geschäftsprozesse. Dabei die bestehende Buchungsregel beachten: Bestand/Absatz beim Hinzufügen einer Position, Umsatz beim Bezahlen; keine zweite Bestandsbuchung beim Abschluss.
