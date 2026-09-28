# Docker Deployment

## Voraussetzungen

Docker Engine mit Compose Plugin >= 2.24.4 (der Smoke-Test nutzt `!override`).
Port 3000 ist oeffentlich gebunden; PostgreSQL und Backend sind standardmaessig
nur ueber Loopback erreichbar. Fuer oeffentlichen Betrieb einen HTTPS-Reverse-Proxy
verwenden und `APP_ORIGIN` sowie `QR_SCAN_BASE_URL` passend setzen.

## Build und Start

Die private, ignorierte `.env` aus `.env.example` erstellen und `DB_USER`,
`DB_PASSWORD`, `JWT_SECRET` sowie `FASSWERK_IMAGE_TAG` setzen. Fuer Releases einen
Commit-SHA-Tag verwenden und niemals mit anderen Inhalten ueberschreiben.
`FASSWERK_VCS_REF` haelt den vollstaendigen Commit in den Image-Labels fest.
Compose erzwingt einen nichtleeren Tag, kann dessen Unveraenderlichkeit aber nicht
pruefen. Basisimages sind per Digest fixiert; Updates muessen bewusst gebaut und
getestet werden. Admin-Provisionierung: [configuration.md](configuration.md).

```bash
docker compose build
docker compose up -d --no-build --wait
docker compose ps
```

Das Backend nutzt `prod` und wartet auf eine gesunde PostgreSQL-Instanz. Flyway
migriert beim Start; das Frontend wartet auf den Backend-Healthcheck. Die App-Images
laufen als Nicht-Root mit schreibgeschuetztem Root-Dateisystem, ohne Linux-Capabilities
und mit `no-new-privileges`. `/tmp` und der Next.js-Cache sind begrenzte, fluechtige
tmpfs-Mounts. Datenbankdaten liegen weiterhin im persistenten Volume. Das Frontend-Runtime-Image
enthaelt keine Paketmanager (npm/Yarn/apk); Aenderungen erfolgen durch einen neuen
Image-Build. Node-Kompression und native Bildkonvertierung werden im Smoke-Test
geprueft.

Backend-Health prueft `/actuator/health`, Frontend-Health den HTTP-Startseitenaufruf.
SMTP geht nur bei aktiviertem Reservierungsversand (`RESERVATION_MAIL_ENABLED`)
in den Backend-Healthcheck ein. Diese Checks ersetzen keine durchgaengige fachliche Ueberwachung. Compose startet
unhealthy Container nicht allein wegen ihres Health-Status neu.

## Build-Gates und Smoke-Test

CI baut beide SHA-getaggten Images erst nach Backend-Verifikation, OpenAPI-/Typcheck
und Frontend-Gates inklusive kritischer Browsertests. Das Maven-Image paketiert ohne
erneuten Testlauf; lokale Docker-Builds allein sind daher kein Release-Nachweis.
Images werden durch diesen Workflow nicht veroeffentlicht.

Mit bereits gebauten Images und explizit exportierten Test-Zugangsdaten:

```bash
export FASSWERK_IMAGE_TAG=sha-example
export DB_USER=smoke
export DB_PASSWORD="$(openssl rand -hex 32)"
export JWT_SECRET="$(openssl rand -hex 48)"
./scripts/container-smoke.sh
```

Das Skript ignoriert lokale `.env`-/Override-Dateien, verwendet ein frisches
Compose-Projekt und dynamische Loopback-Ports. Es prueft Healthchecks, HTTP-Zugriff,
Nicht-Root, Root-Dateisystemschutz, tmpfs-Schreibrechte und Shutdown ohne SIGKILL/OOM.
Beim Ende entfernt es ausschliesslich die Ressourcen seines Testprojekts, inklusive
dessen Wegwerf-Datenbank. Keine produktiven Zugangsdaten verwenden.

## Lokale Vorschau in Docker Desktop

Der Zwischenstand `c80400c` verwendet die lokalen Images
`fasswerk/backend:preview-c80400c` und `fasswerk/frontend:preview-c80400c`.
Die private, Git-ignorierte `.env.preview` enthaelt den Image-Tag und eigene
Zufallszugangsdaten. Das Admin-Login steht dort unter `BOOTSTRAP_ADMIN_EMAIL`
und `BOOTSTRAP_ADMIN_PASSWORD`.

```bash
docker compose --env-file .env.preview -p fasswerk-preview -f docker-compose.yml -f docker-compose.preview.yml up -d --no-build --wait
docker compose --env-file .env.preview -p fasswerk-preview -f docker-compose.yml -f docker-compose.preview.yml stop
```

Aufruf: <http://localhost:13000>. In Docker Desktop erscheint das Projekt
`fasswerk-preview`. Die Vorschau nutzt eine eigene, anfangs leere Datenbank im
Volume `fasswerk-preview_postgres_data`. Stoppen erhaelt diese Daten. Backend und
PostgreSQL sind nur im internen Docker-Netz erreichbar; das Frontend ist nur an
Loopback gebunden. Fuer den lokalen Login `localhost` verwenden.

## Betrieb, Update und Rollback

```bash
docker compose logs backend --tail=100
docker compose logs frontend --tail=100
docker compose stop
```

Die App-Container erhalten SIGTERM und bis zu 30 Sekunden zum Beenden; Spring hat
20 Sekunden pro Shutdown-Phase. PostgreSQL bekommt 60 Sekunden. `docker compose down`
erhaelt das Datenvolume; `down --volumes` loescht es und gehoert nicht zum normalen
Deployment.

Vor Updates ein geprueftes Datenbankbackup anlegen. Fuer Registry-Deployments
`FASSWERK_BACKEND_REPOSITORY`, `FASSWERK_FRONTEND_REPOSITORY` und den geprueften
`FASSWERK_IMAGE_TAG` setzen, dann `docker compose pull` und
`docker compose up -d --no-build --wait` ausfuehren. Registry-Tags gegen Ueberschreiben
schuetzen und die ausgelieferten Image-Digests aufzeichnen.

Fuer einen App-Rollback den vorherigen Tag wieder setzen. Vorher pruefen, ob die
inzwischen ausgefuehrten Flyway-Migrationen mit dem alten Code kompatibel sind;
Image-Rollback setzt die Datenbank nicht zurueck.

- [Backup/Restore](operations/backup-restore.md)
- [Monitoring/Alerting](operations/monitoring-alerting.md)
