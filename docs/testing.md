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
