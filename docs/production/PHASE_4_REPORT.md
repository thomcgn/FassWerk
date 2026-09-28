# PHASE 4 REPORT – Inventory Concurrency

Stand: 28.09.2026. Aufbauend auf [Phase 3](PHASE_3_REPORT.md); Grundlage: [Production-Roadmap](../FASSWERK_PRODUCTION_READY_ROADMAP.md).

## Ergebnis

Status: **PASS**.

In den geprüften Abläufen wurde kein zusätzlicher Bestands-Race-Fehler reproduziert.

Phase 4 ergänzt acht Konkurrenzfälle gegen echtes PostgreSQL. Die vorhandenen Sperren werden wiederverwendet; Produktcode, API und Migrationen bleiben unverändert. Die neuen Fälle prüfen Endbestände, Bewegungsanzahl/-summe, Bonpositionen, Absatz und Idempotenzbelege nach konkurrierenden Service-Aufrufen.

## Vorhandene Schutzmechanismen

- `TableOrderService` hält für Buchung, Storno und Zahlung den transaktionalen PostgreSQL-Advisory-Lock aus `BookingMutationLock`. Zwei Verkäufe laufen daher als getrennte Requests, ihre kritischen DB-Abschnitte werden serialisiert. Die Sperre ist datenbankweit und nicht an eine Java-Instanz gebunden.
- `InventoryService` liest schreibend mit PESSIMISTIC_WRITE: direkt per Artikel-ID, per Variante oder für den eindeutigen aktiven Getränke-Fallback. Alle Varianten eines solchen Fallbacks ändern dieselbe gesperrte Lagerzeile.
- Manuelle Delta-Korrekturen nutzen die Artikelzeilensperre. Sie benötigen den globalen Buchungslock nicht; die Zeilensperre schützt sie auch gegenüber Verkäufen.
- Die Verfügbarkeitsprüfung geschieht nach Erwerb der Bestandszeilensperre. Negative Mengen werden fachlich abgewiesen; zusätzlich existiert der DB-Check `ck_inventory_total_nonnegative` aus V25. Dessen NOT VALID schützt neue Änderungen, ist aber keine nachträgliche Validierung sämtlicher Altdaten.
- Bestandsänderung und Bewegung gehören zur selben Transaktion. Idempotenzschlüssel besitzen eindeutige DB-Indizes; der Replay wird unter der jeweiligen Sperre geprüft.
- `InventoryItem.revision` aus Phase 2 ergänzt den Konfliktschutz. Veraltete absolute Stammdatenformulare dürfen keine neueren Bestände ersetzen. Gegen konkurrierende Deltas werden weiterhin die vorhandenen Zeilensperren verwendet.

Kein zusätzlicher Java-Lock und keine zweite, konkurrierende Buchungsimplementierung erforderlich. Der globale Buchungslock ist konservativ und serialisiert auch unterschiedliche Artikel; eine spätere Optimierung erfordert Messungen und eine vollständige Analyse der Sperrreihenfolge.

## Neue Nachweise

Neue Klasse: `backend/src/test/java/org/thomcgn/backend/inventory/service/InventoryConcurrencyIntegrationTest.java`.

