# Behebung des Abschluss-Audits

Stand: 2026-09-28. Folgeauftrag zu [FINAL_AUDIT.md](FINAL_AUDIT.md), keine neue
Phase der Production-Roadmap. Vorhandene Phase-14/15-Änderungen bleiben erhalten.

## Befunde und Umsetzung

| Befund | Umsetzung und Grenze |
| --- | --- |
| FA-01 | Login prüft auch unbekannte/inaktive Konten mit BCrypt; überlange BCrypt-Eingaben werden kontrolliert abgewiesen. PostgreSQL-Limits für Adresse und Konto/Refresh-Kennung gelten instanzübergreifend, Fehlversuche committen. Gespeichert werden nur HMAC-Fingerprints der Limit-Schlüssel. Kein dauerhafter Account-Lockout. |
| FA-02 | Access-Tokens tragen Kontoversion und Sitzungsfamilie. Logout, Session-Widerruf, Logout-all und JWT-Replay invalidieren bereits ausgestellte Access-Tokens; die aktive Familie wird bei jedem Request geprüft. V26 widerruft nach ausdrücklicher Zustimmung einmalig alle bisherigen Refresh-Sitzungen; siehe Upgrade. |
| FA-03 | BFF-Login fordert eine serverseitige Browser-Sitzung an: zufälliges 256-Bit-Secret, nur SHA-256 in der DB, HttpOnly-/Secure-Cookie, absolute Laufzeit. Gleichzeitige Refreshes rotieren dieses Secret nicht. Access-Antworten werden nach Abschluss nicht gecacht; Refresh/Status überschreiben oder löschen keine neueren Refresh-Cookies. Direkte API-JWT-Refreshes behalten strikte Rotation und Replay-Erkennung. Zwei echte BFF-Prozesse und verspätete Cookies nach Logout geprüft. |
| FA-04 | Lookback, Sicherheitsfaktor und Lieferzeit persistiert, Wertebereiche validiert; Read-after-write getestet. Sicherheitsfaktor maximal vier Nachkommastellen, keine stille Rundung. |
| FA-05 | Bonpositionen speichern den Verkaufstag; gleiche Varianten verschiedener Tage bleiben getrennt. Teilstorno korrigiert Ursprungstag und vorhandene Wochenaggregation. Optionaler `Idempotency-Key` für DELETE verhindert doppelten Teilstorno. Split übernimmt den Ursprungstag. Alte, nicht eindeutig zuordenbare Positionen werden nicht geraten; Storno verlangt Klärung. |
| FA-06 | Gemeinsame Uhr/Zone/Geschäftstagskonfiguration für neue Billing-Abschlüsse, Reports, Archiv und Schichtumsätze. Abschlussdatum bleibt nach manuellem Tageswechsel stabil. Historische Zeitstempel werden unverändert über die bisherige Hibernate-Bindung gelesen. Tagesbereiche sind halboffen; Summen und Diagramme verwenden dieselbe Projektion. Nachtgrenze und beide DST-Wechsel getestet. |
| FA-07 | Je Lagerartikel eigene Transaktion, Erfolgszählung nach Commit. PostgreSQL-Advisory-Lock koordiniert Wochenjobs und Buchungen. Alle vorhandenen Wochen werden nachgeholt; NULL-Varianten bleiben erhalten und werden als Klärungsfall protokolliert. DB-Fehler eines Artikels, zwei parallele Jobs und alte Wochen getestet. |
| FA-08 | Neue V26–V28, keine alte Migration verändert. Leerschema und bekannte Upgradepfade getestet. Read-only-Vorprüfung für eine V25+-Restore-Kopie: `backend/scripts/audit-preflight.sql`. Echte Produktionshistorie/Checksummen und anonymisierte Bestandskopie fehlen weiterhin. |
| FA-09 | Reale Browser→zwei BFFs→Backend→PostgreSQL-Abnahme ergänzt: Login, Reservierung, Check-in, verlorene Buchungsantwort, Wiederholung, Split, Neustart aller App-Prozesse, Zahlung/Checkout und Widerruf. Dump/Restore mit vollständigem Zeilenvergleich der Fach- und Flywaytabellen bestanden. Produktive RPO/RTO, Backup-Retention und Alarm bis zum echten Empfänger bleiben externe Nachweise. |
| TD-030 | PDF paginiert und bricht lange Listen nicht ab; eingebetteter DejaVu-Font mit Lizenz, Breitenumbruch. 160 lange Einträge sowie polnische/griechische Zeichen getestet. Nicht im Font enthaltene Zeichen erhalten bewusst `?`. |
| TD-031 | RECEIVED bucht volle bestellte Menge, Bewegung, Empfangsmenge/-zeit und Status atomar. Wiederholung/Parallelität bucht einmal. Liter/ml werden konvertiert; unpassende Einheiten und nicht darstellbare Mengen schlagen ohne Teiländerung fehl. RECEIVED/CANCELLED sind endgültig. Alte RECEIVED-Daten werden nicht rückwirkend eingebucht; Teil-/Abweichlieferungen benötigen weiterhin einen eigenen Fachvertrag. |

