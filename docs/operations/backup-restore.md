# Backup und Restore Runbook (PostgreSQL)

## Ziel
Dieses Runbook beschreibt Vollbackup, Restore in Staging und das Recovery-Time-Ziel (RTO) fuer FassWerk.

## Annahmen
- PostgreSQL laeuft als Service `postgres` in `docker-compose.yml`.
- Datenbank: `fasswerk`, User: `fasswerk`.
- Backup-Ziel ist ein persistentes Verzeichnis ausserhalb des Containers.
- RTO-Ziel fuer Staging-Test: <= 30 Minuten.

## 1) Vollbackup erstellen
```bash
mkdir -p ./backups
TS=$(date +%Y%m%d-%H%M%S)
docker compose exec -T postgres pg_dump -U fasswerk -d fasswerk -Fc > "./backups/fasswerk-${TS}.dump"
sha256sum "./backups/fasswerk-${TS}.dump" > "./backups/fasswerk-${TS}.dump.sha256"
```

## 2) Backup verifizieren
```bash
sha256sum -c "./backups/fasswerk-${TS}.dump.sha256"
```

## 3) Restore in Staging testen
```bash
# Beispiel: temporaere Staging-Datenbank
cat <<'SQL' | docker compose exec -T postgres psql -U fasswerk -d postgres
DROP DATABASE IF EXISTS fasswerk_staging_restore;
CREATE DATABASE fasswerk_staging_restore;
SQL

docker compose exec -T postgres pg_restore -U fasswerk -d fasswerk_staging_restore --clean --if-exists < "./backups/fasswerk-${TS}.dump"
```

## 4) Smoke-Checks nach Restore
```bash
# Anzahl Reservierungen pruefen (Beispiel)
docker compose exec -T postgres psql -U fasswerk -d fasswerk_staging_restore -c "SELECT count(*) FROM reservations;"

# Health pruefen
curl -fsS http://127.0.0.1:8080/actuator/health
```

## 5) Restore-Zeit messen
```bash
START=$(date +%s)
# ... Restore ausfuehren ...
END=$(date +%s)
echo "Restore-Dauer: $((END-START)) Sekunden"
```
Wenn die Dauer > 1800 Sekunden ist, Incident erstellen und Massnahmen planen (Storage, DB-Tuning, Backup-Frequenz).

## 6) Incident-Erstreaktion (Kurzcheckliste)
- Letztes erfolgreiches Backup und Checksumme identifizieren.
- Kommunikationskanal fuer Incident starten.
- Restore in Staging zuerst validieren.
- Produktiv-Restore nur nach Freigabe und dokumentierter Entscheidung.
- Nach Restore Smoke-Checks und API-Health dokumentieren.

