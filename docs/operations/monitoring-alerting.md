# Monitoring und Alerting

## Implementierter Stand (Phase 13)

Die Anwendung verwendet Spring Boot Actuator, Micrometer und dessen
BOM-verwaltete Prometheus-Registry. Es gibt keinen separaten Monitoring-Server
im Compose-Stack. Scraper, Dashboard, Logsammlung und Alarmzustellung sind
Betriebsinfrastruktur und müssen dort eingerichtet werden.

| Signal | Zugriff / Bedeutung |
| --- | --- |
| `GET /actuator/health` | Öffentlich; Gesamtstatus ohne Komponenten/Details. Prüft unter anderem die DB; SMTP nur bei aktiviertem Reservierungsmailversand. DOWN liefert HTTP 503. |
| `GET /actuator/metrics` und `/actuator/metrics/{name}` | ADMIN-Bearer-Token; Namen und Werte der registrierten Metriken. |
| `GET /actuator/prometheus` | ADMIN-Bearer-Token; tatsächlich scrape-fähiger Prometheus-Export. |
| `http_server_requests_seconds_count` | HTTP-Anfragen nach Methode, Status, Outcome und Routenschablone (`uri`). |
| `http_server_requests_seconds_bucket` | Aktiviertes HTTP-Latenzhistogramm für aggregierbare Quantile. |
| `hikaricp_connections_active`, `_pending`, `_max`, `_timeout_total` | DB-Pool-Belegung, wartende Threads, Poolgrenze, Timeouts. |
| `jvm_memory_used_bytes`, `jvm_gc_pause_seconds_count`, `process_cpu_usage` | Automatisch registrierte JVM-/Prozessmetriken. |
| `reservation_mail_total{outcome="sent\|failed\|skipped"}` | Drei feste Outcomes, ab Start mit null initialisiert. `sent` bedeutet vom MailSender angenommen, nicht Zustellgarantie. `skipped`: Versand deaktiviert oder keine Empfängeradresse. |

Mailfehler entstehen nach dem Commit und verändern deshalb nicht den bereits
erfolgreichen HTTP-/Reservierungsstatus. Sie brauchen den separaten Zähler.
Reservierungsfehler werden über `http_server_requests_seconds_count` mit
`uri=~"/api/reservations.*"` ausgewertet. 4xx (z.B. Kapazitätskonflikte) getrennt
von 5xx betrachten. Für Exceptions, die der ControllerAdvice behandelt, kann das
Standardlabel `exception` `none` sein; Fehlerquoten deshalb nach **status** bilden.
Keine zusätzlichen Labels mit Reservierungs-ID, Gast, E-Mail, QR-Token, Request-ID
oder Exceptiontext verwenden. Unbekannte/abgewiesene Routen haben ggf. keine
fachliche Routenschablone.

## Scraping

Beispiel für einen Scraper im privaten Backend-Netz; Adresse und Jobnamen an die
Deployment-Umgebung anpassen:

```yaml
scrape_configs:
  - job_name: fasswerk-backend
    scrape_interval: 30s
    metrics_path: /actuator/prometheus
    authorization:
      type: Bearer
      credentials_file: /run/secrets/fasswerk-monitoring-access-token
    static_configs:
      - targets: ['backend:8080']
```

Das Token muss zu einem aktiven ADMIN-Konto gehören und vor Ablauf ersetzt werden.
Die Datei extern als Secret bereitstellen; keine Tokens in Konfiguration, Git,
Dashboard-Links oder Logs. Der bestehende ADMIN-Zugang ist kein auf Scraping
beschränktes Servicekonto. Außerhalb eines vertrauenswürdigen privaten Netzes TLS
verwenden. Ein fehlgeschlagener Scrape (`up=0`) kann auch abgelaufene Zugangsdaten
bedeuten. Ein Health-Check ist zusätzlich nötig: ein erreichbarer Metrics-Endpunkt
beweist keine gesunde Datenbank. Weitere Actuator-Endpunkte bleiben gesperrt.

## Konkrete Abfragen und Alarmvorschläge

API-5xx-Anteil über fünf Minuten, Schwellwert 5%, Alarm nach fünf Minuten;
zusätzlich `sum(rate(...[5m])) > 0` prüfen, um leeren Traffic auszuschließen:

```promql
sum(rate(http_server_requests_seconds_count{job="fasswerk-backend",uri=~"/api/.*",status=~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count{job="fasswerk-backend",uri=~"/api/.*"}[5m]))
> 0.05
```

Reservierungsfehler nach Route/Status (4xx und 5xx getrennt sichtbar):

