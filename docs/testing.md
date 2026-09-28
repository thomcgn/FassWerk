# Database Test Strategy

## Running Tests

```bash
cd backend
./mvnw clean verify
```

Requirements: Java 21, a reachable Docker engine and access to Maven Central/the
container registry for the first run. Testcontainers 2.0.4 is version-managed by
Spring Boot 4.0.4. The test-only PostgreSQL module starts `postgres:16-alpine`, the
same PostgreSQL major version as Compose. Docker Desktop and standard Docker engine
socket discovery are supported by Testcontainers; no developer-specific socket path
is committed. An unavailable Docker engine fails integration tests: no skip, H2
fallback, or connection to an existing local/production database.

Unit tests remain plain JUnit/Mockito/ApplicationContextRunner tests without Docker.
For a focused non-database run, select a specific class, for example
`./mvnw -Dtest=InventoryServiceUnitConversionTest test`. This does not replace the
complete verification gate.

## Lifecycle and Isolation

- `PostgresIntegrationTest` owns one non-reusable container per test JVM, with a
  random host port and generated password. Spring gets its JDBC settings through
  `@DynamicPropertySource`; application DB environment values do not select the DB.
- The `test` profile enables Flyway and sets `ddl-auto: validate`. Hibernate never
  creates/updates the test schema. Bootstrap variables are explicitly empty in tests.
- The container outlives cached Spring contexts. Testcontainers/Ryuk cleans it up
  at process exit; individual test classes must not stop the shared container.
- `MigratedPostgresTest` creates a separate empty PostgreSQL database per method,
  runs the actual classpath migrations, then drops only that generated database.
  Upgrade fixtures do not modify the Spring integration-test database.
- HTTP integration tests reset their auth fixtures. Concurrency tests reset their
  domain fixtures with TRUNCATE CASCADE inside the disposable database. Archive
  repository tests use rollback transactions. Test classes run sequentially by
  default; do not enable JUnit class-level parallelism without per-class isolation.
- SQL fixture creation is test data setup, not a substitute schema. No historical
  migration was edited. When adding migrations, update explicit expected migration
  counts and the expected schema inventory in the migration tests.

## Coverage

| Test | Scope |
| --- | --- |
| BackendApplicationTests | Real Spring context, Flyway and Hibernate validation of entity mappings |
| AuthFlowIntegrationTest / SecurityRoleMatrixIntegrationTest | Existing HTTP auth/session/role contracts, now PostgreSQL-backed |
| TableOrderArchiveApiIntegrationTest | Existing HTTP fallback contract; billing repositories remain mocked, auth persistence is real |
| PostgresSchemaIntegrationTest | All 14 migrations to V21 in an empty DB, 24 domain tables, validation and no-op repeat migration, disabled legacy seeds |
| PostgresSchemaIntegrationTest | SQLSTATE assertions for unique table/category/volume/refresh/revocation keys, QR tokens, non-null sales aggregate keys, FK and NOT NULL constraints |
| LegacySeedMigrationTest | V20 -> V21 using real seeded users and sessions, preservation of already changed credentials |
| LegacyTableTextMigrationTest | V17 -> V21 with actual legacy bytea name/area columns; text content/type preserved |
| ArchiveRepositoryIntegrationTest | Real JPQL/PostgreSQL archive queries with null/text/case/paid filters; regression for lower(bytea) with a null query |
| ConcurrentWritesCharacterizationTest | Five synchronized races through real Spring services and repositories, plus stock/movement rollback on a constraint failure |
| OpenApiIntegrationTest | Anonymous JSON/YAML export over real HTTP, document content and media types |
| SecretConfigurationTest | Configuration validation remains isolated; test profile also requires a DB password |

## Module Boundary Regression Tests

Phase 4 adds `RevenueBoundaryIntegrationTest` for Reporting/Shift consumers and the
Billing revenue read contract, using the actual PostgreSQL schema. Timestamp-sensitive
order fixtures use JPA to match the production Hibernate UTC binding. It explicitly
preserves the legacy inclusive chart interval while aggregate intervals remain
half-open; it does not claim the time model is already consistent.

`BillingReadBoundaryTest` checks Report/Shift Java source references to Billing:
only `billing.application` is exported to these consumers. This narrow static guard
is not a full bytecode/runtime dependency analysis and does not hide the remaining
Catalog/Inventory/Billing write cycles. See [Domain Map](architecture/DOMAIN_MAP.md).

## Concurrency Findings: Not Safety Guarantees

The five `knownGap_*` tests deliberately characterize **existing faulty behavior**.
A green run means the defect is reproducible, not that concurrent writes are safe.
They are not disabled or expected-failure tests. After fixing each domain invariant,
replace the corresponding characterization assertion with the desired safety
contract and adapt the synchronization point to the chosen locking strategy.

