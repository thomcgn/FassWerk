# PHASE 5 REPORT – Server State & Recovery

Stand: 28.09.2026. Aufbauend auf [Phase 4](PHASE_4_REPORT.md); Grundlage: [Production-Roadmap](../FASSWERK_PRODUCTION_READY_ROADMAP.md).

## Ergebnis

Status: **PASS**.

Keine zusätzliche Verlustlücke für die geprüften gespeicherten Vorgänge reproduziert; Produktcode und bestehende Persistenzmechanismen bleiben unverändert.

Die vorhandenen angenommenen Geschäftsvorgänge werden serverseitig gespeichert. Diese Phase erweitert die reale Playwright-/BFF-/PostgreSQL-Abnahme um Wiederherstellung ohne Browserdaten, erneuten Login und erneute Prüfung nach Prozessneustart. Es gibt keine Schemaänderung und keinen neuen Browser-Datenspeicher für Geschäftsdaten.

## Serverzustand und lokale Zustände

| Vorgang | Quelle und Wiederherstellung |
| --- | --- |
| Offener Bon | `table_orders` / `table_order_items`. Antippen eines belegten Tisches liest den bestehenden offenen Bon. Die ID, Restpositionen und Beträge stammen vom Server. |
| Unbezahlter archivierter Bon | Persistierter CLOSED-/unbezahlt-Status; Archiv-GET fragt alle unbezahlten Bons ab. Der sessionStorage-Cache ist nur eine Anzeigehilfe bei Ladefehlern und nicht die einzige Kopie. |
| Teilzahlung | Persistierter bezahlter Teilbon, verbleibende Positionen auf dem offenen Ursprungsbon und BillingOperation mit Ergebnisbon-ID. Wiederholung nutzt das bereits gespeicherte Ergebnis. |
| Reservierung / Belegung | Persistierte Reservierung, Tischzuordnung und Bonstatus; erneuter authentifizierter Abruf rekonstruiert CHECKED_IN und OCCUPIED. |
| Gespeicherte Schicht | `shift_settlements` / `shift_worker_entries`; Datum auswählen lädt Kassenwerte, Mitarbeiter und Arbeitszeiten erneut. Umsatz wird aus serverseitig bezahlten Bons abgeleitet. |
| Unklare mutierende Antwort | `PendingMutation` hält Schlüssel und exakten Befehl vor Versand in sessionStorage fest. Reload und erneuter Login im selben Tab behalten den Wiederholungsauftrag; die fachliche Wirkung und der Idempotenzbeleg liegen nach Commit in PostgreSQL. |

Ein frisches Gerät benötigt diese lokalen Caches/Schlüssel nicht, um bereits gespeicherte Vorgänge anzuzeigen. Eine noch nicht bestätigte Teilzahlungs-Auswahl ist ein Formulareintrag, keine separat verbuchte Zahlung. Ungespeicherte Schichtformulare sind weiterhin Entwürfe: Sie werden bei Reload nicht automatisch serverseitig gespeichert. Die Abnahme behauptet weder Hintergrundspeicherung ungesendeter Eingaben noch einen geräteübergreifenden Posteingang für ausstehende Browserbefehle.

## Neue reale Recovery-Abnahme

Erweiterung von `frontend/scripts/fullstack-acceptance.mjs`, bereits durch `scripts/fullstack-acceptance.sh` im CI-Job `frontend-e2e-critical` ausgeführt:

1. Die tatsächliche Teilzahlungsaktion wird auf dem Backend verarbeitet; nur die Antwort an den Browser wird unterbrochen. Anschließend werden Auth-Cookies entfernt, derselbe Tab meldet sich erneut an und wiederholt die gespeicherte Aktion. Der ursprüngliche Idempotenzschlüssel bleibt erhalten; es entsteht kein zweiter Teilbon.
2. Eine weitere unbezahlte Schuld mit einer realen Position wird archiviert. Eine Schicht mit Kassenwerten und Mitarbeiterzeit wird gespeichert.
3. Ein vollständig neuer Browserkontext beginnt ohne Cookies, localStorage oder sessionStorage. Nach Anmeldung am zweiten BFF lädt die Oberfläche den offenen Bon samt Restposition, den unbezahlten Bon sowie den bezahlten Teilbon. Bezahlte Bons können nicht erneut bezahlt werden.
4. Nach explizitem Löschen beider Webspeicher und Reload wird derselbe offene Bon über den Tisch erneut geladen; die serverseitigen Daten bleiben erhalten.
5. Die Schichtoberfläche lädt Kasse, Ausgaben, Mitarbeitername und Zeitspanne. Authentifizierte Serverabfragen prüfen zusätzlich Reservierungsstatus, offenen Bon und exakten Lagerbestand.
6. Backend und beide BFF-Prozesse werden beendet und neu gestartet. Die komplette Wiederherstellung wird erneut mit einem frischen Browser und neuem Login geprüft, bevor die vorhandenen Replays und Abschlussaktionen fortgesetzt werden.
7. Der bestehende PostgreSQL-Dump/Restore-Vergleich prüft auch diese Bon-, Belegungs- und Schichtdaten zeilengenau.

Die Browseraktionen laufen gegen echte Backendantworten. Beim Verlust der Teilzahlungsantwort wird der echte Request mit `route.fetch` ausgeführt und ausschließlich seine Zustellung abgebrochen. Es werden keine erfundenen Erfolgsantworten für Geschäftsvorgänge geliefert. Der Datenbestand wird ausschließlich in einer temporären Abnahmedatenbank aufgebaut; die Docker-Vorschau bleibt unberührt.

## Prüfgates

| Prüfung | Ergebnis |
| --- | --- |
| Backend Build / Tests | **PASS** – `clean verify`, 260 Tests, keine Fehler/Fehlschläge/übersprungen; JAR und JaCoCo-Report erzeugt |
| Frontend Lint / Unit / Security / API-Typen | **PASS** – Lint, 15 Unit- und 3 Security-Tests sowie `api:check` |
| Frontend Build / Critical E2E | **PASS** – Produktionsbuild einschließlich TypeScript; 18 kritische E2E-Tests mit festen Testdaten und `--retries=0` |
| Reale Browser-Recovery / Neustart / Dump-Restore | **PASS** – sämtliche Schritte vor/nach Neustart; exakter Restore-Vergleich einschließlich Schicht und Mitarbeiterdaten, synthetisch 9 Sekunden |

Temporäre Logs: `/tmp/fasswerk-phase5-backend.log`, `/tmp/fasswerk-phase5-frontend.log`, `/tmp/fasswerk-phase5-fullstack.log`. Lokale Abnahme; kein behaupteter GitHub-CI-Lauf und keine Produktions-RPO/RTO-Aussage.

## Grenzen und nächster Schritt

Server-Recovery setzt eine wieder erreichbare Anwendung und Datenbank voraus. Ohne Verbindung werden keine Offline-Zahlungen oder Offline-Buchungen erfunden. Beim Verlust auch des ursprünglichen Tabs/Browserprofils bleibt der Geschäftszustand serverseitig abrufbar; der lokale unklare Auftrag wird nicht automatisch an ein anderes Gerät übertragen. Vor einer neuen Benutzeraktion muss dort der tatsächliche Bonstand geprüft werden. Verbesserungen der Fehler-/Recovery-UX bleiben Thema von Phase 16.

Nächster Schritt: Phase 6 – Korrekturen, Storno und Audit. Keine pauschale Produktionsfreigabe.
