# FassWerk Backend

Spring Boot backend for reservation, menu, table billing, and transactional inventory deduction.

## Features in this iteration
- Flyway-based PostgreSQL schema and seed data
- Reservation API with slot/capacity checks and QR token flow
- QR code PNG generation endpoint
- Drink category/drink/variant CRUD APIs
- Table order open/add item/remove item/close APIs
- Transactional stock deduction and inventory movement logging
- JWT login with role-based authorization (`ADMIN`, `STAFF`)
- Refresh-token rotation and logout revoke flow
- Inventory CRUD + manual adjustment + movement API
- Reorder suggestions API and PDF export endpoint
- Actuator auth metrics (`/actuator/metrics/*`) and Prometheus endpoint (`/actuator/prometheus`)

## Local run
```bash
./mvnw spring-boot:run
```

Empfohlen fuer reproduzierbares lokales Setup:

```bash
cp .env.example .env
./scripts/run-local.sh
```

Das Script laedt alle Variablen aus `.env` und startet das Backend konsistent mit diesen Werten.

## Test run
```bash
./mvnw test
```

## Required environment variables
- `DB_URL` (default `jdbc:postgresql://localhost:5432/fasswerk`)
- `DB_USER` (default `fasswerk`)
- `DB_PASSWORD` (default `fasswerk`)
- `QR_SCAN_BASE_URL` (default `http://localhost:8080`)
- `JWT_SECRET` (default is dev-only fallback, set a strong secret in production)
- `JWT_ISSUER` (default `fasswerk-backend`)
- `JWT_ACCESS_TOKEN_MINUTES` (default `120`)
- `JWT_REFRESH_TOKEN_DAYS` (default `14`)
- `AUTH_CLEANUP_ENABLED` (default `true`)
- `AUTH_CLEANUP_CRON` (default `0 */30 * * * *`)
- `AUTH_CLEANUP_REFRESH_TOKENS` (default `true`)

## Actuator
- `GET /actuator/health` is public
- `GET /actuator/metrics/**` and `GET /actuator/prometheus` require `ADMIN`

## Seed users
- Admin: `admin@fasswerk.local` / `ChangeMe123!`
- Staff: `staff@fasswerk.local` / `StaffPass123!`

## Auth endpoints
- `POST /api/auth/login` -> access + refresh token
- `POST /api/auth/refresh` -> rotates refresh token and issues a new pair
- `POST /api/auth/logout` -> revokes refresh token + current access token (if bearer provided)
- `GET /api/auth/sessions` -> list active sessions for current user
- `DELETE /api/auth/sessions/{id}` -> revoke one session
- `POST /api/auth/logout-all` -> revoke all active sessions for current user

