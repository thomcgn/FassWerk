# Monitoring und Alerting Baseline

## Ziel
Mindeststandard fuer Monitoring und Alerts, damit On-Call schnell reagieren kann.

## Monitoring-Baseline
- Health: `GET /actuator/health`
- JVM: `jvm.memory.used`, `jvm.gc.pause`, `process.cpu.usage`
- DB-Verbindungen: `hikaricp.connections.active`, `hikaricp.connections.pending`
- API-Fehlerrate: Anteil 5xx pro Endpoint/Route
- Scheduler-Laufzeiten/Erfolg: Auth-Cleanup und Sales/Reorder-Jobs

## Empfohlene Erfassung
- Prometheus scrape auf `GET /actuator/prometheus`
- Dashboard fuer:
  - API-Latenz (p50/p95)
  - 4xx/5xx Rate
  - DB-Pool-Auslastung
  - Scheduler letzte Laufzeit und Fehlerzaehler

## Alerting-Regeln (kritisch)
1. API-Fehlerrate
- Bedingung: 5xx Rate > 5% ueber 5 Minuten
- Schweregrad: High
- Erstreaktion: letzte Deployments und Logs pruefen

2. Job-Ausfaelle
- Bedingung: geplanter Job 2x nacheinander fehlgeschlagen oder nicht gelaufen
- Schweregrad: High
- Erstreaktion: Scheduler-Logs und DB-Locks pruefen

3. DB-Erreichbarkeit
- Bedingung: Health DOWN oder DB-Check fehlgeschlagen > 2 Minuten
- Schweregrad: Critical
- Erstreaktion: DB-Service/Netzwerk/Storage pruefen, ggf. Failover einleiten

4. Auth-Anomalien
- Bedingung: starke Abweichung bei Login-Fehlern oder Refresh-Fehlern
- Schweregrad: Medium/High
- Erstreaktion: verdaechtige IPs/User-Agent aggregieren, Rate-Limits/WAF pruefen

## Incident-Erstreaktion (15 Minuten)
- Alert bestaetigen, Statusseite und Team informieren.
- Scope bestimmen: alle APIs oder nur einzelne Domains.
- Health, DB-Pool und Error-Logs parallel pruefen.
- Falls noetig: Rollback oder Traffic-Reduktion vorbereiten.
- Entscheidung und Zeitstempel im Incident-Log dokumentieren.