```promql
sum by (uri, status) (rate(http_server_requests_seconds_count{job="fasswerk-backend",uri=~"/api/reservations.*",status=~"[45].."}[5m]))
```

Weitere Vorschläge:

```promql
# Versandfehler: Alarm bei mindestens einem beobachteten Fehler in 15 Minuten
sum(increase(reservation_mail_total{job="fasswerk-backend",outcome="failed"}[15m])) > 0

# Wartende DB-Verbindungen: Alarm nach zwei Minuten
max(hikaricp_connections_pending{job="fasswerk-backend"}) > 0

# HTTP p95 pro Routenschablone (Sekunden)
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{job="fasswerk-backend",uri=~"/api/.*"}[5m])))

# Scrape-Ausfall: Alarm nach zwei Minuten
up{job="fasswerk-backend"} == 0
```

Zusätzlich extern `/actuator/health` auf HTTP 200 überwachen (Alarm nach zwei
Minuten ohne Erfolg). Zähler gelten pro Prozess und werden bei Neustart
zurückgesetzt; `rate`/`increase` berücksichtigen Resets. Ereignisse zwischen
Neustart und erstem Scrape können fehlen. Logs bleiben für einzelne Fehler nötig.

## Sichere Fehlerdiagnose

Mit Profil `prod` schreibt Boot ECS-JSON auf stdout. `requestId` ist im MDC und
im Header `X-Request-Id`; bei API-Fehlern auch im Response-Envelope. Die ID wird
validiert/bei Bedarf ersetzt und nach jeder Anfrage aus dem MDC entfernt.
Clients dürfen dort nur eine technische Korrelations-ID senden, keine Secrets
oder personenbezogenen Daten. Die ID ist kein vertrauenswürdiger Benutzerbeleg.

- `http_request_failed`: HTTP-Status und Routenschablone; ohne Match `UNMATCHED`,
  niemals die rohe URL oder Query. WARN bei 4xx, ERROR bei 5xx.
- `api_processing_failed`: begrenzte Exception-/Cause-Typen und Codepositionen,
  ohne Exceptiontext, SQL, Parameter oder vollständigen Throwable-Dump.
- `authentication_store_failed`: gleiche sichere Diagnose für DB-Ausfälle bei
  der Authentifizierung; zugehöriger Request liefert 503.
- `reservation_mail_failed`: sichere Fehlerdiagnose ohne Empfänger, Namen oder
  Absagegrund; bei synchronem Aufruf bleibt die Request-ID verfügbar.
- `reorder_calculation_failed`: sichere Diagnose für abgefangene Reorder-Fehler.

Hibernate 7 protokolliert über `org.hibernate.orm.jdbc.error` auch abgelehnte
Datenbankwerte. Diese Ausgabe ist im Produktionsprofil deaktiviert und durch die
obige Diagnose plus HTTP-Statusmetriken ersetzt; auch echte PostgreSQL-Konflikte
werden darauf getestet. Andere Framework-Logs werden nicht pauschal unterdrückt.
Produktionsbetrieb nicht mit `DEBUG`, `TRACE`, SQL-/Bind-Logging oder
Request-Payload-Logging überschreiben. JDBC-Zugangsdaten separat über DB_USER und
DB_PASSWORD setzen, nicht in die geloggte DB_URL einbetten. Zugriffe auf gesammelte
Logs beschränken und Aufbewahrung/Rotation im Collector konfigurieren.

Incident: Request-ID und Zeitraum erfassen, JSON-Events danach filtern, Status,
Routenschablone und Codeposition zur eingesetzten Version zuordnen. Bei DB-Fehlern
Health/Pool/Timeouts prüfen, bei Mailfehlern SMTP-Erreichbarkeit und
Versandkonfiguration. Zur Diagnose keine Gäste-/Token-Payloads nachloggen.

## Grenzen und nächste Betriebsschritte

Vorhandene Auth-Cleanup-Zähler zählen entfernte Tokens, nicht erfolgreiche
Scheduler-Läufe. Ein belastbarer Alarm „Job zweimal nicht gelaufen“ ist damit
noch nicht abgedeckt; keine solche Zusicherung aus diesen Zählern ableiten.
Scheduler-Lauf-/Heartbeatüberwachung und Ende-zu-Ende-Alarmtests bleiben separate
Betriebsaufgaben. Ebenso fehlen ein installierter Collector/Scraper und ein
separater Scrape-Servicezugang. Mailversand hat weiterhin weder Retry noch Outbox.

Grundlagen: [Boot Structured Logging](https://docs.spring.io/spring-boot/4.0/reference/features/logging.html),
[Boot Metrics](https://docs.spring.io/spring-boot/4.0/reference/actuator/metrics.html).