Verkaufsstatistik folgt dem Verkaufstag, bezahlter Umsatz dem Abschlussgeschäftstag.
Ein über Nacht offener Bon kann daher bewusst Verkauf und Zahlung an verschiedenen
Tagen haben. Neue Archiveinträge verwenden den Abschlussgeschäftstag; unbezahlte
Forderungen bleiben wie bisher unabhängig vom Datumsfilter sichtbar.

## Upgrade und einmaliger Sitzungswiderruf

V26 ergänzt Auth-Metadaten und Konfigurationsfelder, V27 den ursprünglichen
Verkaufstag, V28 den Abschlussgeschäftstag. Bestehende Zeilen werden nicht mit
vermuteten Geschäftstagen überschrieben. Vor dem Upgrade offene Positionen müssen
für spätere Stornos fachlich zugeordnet werden. Die alte Serverzeitzone muss für
historische `LocalDateTime`-Daten beim Deployment erhalten bleiben; ein Wechsel
braucht einen separat geprüften Datenmigrationsplan.

Access-Tokens ohne neue Version werden abgelehnt. **V26 widerruft einmalig alle
noch nicht widerrufenen Refresh-Sitzungen** (`revoked_at = current_timestamp`,
`revoked_reason = 'SECURITY_UPGRADE'`). Alle Nutzer müssen sich danach auf ihren
Geräten erneut anmelden. Bereits widerrufene Sitzungen behalten Zeitpunkt und
Grund; Konten, Passwörter und Rollen werden durch den Widerruf nicht verändert.
Ein wegen `SECURITY_UPGRADE` widerrufener Token liefert 401, ohne über die
Replay-Behandlung eine inzwischen neu angemeldete Sitzung erneut zu widerrufen.
Die strikte Replay-Erkennung für regulär rotierte JWT-Refresh-Tokens bleibt bestehen.

Die historische Rotationsfamilie wurde nicht gespeichert und lässt sich nicht
zuverlässig rekonstruieren. Der einmalige Widerruf verhindert, dass alte
Vorgänger-/Nachfolgertokens oder Browser-JWT-Cookies diesen Altpfad weiter nutzen.
Nach neuem Browser-Login gilt die gemeinsame serverseitige Sitzung. Damit ist die
zuvor offene Altbestandsentscheidung für FA-02/03 umgesetzt.

Die automatische Freigabeprüfung hatte diese Zwangsabmeldung zunächst abgelehnt.
Nach Erläuterung der Folgen hat der Nutzer ausdrücklich zugestimmt; die Ergänzung
ist nun in der noch unveröffentlichten V26 enthalten. Flyway führt sie nur beim
Upgrade aus, nicht bei späteren Neustarts. In diesem Auftrag wurden ausschließlich
isolierte Testdatenbanken migriert; keine produktive Migration ausgeführt.

Alle Backend- und BFF-Instanzen müssen gemeinsam aktualisiert werden; keine
Mischversionen während des Rollouts. Ein Session-Widerruf erhöht konservativ die
Kontoversion für alle Access-Tokens dieses Kontos; andere gültige Sitzungen können
sich mit ihrem weiterhin gültigen Refresh erneuern.

## Konfiguration und Betrieb

Limits sind feste Datenbank-Minutenfenster (standardmäßig 120 pro Adresse und
15 pro Konto/Refresh-Secret). Überschreitung liefert HTTP 429, Rücksetzung im
nächsten Fenster. Schlüssel: `app.auth.rate-limit.address-per-minute` und
`app.auth.rate-limit.identity-per-minute`; z. B. als Spring-Startparameter
`--app.auth.rate-limit.address-per-minute=240`. Testprofil setzt hohe Werte für
bestehende Rollenproben; der gemeinsame Limiter besitzt separate Grenztests.

Die Backend-Adresse ist `request.remoteAddr`; beliebige Forwarded-Header werden
nicht vertraut. Hinter dem BFF ist dies dessen gemeinsame Adresse. Ein tatsächlich
vertrauenswürdiges Gateway muss bei öffentlichem Betrieb zusätzlich pro Client
begrenzen; aggregiertes BFF-Limit passend zur Last dimensionieren. Feste Fenster
verhindern keine konzentrierten Grenzbursts. Keine Behauptung identischer
Gesamtlaufzeiten aller Loginantworten, sondern gleicher teurer Passwortprüfpfad.

Browser-Sitzungen verlängern ihre absolute Ablaufzeit beim Refresh nicht. Das
stabile Secret ist ein Bearer-Credential; TLS und sichere Cookie-/Proxykonfiguration
bleiben notwendig. Der BFF gibt es nicht als Login-JSON an JavaScript weiter.
Wochen-Catch-up scannt aktuell alle Tagesdaten unter einer globalen Sperre;
Durchsatz-/Bestandsgrößentests und ein inkrementeller Job bleiben P2.

## Reproduzierbare Prüfung

