# Frontend Architecture

Stand: Phase 9, 2026-09-28.

## Boundary model

The App Router remains the composition and HTTP-adapter layer. Authenticated
`page.tsx` files are Server Components that check cookies and redirect before
rendering an interactive client. Browser state, effects, FullCalendar, QR rendering,
local/session storage and mutations remain deliberate Client Component boundaries.

Feature-owned rules live below `frontend/features`:

- `reservation/model.ts`: reservation/settings response validation, lifecycle view
  grouping, status presentation and local date/time formatting.
- `billing/model.ts`: billing/catalog response validation, archive parsing/sorting,
  sellable-stock selection and billing presentation helpers.
- `inventory/model.ts`: package/unit narrowing, crate and stock calculations,
  thresholds and inventory response validation.
- `catalog/model.ts`: batch normalization/validation, known serving volumes and
  deterministic price adjustment.

`frontend/lib/api-client.ts` is technical infrastructure. It reads the existing
backend error envelope and rejects successful responses whose runtime shape does
not match the feature parser. Compile-time DTOs remain centralized in
`frontend/types/api.ts`; the manual runtime parsers are boundary guards, not a new
API schema. Phase 10 decides generation versus contract tests.

## Component rules

- Components own rendering, interaction state and orchestration, not stock,
  allocation, price or status calculations.
- A response body is `unknown` until a parser accepts it. New direct
  `response.json() as SomeDto` assertions are not permitted.
- API errors use `readApiError`; domain-specific fallback text stays at the call
  site.
- Route-transition loading uses `app/loading.tsx`; uncaught rendering/data errors
  use `app/error.tsx`. Recoverable feature errors stay local and offer retry where
  useful.
- Server Components stay the default. `"use client"` is used only for hooks, event
  handlers, browser-only APIs or libraries requiring the browser.
- Pure feature rules receive and return plain values and are covered by the native
  Node test suite.

## Audit result

The largest clients were `bar-admin-client.tsx`, `inventory-client.tsx` and
`table-billing-client.tsx`. Phase 9 removed roughly 540 lines from existing UI files
by extracting nontrivial rules and boundary parsing. They remain sizeable screen
composers (1237, 896 and 592 lines), but no longer contain the extracted business
calculations. Further visual decomposition should follow cohesive panels rather
than moving state into arbitrary wrapper components.

All current `"use client"` declarations were reviewed. The route pages for
bar-admin, bookings, inventory, sales configuration, sessions and table billing
already use server wrappers. Remaining client files require interaction state,
browser APIs or client-only libraries; `app/error.tsx` is client-side by Next.js
contract. No directive was removed merely to optimize a metric.

No explicit `any`, `@ts-ignore` or ESLint suppression was found. Unsafe JSON
assertions were removed from reservation, billing, inventory and catalog-admin
flows. Assertions remain in secondary areas including shift settlement, sessions,
sales configuration, metadata and auth helpers; these are recorded as residual
work and must converge with the Phase-10 API-contract decision.