Tests run two real service transactions at PostgreSQL's default READ COMMITTED
isolation. Repository spies delegate to the original Spring repository implementation
and synchronize after both initial reads with a bounded CyclicBarrier. No repository
results are fabricated. Both futures must commit successfully; final state is read
back independently. Worker pools are closed even when assertions or calls fail.

| Existing defect reproduced | Observed committed state | Desired invariant / follow-up |
| --- | --- | --- |
| Last reservation slot | Two reservations for one active table | At most available capacity; Phase 7 |
| Open order creation | Two OPEN orders for one table | One open order per table; Phase 8 |
| Partial payments | From 3 units, 4 units paid plus 1 unit still open | Quantity/value conservation; Phase 8 |
| Stock adjustments | Two -6 movements from 10 stock, but stock ends at 4 | Stock equals initial stock plus movements; Phase 8 |
| Refresh rotation | Same original token yields 2 active successors | Single-use rotation, then separately correct replay revocation; Phase 6 |

A separate aggregate-key check shows PostgreSQL UNIQUE permits duplicate daily and
weekly rows when `drink_variant_id` is NULL. Non-null variant keys are protected.
This is another existing schema/domain gap, not a regression from Testcontainers.

Phase 3 establishes the PostgreSQL test foundation and evidence. It deliberately does
not choose locking/idempotency semantics for all domains in one broad refactor.
Production concurrency safety remains unresolved. See
[technical debt](architecture/TECH_DEBT.md) and the phase report.

## Limits

This is neither a production load test nor complete coverage of all constraints,
service branches, migrations of external historical schemas, or payment retries.
Legacy-upgrade tests use the repository's existing consolidated migration history;
they cannot certify checksums from independently deployed databases.

The backend-openapi GitHub job now runs `clean verify` before export. A separate
throwaway PostgreSQL container and generated, masked DB/JWT credentials support the
prod-profile export; failure logs are uploaded and the container is removed even
on failure. This fixes the missing runtime prerequisites found after Phase 3.
Broader release/image enforcement remains Phase 11/12 work; local verification
is not a claim that GitHub CI ran. CI must not reuse persistent test databases or
disable Ryuk.

Frontend critical Playwright tests still mock API responses. They protect UI flows,
not the live frontend/backend/PostgreSQL contract. See the phase report for separately
executed npm installation, lint, build and browser checks.

## HTTP-Vertraege (Phase 5)

ApiContractIntegrationTest startet einen echten HTTP-Server mit PostgreSQL/Flyway,
OSIV=false und synthetischem signiertem ADMIN-Token. Geprueft werden MVC-Fehlercodes,
JSON-Envelope/Request-ID, unterdrueckte Parser-/SQL-/Tokeninformationen,
Inventar-/Nachbestellungsantworten mit LAZY-Relationen, Verbrauchsmetadaten und
leere Berechnungsergebnisse. Nur der gezielt ausgeloeste unerwartete 500 verwendet
einen Service-Spy; Persistenz- und Mappingfaelle verwenden echte Services/Repositories.

RequestCorrelationFilterTest prueft getrennt ungueltige/fehlende und gueltige
Request-IDs sowie MDC-Cleanup. OpenApiIntegrationTest prueft weiterhin JSON und YAML.

Gezielter Lauf:
```bash
cd backend
./mvnw -B -Dtest=ApiContractIntegrationTest,RequestCorrelationFilterTest,OpenApiIntegrationTest clean test
```

Diese Tests ersetzen weder die Rollenmatrix (Phase 6), die bekannten
Konkurrenz-Charakterisierungen noch einen echten Frontend-Backend-E2E-Vertrag.
Die kritischen Playwright-Tests verwenden weiterhin API-Mocks.

## Security-Gates (Phase 6)

SecurityHardeningIntegrationTest prueft echte HTTP-Autorisierung einschliesslich
aller registrierten Fach-Endpunkte, aktiver Konten, Rollenwechsel, BARCHEF,
Sessionownership, Health/CORS und Secret-freier Security-Fehler.
Bei generischen Schreibproben reichen 400/404 zum Nachweis, dass die Autorisierung
passiert wurde; separate positive Requests pruefen echte Use-Case-Erfolge.
Jede Rollenprobe verwendet ein frisches Access-Token, weil Logout-all das
mitgegebene Token absichtlich widerruft.

JwtTokenServiceTest deckt Signatur, Issuer, Ablauf, Pflichtclaims, Tokenlifetimes,
BARCHEF und DTO-Logredaktion ab. BarchefRoleMigrationTest prueft V21 -> V22.
Der Refresh-knownGap-Test ist jetzt ein Konkurrenz-Solltest: genau ein Nachfolger,
der nach dem konkurrierenden Replay widerrufen ist. Die vier anderen knownGap-Tests
bleiben bewusst Reproduktionen noch nicht behobener fachlicher Fehler.

