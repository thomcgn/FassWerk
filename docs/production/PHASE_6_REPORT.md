# PHASE 6 REPORT – Korrekturen, Storno und Audit

Stand: 28./29.09.2026. Aufbauend auf [Phase 5](PHASE_5_REPORT.md); Grundlage: [Production-Roadmap](../FASSWERK_PRODUCTION_READY_ROADMAP.md).

## Ergebnis

Status: **PASS**.

Die bestätigte Preisregel lautet: Bereits gebuchte Positionen behalten ihren Einzelpreis; neue Bestellungen verwenden den aktuellen Katalogpreis. Ein anderer Preis erzeugt eine eigene Position desselben Getränks. Varianten- und Standardpreisänderungen schreiben bestehende offene Positionen nicht mehr um. Teilzahlung und Storno beziehen sich weiterhin auf die konkrete Positions-ID und deren gebuchten Preis.

Zusätzlich entsteht ein transaktionales Auditjournal für vorhandene finanzielle Änderungswege. Es ergänzt die bestehenden Bestandsbewegungen und Idempotenzbelege. Es ersetzt weder eine Zahlungsanbieterintegration noch führt es nachträglich neue Refund-/Stornoregeln für bezahlte Bons ein.

## Reale Befunde und Reproduktion

- Es existierte kein durchgängiges Auditjournal mit Akteur und Vorher-/Nachher-Zustand. `createdAt`/`updatedAt` und BillingOperation allein dokumentierten Preis- und Schichtänderungen nicht ausreichend.
- Varianten- und Standardpreisänderungen berechneten offene Bonpositionen neu. Zwei neue Tests am unveränderten Produktcode zeigen jeweils: zwei gebuchte Portionen zu 3 € wurden nach Preisänderung zu 4 € als 8 € statt 6 € geführt. Nachweis: `/tmp/fasswerk-phase6-red.log`.
- Preisupdates verwendeten bisher nicht den gemeinsamen Buchungslock. Nun serialisieren Katalog-Preismutationen mit Buchungen und Abschlüssen. Die Preisbindung verhindert unabhängig davon eine nachträgliche Neubepreisung bestehender Positionen.

## Umsetzung

### Preisbindung

`MenuService` aktualisiert Katalog-/Standardpreise ohne Umschreiben von Bonpositionen. Preis- und Variantenmutationen verwenden den vorhandenen PostgreSQL-BookingMutationLock. `TableOrderService.addItem` sucht eine zusammenfassbare Position jetzt zusätzlich anhand von `unitPrice`. Bereits gebuchte Mengen bleiben dadurch zum ursprünglichen Preis erhalten. API und DTOs bleiben unverändert; eine Bestellung kann mehrere Positionen derselben Variante mit verschiedenen Preisen enthalten.

Die Tests prüfen individuelle und Standardpreise sowie die Teilzahlung einer alten Preisposition. Die reale Browser-Abnahme zeigt nach einer Preisänderung zwei Positionen zu 3 € und 4 € und einen Gesamtbetrag von 7 €; auch der bereits bezahlte Teilbon bleibt unverändert.

### Auditjournal

V31 ergänzt `business_audit_events` mit:

- Zeitstempel, Transaktions-ID und optionaler Request-ID;
- Akteur als `user:<ID>`, `system:internal` oder ausdrücklich `system:unattributed`;
- Aktion, Entity-Typ, Entity-ID und Begründung;
- JSON-Metadaten mit tatsächlichem Vorher-/Nachher-Zustand.

PostgreSQL-Trigger schreiben das Journal in derselben Transaktion wie die fachliche Änderung. Ein Auditfehler bricht deshalb auch die Buchung ab. Echte Replays ohne Datenänderung erzeugen keine weitere Historie. Zusammengehörige Teiländerungen sind über `transaction_id` korrelierbar.

| Änderungsweg | Auditquelle |
| --- | --- |
| Zahlung / Abschluss / unbezahltes Archiv / Wiederöffnung | `table_orders`, Status-/Paid-/Zeit-/Datumsänderung |
| Split Payment | neuer bezahlter Teilbon, Positionsverschiebungen und Operationsbeleg |
| Storno offener Positionen | Mengenreduktion/Löschung plus Bestands-Gegenbewegung und Operationsbeleg |
| Bestandskorrektur / Lieferung / Verbrauch | `inventory_movements`, inklusive vorhandener Begründung und Bezug |
| Preisänderung | Preis-/Volumen-/Standardpreis-Projektion von `drink_variants` und `volume_prices` |
| Schichtänderung | `shift_settlements` und `shift_worker_entries`, einschließlich vorheriger Kassenwerte und Mitarbeiterzeiten/-sätze |
| Manueller Geschäftstagsabschluss | `business_day_close_operations` |

`ITEM_REDUCTION` bezeichnet absichtlich eine technische Mengenreduktion: Erst zusammen mit Bewegung/Operationsbeleg ist erkennbar, ob Storno oder Verschiebung in einen Teilbon vorliegt. Die Begründung übernimmt bei Bestandsbewegungen den vorhandenen fachlichen Text; sonst kennzeichnet sie den ausgeführten Befehl (`Committed <Aktion>`). Dies ist keine erfundene handschriftliche Benutzerbegründung. Eine Pflicht zur freien Begründung jeder Preis-/Schichtkorrektur wurde nicht neu eingeführt.

`AuditContext` löst den authentifizierten Benutzer serverseitig in eine Benutzer-ID auf. Actor und Request-ID werden mit PostgreSQL `set_config(..., true)` nur für die aktuelle Transaktion gesetzt; Pool-Verbindungen dürfen keine vorige Benutzeridentität weitertragen. Ohne Authentifizierung werden interne Serviceaufrufe als Systemvorgänge markiert. Direkte SQL-Änderungen ohne Anwendungskontext erhalten ausdrücklich `system:unattributed`.

