# FassWerk Frontend

Next.js App Router Frontend fuer Iteration 5.

## Features
- Login gegen Backend (`/api/auth/login`) mit HttpOnly-Cookies
- Inventory-Ansicht ueber serverseitigen Proxy (`/api/inventory`)
- Buchungssystem (`/bookings`) mit Reservierungsanlage, Check-in und Storno
- Tischabrechnung (`/table-billing`) fuer Bon-Erstellung, Positionen und Abschluss
- PDF-Download fuer Nachbestellliste (`/api/reports/reorder-list`)
- Refresh-Token-Rotation ueber internen Auth-Proxy
- Session-Verwaltung (`/sessions`) mit Session-Revoke und Logout-All

## Environment
- `BACKEND_BASE_URL` (default `http://localhost:8080`)

## Start

```bash
npm run dev
```

Dann im Browser:
- `http://localhost:3000/login`
- `http://localhost:3000/inventory`
- `http://localhost:3000/bookings`
- `http://localhost:3000/table-billing`
- `http://localhost:3000/sessions`

## E2E (Playwright)

```bash
npm run test:e2e
```

Neue kritische Flows:
- `e2e/reservation-lifecycle.spec.ts`
- `e2e/table-billing-split-payment.spec.ts`
- `e2e/inventory-deduction-order-events.spec.ts`

Reproduzierbare Testdaten und Session-Mocks:
- `e2e/support/test-data.ts`
- `e2e/support/mock-auth.ts`

## Lint-Report fuer Sonar

```bash
npm run lint:report
```

