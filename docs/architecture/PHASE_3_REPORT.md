# PHASE 3 REPORT

Datum: 2026-09-27

## Umfang

PostgreSQL-Teststrategie gemaess `FASSWERK_CODEX_REFACTOR.md`.
Keine Phase 4 begonnen. Keine bestehende Datenbank veraendert, keine historischen
Migrationen editiert, keine API-Vertraege geaendert.

## Geaenderte Dateien

- `backend/pom.xml`: H2 durch testcontainers-postgresql (test scope, Boot-BOM) ersetzt.
- `backend/src/test/resources/application-test.yml`: PostgreSQL, Flyway aktiv,
  Hibernate validate, Bootstrap in Tests deaktiviert.
- `backend/src/test/java/org/thomcgn/backend/support/PostgresIntegrationTest.java`:
  gemeinsame Container-Lebensdauer, dynamische JDBC-Konfiguration, Laufzeitpasswort.
- `backend/src/test/java/org/thomcgn/backend/support/MigratedPostgresTest.java`:
  isolierte leere Datenbank pro SQL-/Migrationstest, Flyway und gezieltes Cleanup.
- `backend/src/test/java/org/thomcgn/backend/BackendApplicationTests.java`:
  Context-/Mapping-Validierung jetzt auf PostgreSQL.
- `backend/src/test/java/org/thomcgn/backend/auth/AuthFlowIntegrationTest.java` und
  `backend/src/test/java/org/thomcgn/backend/auth/SecurityRoleMatrixIntegrationTest.java`:
  bestehende HTTP-Vertraege auf PostgreSQL umgestellt.
- `backend/src/test/java/org/thomcgn/backend/billing/api/TableOrderArchiveApiIntegrationTest.java`:
  echte Auth-Persistenz auf PostgreSQL; bestehende Billing-Mocks beibehalten.
- `backend/src/test/java/org/thomcgn/backend/auth/LegacySeedMigrationTest.java`:
  echter Flyway-Upgrade V20 -> V21 statt H2 mit nachgebautem Minimalschema.
- `backend/src/test/java/org/thomcgn/backend/persistence/PostgresSchemaIntegrationTest.java`:
  leere Datenbank, Schema-Inventar, Migrationshistorie, Constraints.
- `backend/src/test/java/org/thomcgn/backend/persistence/LegacyTableTextMigrationTest.java`:
  Legacy-bytea-Upgrades fuer name und area.
- `backend/src/test/java/org/thomcgn/backend/persistence/ArchiveRepositoryIntegrationTest.java`:
  echte PostgreSQL-Archivabfragen fuer NULL/Text/Grossschreibung/fehlende Treffer.
- `backend/src/test/java/org/thomcgn/backend/persistence/ConcurrentWritesCharacterizationTest.java`:
  fuenf deterministische Service-Races und ein Transaktions-Rollback-Test.
- `backend/src/main/java/org/thomcgn/backend/billing/repository/TableOrderRepository.java`:
  explizite Suchparameter-Typisierung in beiden Archivabfragen.
- `backend/src/main/java/org/thomcgn/backend/config/StartupSecurityConfiguration.java`
  und `backend/src/test/java/org/thomcgn/backend/config/SecretConfigurationTest.java`:
  obsolete H2-Passwortausnahme entfernt, Regressionstest ergaenzt.
- `backend/README.md`, `docs/configuration.md`, `docs/testing.md`,
  `docs/architecture/TECH_DEBT.md` und dieser Bericht: Voraussetzungen,
  Testgrenzen und nachgewiesene Risiken dokumentiert.

## Behobene Probleme

1. Datenbanktests verwenden jetzt dieselbe Datenbankfamilie wie Produktion.
   Flyway erstellt das Schema; Hibernate darf es nur validieren. Kein H2-Fallback,
   keine Test-Skips bei fehlendem Docker und keine Verbindung zur lokalen Fach-DB.
2. Alle 14 vorhandenen Migrationen erzeugen auf leerer PostgreSQL-DB die erwarteten
   24 Fach-Tabellen. Wiederholtes migrate aendert nichts; Flyway validiert die Historie.
3. Neuer echter Repository-Test reproduzierte `function lower(bytea) does not exist`
   bei NULL-Suchtext. String-Casts am Parameter in beiden Archivabfragen beheben den
   Dialekt-/Bindungsfehler ohne Schemaaenderung. Vorher: 1 Fehler in 68 Tests.
4. Die Testkonfiguration benoetigt jetzt ebenfalls ein DB-Passwort; alte H2-Sonderlogik
   ist entfernt. Private Zugangsdaten werden ausschliesslich zur Laufzeit erzeugt.

## Neue und geaenderte Tests

24 neue Testfaelle gegenueber Phase 2, insgesamt 68:

| Bereich | Faelle | Aussage |
| --- | ---: | --- |
| Schema / Constraints | 11 | Migrationsinventar, Wiederholung, Unique/FK/NOT NULL, QR, Verkaufsaggregate |
| Legacy-bytea-Upgrades | 2 | V17 -> V21 erhaelt Text und korrigiert PostgreSQL-Spaltentypen |
| Archiv-Repository | 4 | NULL-, Text-, Gross-/Kleinschreibungs- und Zahlungsfilter |
| Konkurrenz / Transaktion | 6 | Fuenf Ist-Fehler reproduziert, atomarer Stock/Movement-Rollback geprueft |
| Testprofil-Konfiguration | 1 | Fehlendes Datenbankpasswort wird abgelehnt |