| Fall | Erwartung |
| --- | --- |
| Zwei Bons, dieselbe Variante | 10 Liter − 2 − 3 = 5; zwei Bewegungen und zwei Operationsbelege; Tagesabsatz 5.000 ml. Anschließende parallele Zahlung bucht keinen weiteren Verbrauch. |
| Zwei Varianten, gemeinsamer Getränke-Fallback | Zwei 1-Liter-Portionen und sechs 0,5-Liter-Portionen verbrauchen zusammen 5 Liter aus derselben Lagerzeile. Beide Bonmengen bleiben erhalten. |
| Zwei gleichzeitige Requests mit demselben Add-Schlüssel | Ein Verbrauch von 2 Litern, eine Bewegung, eine Bonwirkung und ein Operationsbeleg; weiterer Retry bleibt wirkungslos. |
| Zwei Verkäufe von je 6 Litern bei 10 Litern Bestand | Genau ein Erfolg und ein fachlicher Konflikt. Restbestand 4 Liter, keine Teilposition/Absatzbuchung des Verlierers. Nach Auffüllen um 2 Liter kann der abgewiesene Befehl mit demselben Schlüssel bis exakt 0 buchen; ein Retry unterschreitet 0 nicht. |
| Verkauf und manuelle Bestandskorrektur | 10 − 2 Liter Verkauf − 3 Liter Korrektur = 5; beide Bewegungen bleiben erhalten, nur der Verkauf erhöht den Absatz. |
| Gleichzeitige identische Korrektur | Genau eine Bewegung und eine Revisionserhöhung. |
| Storno und Verkauf auf einem anderen Bon | Nach ursprünglichem Verbrauch von 2 Litern: +1 Liter Storno und −3 Liter Verkauf = 6 Liter Restbestand; Nettoabsatz 4 Portionen. |
| Fehlerhafter und erfolgreicher Verkauf | Echter Constraint-Fehler beim Operationsbeleg eines Requests rollt ausschließlich diesen Vorgang zurück. Der andere Verkauf bleibt mit Bestand, Absatz, Bewegung und Nachbestellberechnung erhalten. Der zuvor gescheiterte Schlüssel kann anschließend erfolgreich buchen. |

Die Tests starten zwei Executor-Aufgaben an einer gemeinsamen Barriere. Jeder Aufruf verwendet seine eigene Spring-Service-Transaktion; die Tests haben keine umschließende Transaktion. Wartezeiten sind begrenzt, Executor-Abschluss wird geprüft. Nach beiden Ergebnissen werden persistierte Daten direkt per JDBC gelesen. Es gibt keine gemockten Inventarantworten oder simulierten Sperren.

Bestehende Nachweise bleiben relevant: parallele Bestandskorrekturen bei unzureichender Menge (`ConcurrentWritesCharacterizationTest`), parallele Lieferungsannahme und Wareneingangs-Replay, Milliliterpräzision, Bestands-/Nachbestell-Rollback sowie Commit-Fehlerinjektionen aus Phase 3. Der Schutz veralteter Formulare bleibt in den HTTP-Vertragstests aus Phase 2 geprüft.

## Prüfgates

| Prüfung | Ergebnis |
| --- | --- |
| Backend Build / Tests | **PASS** – `clean verify`: 260 Tests, keine Fehler/Fehlschläge/übersprungen; davon acht neue Konkurrenzfälle. JAR und JaCoCo-Report erzeugt |
| Frontend Lint / Unit / Security | **PASS** – Lint, 15 Unit- und 3 Security-Tests |
| API-Typen / Frontend Build | **PASS** – `api:check`, Produktionsbuild einschließlich TypeScript |
| Critical E2E | **PASS** – 18 Tests gegen Produktionsbuild, feste Testdaten, `--retries=0` |

Backend: isolierte Kopie unter `/tmp/fasswerk-phase4/backend`, `clean verify` mit PostgreSQL/Testcontainers und aktiviertem Ryuk. Logs: `/tmp/fasswerk-phase4-backend.log` und `/tmp/fasswerk-phase4-frontend.log`. Die laufende Docker-Vorschau und ihre Datenbank werden nicht für Tests verwendet. Diese Abnahme ist lokal, kein behaupteter CI-Lauf.

## Grenzen und nächster Schritt

Die Tests verwenden konkurrierende DB-Verbindungen in einer Spring-Anwendung, keinen Lasttest mit mehreren Backend-Prozessen. Der instanzübergreifende Schutz ergibt sich aus PostgreSQL-Sperren und DB-Constraints; Durchsatz und Wartezeiten unter realer Gastro-Last sind hier nicht gemessen. HTTP-Timeout-Recovery und stabile Browser-Schlüssel sind weiterhin durch Phase 1 abgedeckt. Direkte SQL-Schreiber außerhalb der Services und unbekannte Altdaten erfordern gesonderte Prüfung.

Nächster Schritt: Phase 5 – Server State & Recovery. Keine pauschale Produktionsfreigabe.