Frontend ohne neues Testframework:
```bash
cd frontend
npm run test:security
npm run test:e2e:critical
```

Die nativen Node-Tests brauchen Type-Stripping-Unterstuetzung (Node >= 22.6).
Die neuen BFF-Tests im kritischen Playwright-Satz verwenden echte Next-HTTP-Routen
fuer Origin-/Headerpruefungen, keine API-Mocks. Die bisherigen neun fachlichen
Browserfaelle verwenden weiterhin API-Mocks und belegen keinen Vollstackvertrag.
In der aktuellen Node-25-Umgebung meldet der native Testimport eine
MODULE_TYPELESS_PACKAGE_JSON-Warnung, weil das bestehende Frontend keinen expliziten
package type besitzt; die drei Tests bestehen. Warnungen werden nicht unterdrueckt.

## Reservation Gates (Phase 7)

- ReservationRulesTest: overnight business date, split opening windows, full-stay
  fit, FIXED/FLEXIBLE intervals, DST gaps/ambiguities and elapsed duration,
  same-area multi-table allocation and missing capacities.
- ReservationLifecycleIntegrationTest: PostgreSQL-backed allocation, concurrency,
  atomic failed rescheduling, cancellations, check-in boundaries, completion,
  persisted deadlines, capacity mutation guards, older-client compatibility and
  after-commit mail including rollback and SMTP failure.
- Its HTTP contract test uses a real server, persisted BARCHEF account and disabled
  Open-Session-in-View, covering public settings/create, protected read/update/scan
  and multi-table DTO serialization.
- ReservationRegressionTest: past creation, unknown capacity, missed prior-day
  no-shows and terminal cancellation.
- ReservationCapacityMigrationTest: V22 -> V23 preserves old assignments and leaves
  unknown capacities/snapshots NULL; fresh PostgreSQL applies 16 migrations.
- The former reservation double-booking knownGap is now a concurrency invariant.
  The later Phase-8 suite converts the remaining billing/inventory known gaps into
  correctness assertions; see the Phase-8 section below.
- Browser tests include explicit QR scan/check-in, denied scan without guest data
  and explicit seat entry. Business UI tests mock APIs; they are not full-stack
  tests. The backend HTTP contract is tested independently.

## Reservation/Payment Follow-up

The original 180-minute Phase 7 tests are superseded by payment-bound occupancy.
ReservationRulesTest now checks arrival-only windows, fixed 30-minute elapsed
no-show grace and DST-safe DTO expiry display. ReservationLifecycleIntegrationTest
covers real PostgreSQL group check-in, directly bookable drinks/stock deduction,
partial payment, per-table full payment, unpaid archival/reopening, no time expiry
after arrival, no-show at exactly 30 minutes, old 15-minute snapshots, atomic rollback
of a conflicting group check-in, payment rollback and concurrent repeated check-in.
Its HTTP contract includes reading the automatically opened bills and settling
them through the real protected endpoints.

ReservationPaymentMigrationTest verifies V23 -> V24, preservation of historical
snapshots, unbounded new reservations and the unique OPEN-bill constraint. A second
case verifies that historical duplicate OPEN bills block migration without deleting
financial records. At this historical checkpoint fresh PostgreSQL applied 17 migrations; Phase 8
adds V25 and the fresh-schema total is now 18.

Duplicate-open, double-split-payment, and inventory-adjustment concurrency tests
now assert safe outcomes. The payment follow-up was historically incomplete; the
Phase-8 section below records the completed inventory gate.

Browser tests also verify no manual reservation-completion button, reuse of a table
after the final split payment, and unpaid archival/reopening without replacement
bills. Business browser tests remain mocked; actual backend state is covered by the
independent PostgreSQL and HTTP integration tests.


## Archived Deckel Correction

ReservationLifecycleIntegrationTest now verifies that explicit unpaid archiving
releases a table without marking the bill paid; elapsed time and partial payment
alone still do not release it. Additional cases cover a new group on the released
table, debt preservation after the new group pays, blocked old-deckel reopening
while a new reservation/table bill holds it, separate walk-in bills and rollback
of archival plus reservation completion. Browser coverage verifies a new bill is
opened instead of silently loading the archived deckel.

## Phase 8 Billing and Inventory

`BillingInventoryPhase8IntegrationTest` runs against PostgreSQL and verifies canonical
rounding, multiple positions, idempotent add/split/adjust, exact-zero and insufficient
stock, complete transaction rollback, 1 ml precision, sales quantities/cancellation,
weekly retry behavior, reorder units and preservation of paid history.
`ConcurrentWritesCharacterizationTest` now treats the former stock lost-update case as
a correctness assertion. `BillingInventoryMigrationTest` upgrades V24 to V25 and
checks precision plus the nonnegative-stock database guard. The complete gate is
`cd backend && ./mvnw -B clean verify`; Docker is required.