Bestehende 44 Tests beibehalten bzw. auf PostgreSQL umgestellt. Mockito-Unit-Tests
bleiben Unit-Tests. Der selektive Seed-Upgrade wird jetzt mit dem vollstaendigen
historischen Schema und echten Flyway-Versionen ausgefuehrt.

## Offene Risiken

**Die fuenf knownGap-Tests dokumentieren Fehlverhalten, keine Konkurrenzsicherheit.**
Sie erwarten ausdruecklich den Ist-Zustand. Bei Domainkorrekturen muessen sie durch
Soll-Invarianten ersetzt werden; sie duerfen nicht als Sicherheitsnachweis gelten.

| Reproduzierter Fall | Ergebnis | Folgephase |
| --- | --- | --- |
| Letzter Reservierungsslot | Zwei Reservierungen bei einem aktiven Tisch | 7 |
| Boneroeffnung | Zwei offene Bons fuer einen Tisch | 8 |
| Parallele Teilzahlungen | Aus 3 Einheiten werden 4 bezahlt und 1 bleibt offen | 8 |
| Bestand | Anfang 10, zwei Bewegungen -6, Endbestand trotzdem 4 | 8 |
| Refresh-Rotation | Zwei aktive Nachfolger desselben urspruenglichen Tokens | 6 |

Weitere Grenzen:

- PostgreSQL erlaubt doppelte Verkaufsaggregate bei NULL drink_variant_id; nur
  nicht-NULL-Varianten sind durch die vorhandenen Unique Keys geschuetzt.
- Legacy-Upgrade-Tests pruefen die Repository-Historie, nicht fremde konsolidierte
  Produktionshistorien/Checksummen. Keine Freigabe eines beliebigen Altbestands.
- GitHub-CI ueberspringt weiterhin Backendtests; CI/Release-Gates bleiben Phase 11/12.
- Bestehende npm-Audit-Befunde (20, davon 1 critical/11 high) und fuenf Lint-Warnungen
  sind unveraendert. Browser-Tests mocken weiterhin APIs.
- In inkrementellen Zwischenlaeufen gab es fehlerhafte Klassenartefakte mit
  `Unresolved compilation problems` / `Flexible Constructor Bodies`. Der Verursacher
  ist nicht abschliessend festgestellt; das Abschlussgate verwendet einen Clean-Build.

## Ausgefuehrte Pruefungen

| Pruefung | Ergebnis |
| --- | --- |
| `./mvnw -B clean verify` | PASS: 68 Tests, 0 Failures, 0 Errors, 0 Skips; JAR und JaCoCo erzeugt, 1:35 min |
| Flyway / Hibernate | PASS: 14 Migrationen, 24 Fach-Tabellen, JPA validate; separate Legacy-Upgrades |
| `npm ci` | PASS; 20 bekannte Audit-Befunde |
| `npm run lint` | PASS mit 5 bekannten Warnungen, 0 Fehler |
| `npm run build` | PASS |
| `npm run test:e2e:critical` | PASS im isolierten Abschlusslauf: 9/9, 1.2 min |
| Produktions-JAR | Keine Testcontainers-/H2-JARs und keine application-test.yml enthalten |
| Historische Migrationen / Diff | Unveraendert gegen HEAD; git diff --check sauber |

JaCoCo: 837/1943 Lines (43.1%), 205/517 Branches (39.7%). Das ist kein neuer
Coverage-Schwellwert und kein Nachweis vollstaendiger Domainabdeckung.

Der erste Browserlauf waehrend paralleler Backend-Arbeit hatte 8/9 erfolgreiche
Tests; der Setup-Flow erreichte das bestehende 30-Sekunden-Limit. Der anschliessende
isolierte Lauf bestand unveraendert (Setup-Flow 13.3s). Keine Timeouts erhoeht,
Assertions reduziert, Tests deaktiviert oder Frontenddateien geaendert. Die bestehende
Last-/Devserver-Empfindlichkeit bleibt ein Risiko, nicht als behoben verbucht.

Lokale Nachweise: `/tmp/fasswerk-phase3-backend-verify.log`,
`/tmp/fasswerk-phase3-backend-before-archive-fix.log`,
`/tmp/fasswerk-phase3-npm-ci.log`, `/tmp/fasswerk-phase3-lint.log`,
`/tmp/fasswerk-phase3-build.log`, `/tmp/fasswerk-phase3-e2e-isolated.log`.
Fehlertrace: `/tmp/fasswerk-phase3-playwright-initial-failure`;
Abschlussartefakte: `/tmp/fasswerk-phase3-playwright-final`.
Generierte, bereits versionierte Browserreports wurden nach Sicherung auf ihren
sauberen Ausgangsstand zurueckgesetzt. Testcontainers hat seine Container entfernt.

## Ergebnis

**PASS fuer Phase 3 / PostgreSQL-Teststrategie.** Das definierte Gate ist erreicht:
ein frischer PostgreSQL-Container erzeugt ausschliesslich mit Flyway den erwarteten
Schema-Stand; alle Abschlusspruefungen bestehen.

**Keine Freigabe der Konkurrenzsicherheit:** Die dokumentierten fachlichen Fehler
bleiben offen und muessen vor verlaesslichem konkurrierendem Produktionsbetrieb
behoben werden. Ein gruener Charakterisierungstest darf nicht als behobener Defekt
interpretiert werden.

## Empfohlener naechster Schritt

**PHASE 4 -- Domain Map und Modulgrenzen**, nach ausdruecklicher Freigabe.
Dabei die nachgewiesenen Transaktions-/Domainprobleme den Verantwortungsbereichen
zuordnen; die eigentliche Security-/Reservation-/Billing-Korrektur folgt in Phasen
6/7/8. Keine Folgephase automatisch begonnen.
