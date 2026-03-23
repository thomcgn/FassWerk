# Docker Deployment

## Voraussetzungen
- Docker Engine mit Compose Plugin
- Offene Ports: `3000`, `5432` (optional extern), `8080` (optional extern)

## Build + Start

```bash
docker compose up -d --build
```

## Status pruefen

```bash
docker compose ps
docker compose logs backend --tail=100
docker compose logs frontend --tail=100
```

## Flyway/Migrationen
- Flyway laeuft im Backend beim Start automatisch.
- Das Backend wartet mit Retry (`FLYWAY_CONNECT_RETRIES`) auf PostgreSQL.

## App URLs
- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`

## Optional: Nur Images bauen

```bash
docker compose build
```

## Optional: Registry Push (Beispiel)

```bash
docker tag fasswerk/backend:latest ghcr.io/<org>/fasswerk-backend:latest
docker tag fasswerk/frontend:latest ghcr.io/<org>/fasswerk-frontend:latest
docker push ghcr.io/<org>/fasswerk-backend:latest
docker push ghcr.io/<org>/fasswerk-frontend:latest
```