UPDATE, DELETE und TRUNCATE des Journals werden durch einen DB-Trigger zurückgewiesen. Das Journal besitzt keine löschenden Fremdschlüssel zu Fachobjekten. DB-Eigentümer können Trigger/Schema administrativ verändern; dieser Schutz ist keine Garantie gegen einen privilegierten Datenbankadministrator und keine Zusage eines rechtlich zertifizierten Fiskalarchivs.

Keine Tokens, Passwörter, vollständigen Requests, Gastkontakte oder zusätzlichen E-Mail-Adressen werden aufgenommen. Idempotenzschlüssel und Request-Fingerprints werden nicht nochmals in Auditmetadaten kopiert. Mitarbeitername, Arbeitszeit und Satz bleiben für die Nachvollziehbarkeit ersetzter Schichtzeilen enthalten; Journal und Backups sind deshalb nur berechtigten Betreibern zugänglich zu machen. Es gibt keinen neuen öffentlichen Audit-Endpoint.

## Migration und Betrieb

Neue Migration: `V31__business_audit.sql`. Frühere Migrationen bleiben unverändert. Vorhandene Preise, Mengen und Sessions werden nicht umgeschrieben. Alte Vorgänge erhalten keine erfundenen historischen Auditdaten; die Historie beginnt ab V31. Bereits früher überschriebene Preise werden nicht rekonstruiert.

Backend-Versionen mit alter Neubepreisungslogik nicht parallel weiterbetreiben. Alte Schreiber könnten die neue Preisregel verletzen, obwohl die DB-Trigger ihre Änderungen protokollieren. V31 wird beim regulären Backendstart durch Flyway installiert; die lokale Vorschau wurde nicht umgestellt.

Das Auditjournal gehört in reguläre Backups. Der vorhandene Fullstack-Dump/Restore-Vergleich umfasst jetzt `business_audit_events`. Berechtigte Betreiber können zunächst direkt nach einem Fachobjekt suchen:

```sql
select id, occurred_at, transaction_id, actor, request_id, action, reason, metadata
from business_audit_events
where entity_type = 'table_orders' and entity_id = '123'
order by id;
```

Die Objekt-ID ist durch den konkreten Bon zu ersetzen. Numerische IDs werden als Text gespeichert; Tagesabschluss-Belege verwenden den SHA-256-Hash ihres natürlichen Operationsschlüssels als Audit-ID. Für alle Folgeschritte einer Buchung nach `transaction_id` filtern. Keine regelmäßige Löschung oder Aufbewahrungsdauer wird hier vorgegeben; Datenlebenszyklus und organisatorische Berechtigungen bleiben Teil der späteren Betriebsabnahme.

## Tests und Gates

Neue Nachweise: individuelle/Standardpreisbindung; Akteur-/Requestzuordnung ohne Credentials; kein zusätzlicher Auditeintrag beim Replay; Ablehnung einer Änderung bezahlter Positionen; Auditfehler mit vollständigem Rollback; Schutz gegen UPDATE/DELETE/TRUNCATE; Vorher-/Nachher-Werte für Preis und Schicht; Upgrade V30→V31 ohne erfundene Althistorie. Die Phase-3-Snapshots vergleichen jetzt auch sämtliche Auditzeilen.

| Prüfung | Ergebnis |
| --- | --- |
| Preisregression am alten Produktcode | **REPRODUZIERT** – beide Fälle 8 € statt 6 € |
| Backend Build / Tests / Migrationen | **PASS** – `clean verify`, 267 Tests, keine Fehler/Fehlschläge/übersprungen; Frischaufbau, V30→V31 und frühere Upgradepfade; JAR und JaCoCo-Report |
| Frontend Lint / Unit / Security / API-Typen / Build | **PASS** – Lint, 15 Unit- und 3 Security-Tests, `api:check`, Produktionsbuild einschließlich TypeScript |
| Critical E2E | **PASS** – 18 Tests mit festen Testdaten und `--retries=0` |
| Reale Fullstack-Preisbindung / Recovery / Audit-Restore | **PASS** – sichtbare 3-/4-€-Positionen, unveränderter bezahlter Teilbon, Neustart/Retry und vollständiger Dump-/Restore-Vergleich einschließlich Auditjournal; synthetischer Restore 8 Sekunden |

Temporäre Logs: `/tmp/fasswerk-phase6-red.log`, `/tmp/fasswerk-phase6-backend.log`, `/tmp/fasswerk-phase6-frontend.log`, `/tmp/fasswerk-phase6-fullstack.log`. Isolierte PostgreSQL-Testdatenbanken, keine Änderung an Vorschau- oder Produktionsdaten. Lokale Abnahme, kein behaupteter CI-Lauf.

## Verbleibende Grenzen

Bezahlte Bons können über die vorhandenen Services weiterhin nicht still storniert oder wieder geöffnet werden. Ein neuer Refund-Prozess benötigt eigene fachliche Regeln; er ist nicht Teil dieser Änderung. Die Anwendung besitzt weiterhin keinen separaten unveränderlichen Schichtabschlusszustand: Jede vorhandene Schichtspeicherung und jede Mitarbeiteränderung wird jetzt historisiert. Katalognamen bleiben Anzeigeinformationen aus dem aktuellen Katalog; die neue Preisbindung schützt die gebuchten finanziellen Werte.

Nächster Schritt: Phase 7 – Direktverkauf / Barverkauf. Keine pauschale Produktionsfreigabe.