```bash
cd backend
./mvnw -B clean verify
cd ../frontend
npm ci
npm run test:unit
npm run test:security
npm run lint
npm run build
npx playwright install chromium
E2E_PRODUCTION=true npm run test:e2e:critical -- --retries=0
cd ..
ACCEPTANCE_JAR="$PWD/backend/target/backend-0.0.1-SNAPSHOT.jar" scripts/fullstack-acceptance.sh
```

Full-Stack benötigt Java 21, Node 22+, Docker, curl und OpenSSL. Standardports:
18101 (Backend), 13101/13102 (BFF), PostgreSQL dynamisch; über
`ACCEPTANCE_BACKEND_PORT`, `ACCEPTANCE_LEFT_PORT`, `ACCEPTANCE_RIGHT_PORT`
änderbar. Eigene zufällige Zugangsdaten, temporäre DB und Sessiondateien werden
aufgeräumt. Keine Verbindung zur bestehenden DB oder privaten `.env`.
Nur Öffnungszeit-Fixtures werden vor dem Test direkt in der eigenen DB gesetzt;
Fachaktionen erfolgen über echte Browser-fetches/BFF-Routen. Responseverlust wird
nach realem Backend-Commit simuliert, ohne erfundene API-Antworten.

CI übergibt das zuvor mit `clean verify` geprüfte JAR an `frontend-e2e-critical`.
Die Full-Stack-Abnahme ist damit Voraussetzung des bestehenden Release-Gates.
Ein tatsächlicher GitHub-Lauf für diesen Arbeitsbaum ist noch ausstehend.

Lokale Nachweise vor der abschließenden V26-Freigabe (Folgeprüfung siehe unten):

- `clean verify`: **228 Tests, 0 Fehler, 0 Failures, 0 Skips**. PostgreSQL-Migrationen,
  neue Auth-/Scheduler-/Storno-/Nacht-/Wareneingangs-/PDF-Regressionen eingeschlossen.
  JaCoCo: 83,86 % Zeilen (2109/2515), 68,13 % Branches (697/1023).
- 7 Modell- und 3 Security-Tests, Frontend-Lint, strenge Unused-/Typprüfung und
  Production-Build bestanden. Kritische Chromium-Flows: **18/18 ohne Retry**, auch nach der abschließenden
  Storno-Erweiterung.
- Echter Full-Stack-Test einschließlich Storno-Retry, zwei BFFs und Neustart:
  bestanden. Synthetischer Dump/Restore und Zeilenvergleich: **5 Sekunden** im
  finalen Lauf; kein produktives RPO-/RTO-Ergebnis.
- npm-Audit und Grype des finalen Backendartefakts: **jeweils 0 Befunde**.
- `actionlint`, Skriptsyntax und `git diff --check` bestanden. OpenAPI-Export auf
  zwei Ports byte-identisch; einzige Vertragsänderung ist der optionale
  `Idempotency-Key` für den Teilstorno. Generierte TS-Typen aktualisiert; `api:check` und anschließende strenge
  TypeScript-Prüfung bestanden. Zwei lokale Exportversuche überschritten unter Last den bisherigen
  40-Sekunden-Starttimeout. Der Export wartet jetzt konfigurierbar mit
  `OPENAPI_STARTUP_TIMEOUT_SECONDS` (Standard 120 Sekunden).

Maven wurde in einer isolierten `/tmp/fasswerk-audit-fixed/backend`-Kopie ausgeführt,
weil IDE-Compiler gleichzeitig `backend/target` beschreiben. Die Prüfung verwendet
unveränderte Repoquellen; keine Tests deaktiviert. Temporäre Logs
`/tmp/fasswerk-audit-*.log` sind keine versionierten CI-Artefakte.

### Folgeprüfung nach Zustimmung zu V26

Die genehmigte Ergänzung und der Schutz neuer Logins wurden anschließend mit
**14 gezielten PostgreSQL-/HTTP-Tests** geprüft: 0 Fehler, 0 Failures, 0 Skips.
`AuditSessionMigrationTest` belegt V25→V26, erhaltene Kontodaten/Widerrufshistorie
und die Einmaligkeit beim erneuten Flyway-Start. Die zusätzliche HTTP-Regression
belegt 401 für den Upgrade-Token und weiterhin gültige neue Access-/Refresh-Tokens.
Login/Logout, normaler JWT-Replay und Rollback bei fehlerhafter Rotation bestehen
weiterhin. Log: `/tmp/fasswerk-v26-final.log`.

Der frühere Gesamtlauf mit 228 Tests sowie Frontend-/Full-Stack-/Vertragsnachweise
wurden für diese begrenzte Ergänzung nicht als erneut ausgeführt dargestellt.

Offen bleiben die im Audit aufgeführten P2-Themen ohne konkreten Fix in diesem
Auftrag: Mail-Outbox/Retry, weitere Frontend-JSON-Fallbacks, Architektur-/Performance-
Umbauten, Monitoring-Heartbeat und externe Lieferketten-/Betriebseinstellungen.
Dieser Bericht ersetzt diese offenen Punkte nicht durch eine Produktionsfreigabe.
