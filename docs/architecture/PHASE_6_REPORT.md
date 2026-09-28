# PHASE 6 REPORT

Datum: 2026-09-27. Ausgangsstand: sauberer Arbeitsbaum nach Phase 5.
Scope: Security Review. Nutzerentscheidung: ADMIN und neue Rolle BARCHEF duerfen
Lieferanten-/Nachbestellungen verwalten, Nachbestellungen berechnen und den
Geschaeftstag manuell abschliessen. Andere ADMIN-Rechte bleiben unveraendert.
Keine Phase 7 begonnen.

## Loesung

Serverseitige Rollenmatrix vervollstaendigt, deny-all-Fallback eingefuehrt und
BARCHEF samt neuer V22-Rollenconstraint integriert. Keine Konten angelegt oder
automatisch befoerdert.

JWTs benoetigen Signatur, richtigen Issuer, Ablauf-/Ausstellzeit, Subject und JTI.
Aktives Konto und aktuelle Rolle begrenzen alte Tokenrechte. Security-Fehler sind
konsistente 401/403 mit Request-ID; Auth-DB-Fehler werden geschlossen als 503 behandelt.

Accountbezogene PostgreSQL-Sperren verhindern doppelte Refresh-Nachfolger.
Replay-Widerruf wird committed, waehrend sonstige Persistenzfehler die Rotation
weiterhin atomar zurueckrollen. Sensible Auth-DTO- und Ereignislogs sind redigiert.

Next.js prueft Origin fuer schreibende Cookie-Requests, liefert Sicherheitsheader
und koordiniert parallele Refresh-Aufrufe pro Prozess. APP_ORIGIN ist in Compose
und Konfigurationsdokumentation aufgenommen.

Review aller geforderten Themen, vollstaendige Rechte-/Rest-Risikomatrix:
[SECURITY_REVIEW.md](SECURITY_REVIEW.md).

## Geaenderte Dateien

- `.env.example`
- `backend/src/main/java/org/thomcgn/backend/auth/AuthenticationRejectedException.java`
- `backend/src/main/java/org/thomcgn/backend/auth/JwtAuthenticationFilter.java`
- `backend/src/main/java/org/thomcgn/backend/auth/JwtTokenService.java`
- `backend/src/main/java/org/thomcgn/backend/auth/api/dto/LoginRequest.java`
- `backend/src/main/java/org/thomcgn/backend/auth/api/dto/LoginResponse.java`
- `backend/src/main/java/org/thomcgn/backend/auth/api/dto/LogoutRequest.java`
- `backend/src/main/java/org/thomcgn/backend/auth/api/dto/RefreshTokenRequest.java`
- `backend/src/main/java/org/thomcgn/backend/auth/api/dto/SessionResponse.java`
- `backend/src/main/java/org/thomcgn/backend/auth/domain/UserRole.java`
- `backend/src/main/java/org/thomcgn/backend/auth/repository/AppUserRepository.java`
- `backend/src/main/java/org/thomcgn/backend/auth/service/AuthService.java`
- `backend/src/main/java/org/thomcgn/backend/common/api/RequestCorrelationFilter.java`
- `backend/src/main/java/org/thomcgn/backend/common/api/SecurityErrorResponseWriter.java`
- `backend/src/main/java/org/thomcgn/backend/config/SecurityConfig.java`
- `backend/src/main/java/org/thomcgn/backend/reservation/service/ReservationMailService.java`
- `backend/src/main/resources/db/migration/V22__supported_user_roles.sql`
- `backend/src/test/java/org/thomcgn/backend/auth/AuthFlowIntegrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/auth/JwtTokenServiceTest.java`
- `backend/src/test/java/org/thomcgn/backend/auth/LegacySeedMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/auth/SecurityHardeningIntegrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/common/api/ApiContractIntegrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/BarchefRoleMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/ConcurrentWritesCharacterizationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/LegacyTableTextMigrationTest.java`
- `backend/src/test/java/org/thomcgn/backend/persistence/PostgresSchemaIntegrationTest.java`
- `docker-compose.yml`
- `docs/architecture/DOMAIN_MAP.md`
- `docs/architecture/PHASE_6_REPORT.md`
- `docs/architecture/SECURITY_REVIEW.md`
- `docs/architecture/TECH_DEBT.md`
- `docs/configuration.md`
- `docs/testing.md`
- `frontend/e2e/security-bff.spec.ts`
- `frontend/lib/csrf.ts`
- `frontend/lib/refresh-coordinator.ts`
- `frontend/lib/server-auth.ts`
- `frontend/next.config.ts`
- `frontend/package.json`
- `frontend/proxy.ts`
- `frontend/test/security.test.mjs`

## Behobene Probleme

- Nicht erfasste Verwaltungsoperationen waren mit beliebigem authentifizierten
  Token erreichbar; jetzt explizite ADMIN/BARCHEF-Rechte, unbekannte Routen denied.
- Fehlende aktive Kontopruefung und weiterhin wirksame veraltete ADMIN-Claims.
- JWTs ohne zwingende Ablauf-/Identitaetsclaims wurden nicht explizit abgewiesen.
- 403 statt 401 verhinderte den vorgesehenen Frontend-Refresh.
- Replay-Widerruf wurde zurueckgerollt; parallele Refreshs erzeugten zwei Nachfolger.
- Auth-Filter konnte Downstream-Exceptions verschlucken bzw. Chain doppelt aufrufen.
- Secret-/Session-DTOs konnten durch toString-Logging Credentials/Metadaten ausgeben.
- BFF besass keine explizite Origin-Pruefung fuer cookieauthentifizierte Mutationen
  und keine eigenen grundlegenden Sicherheitsheader.

## Neue und geaenderte Tests

- SecurityHardeningIntegrationTest: 27 Testfaelle inklusive dynamischer Pruefung
  der registrierten Fach-Endpunkte, Rollenmatrix, BARCHEF-Erfolgspfad, deaktiviertes
  Konto, Herabstufung, Sessionownership, Replay/Rollback und Health/CORS.
- JwtTokenServiceTest: 10 Faelle fuer Signatur/Issuer/Ablauf/Pflichtclaims,
  BARCHEF/Lebenszeiten und redigierte DTO-Ausgaben.
- BarchefRoleMigrationTest: Upgrade von V21, bestehende Konten und Rollenconstraint.
- Bestehender Refresh-knownGap-Test auf das korrigierte Soll umgestellt; die vier
  uebrigen Konkurrenzfehler bleiben ausdruecklich offen.
- AuthFlowIntegrationTest und ApiContractIntegrationTest folgen den beabsichtigten
  401-/deny-all-Vertraegen und pruefen echten Replay-Widerruf statt alter Luecke.
- Schema-/Legacy-Migrationstests erwarten die zusaetzliche V22-Migration.
- Drei native Node-Tests fuer Origin und begrenzte Refresh-Koordination.
- Fuenf echte BFF-HTTP-Tests fuer Cross-Origin/fehlenden Origin, Same-Origin-
  Validierung, Sicherheitsheader und anonymen Status-Read im kritischen E2E-Satz.

Keine Security-Entscheidung wird lediglich durch eine Frontend-Schaltflaeche
erzwungen. Generische Rollenproben erlaubter Schreibpfade erwarten teils 400/404
wegen absichtlich unvollstaendiger Requests, nicht zwingend fachlichen Erfolg.
Positive Reads, Login, Lieferanten- und Sessionoperationen sind separat getestet.

## Reproduktion und Korrekturen

Vor dem Backend-Umbau waren alle 18 initialen Security-Testfaelle rot.
Der erste korrigierte fokussierte Lauf bestand mit 59 Tests.
Am unveraenderten BFF reproduzierten drei von vier HTTP-Tests fehlende
Origin-Pruefung/Header; der lesende Status-Endpunkt bestand bereits.

Ein Testannotationsfehler wurde vor der eigentlichen Backend-Reproduktion korrigiert.
Der erste Gesamt-Verify fand eine veraltete feste Migrationsanzahl im
LegacySeedMigrationTest: V20 -> latest sind nach V22 zwei statt einer Migration.
Ein anschliessender dynamischer Rollentest widerrief durch seinen eigenen
Logout-all-Aufruf die fuer spaetere Proben wiederverwendeten Tokens. Jede Probe
erhaelt jetzt ein frisches Token; die Widerrufswirkung wurde nicht abgeschwaecht.

Der Frontend-Build fand eine generische Promise/Awaited-Typabweichung im neuen
Koordinator; explizite Promise-Typisierung ohne unsicheren Platzhalter behebt sie.
Ein nicht mehr unterstuetzter Node-Modulschalter wurde entfernt. Der kompatible
native Testaufruf laesst die Modul-Erkennungswarnung sichtbar, statt sie zu verstecken.

Der erste kombinierte Browserlauf bestand 12/13 Faelle, erkannte aber eine
NextURL-Normalisierung von 127.0.0.1 zu localhost im Origin-Fallback.
Der Fallback nutzt nun den unveraenderten HTTP-Host und Request-Schema;
APP_ORIGIN hat weiter Vorrang. Ein zusaetzlicher Negativtest verhindert eine
stille Gleichsetzung verschiedener Loopback-Origins. Keine Origin-Whitelist
oder Abschwaechung des Cross-Origin-Schutzes.

## Ausgefuehrte Pruefungen

| Pruefung | Ergebnis |
| --- | --- |
| Initiale Backend-Reproduktion | 18/18 erwartete Fehler |
| Fokussierter Backend-Lauf nach Korrektur | PASS: 59 Tests |
| BFF-Reproduktion vor Korrektur | Drei erwartete Fehler, ein bestandener Fall |
| ./mvnw -B clean verify (final) | PASS: 149 Tests, 0 Failures, 0 Errors, 0 Skips; JAR und JaCoCo |
| OpenAPI JSON/YAML | PASS im Backend-Gate |
| npm ci | PASS; 20 bekannte Audit-Befunde, darunter 1 critical |
| npm run test:security | PASS: 3/3 |
| npm run lint | PASS: 0 Fehler, 5 bekannte Warnungen |
| npm run build | PASS nach Typkorrektur |
| npm run test:e2e:critical | PASS: 14/14, 58.8 Sekunden |
| git diff --check | PASS |

Die Browserpruefung startet nach den abgeschlossenen Backend-/Frontend-Builds.
Keine Tests deaktiviert, Timeouts erhoeht, Warnungen unterdrueckt oder alte
Flyway-Migrationen geaendert. Keine neue Dependency.
Native Node-Tests melden MODULE_TYPELESS_PACKAGE_JSON wegen fehlender expliziter
Moduldeklaration im bestehenden Frontend-Paket; die Tests bestehen.
Kein GitHub-Run, Commit oder Push in dieser Phase.

Nachweise: /tmp/fasswerk-phase6-before.log, /tmp/fasswerk-phase6-after.log,
/tmp/fasswerk-phase6-backend-verify.log, /tmp/fasswerk-phase6-backend-final.log,
/tmp/fasswerk-phase6-backend-complete.log, /tmp/fasswerk-phase6-bff-before.log,
/tmp/fasswerk-phase6-npm-ci.log, /tmp/fasswerk-phase6-node-tests.log,
/tmp/fasswerk-phase6-lint.log, /tmp/fasswerk-phase6-build.log,
/tmp/fasswerk-phase6-build-final.log, /tmp/fasswerk-phase6-e2e.log,
/tmp/fasswerk-phase6-lint-final.log, /tmp/fasswerk-phase6-build-complete.log,
/tmp/fasswerk-phase6-e2e-final.log.
Fehlgeschlagene BFF-Reproduktionsartefakte:
 /tmp/fasswerk-phase6-playwright-before.

## Offene Risiken

- TD-035: Kein belastbares Login-/Refresh-Rate-Limit; Unknown-User-Timingrisiko.
  Vor oeffentlichem Betrieb mit gemeinsamer IP-/Account-Policy schliessen.
- TD-036: Widerruf aller Refresh-Sessions bedeutet nicht sofortigen Widerruf aller
  schon ausgegebenen Access-Tokens. Nicht mitgegebenes Access-JTI bleibt bis Ablauf
  gueltig, solange das Konto aktiv ist (Default 120 Minuten).
- TD-037: BFF-Koordination gilt nur pro Prozess und drei Sekunden, nicht clusterweit.
  Multiinstanzbetrieb sowie Logout/Refresh-Rennen benoetigen Sessionkoordination.
- APP_ORIGIN muss hinter externem TLS-/Reverse-Proxy korrekt gesetzt sein;
  TLS/HSTS/Proxy-IP-Vertrauen wurden nicht auf einer Produktionsumgebung getestet.
- Bekanntes Frontend-Dependency-Audit inkl. critical-Befund bleibt offen.
- DEBUG-/TRACE-/Proxy-Logs sind trotz gezielter DTO-Redaktion kein sicherer Ort fuer
  sensible Payloads/QR-URLs. Vollstaendige strukturierte Fehlerdiagnose bleibt offen.
- Vier fachliche Konkurrenzdefekte und historisches Bonloeschen bleiben Phasen 7/8.
- BARCHEF besitzt bewusst keine weiteren ADMIN-Rechte und wird nicht automatisch
  einem Konto zugewiesen. Rollenwechsel/alte Backend-Instanzen erfordern koordinierten Rollout.

## Ergebnis

**PASS fuer das Phase-6-Gate.** Alle aktuellen Fach-Endpunkte sind serverseitig
einer Rollenregel zugeordnet; keine sicherheitsrelevante Freigabe haengt allein
vom Frontend ab. Alle Build-/Testgates bestehen. Die expliziten Security-Restbefunde
bleiben offen: Dies ist kein behaupteter Rundumschutz und keine Produktionsfreigabe.

Finale Browserartefakte: /tmp/fasswerk-phase6-playwright-final.
Der fehlgeschlagene Origin-Lauf bleibt unter
/tmp/fasswerk-phase6-playwright-origin-failure gesichert. Die beiden zuvor sauberen
versionierten Browserreportdateien wurden nur von den eigenen Testaenderungen
auf den Ausgangsstand zurueckgesetzt. Keine laufenden Testcontainer verblieben.

## Empfohlener naechster Schritt

PHASE 7 -- Reservierungs-Domain, nach erfolgreichem Abschluss und ausdruecklicher
Freigabe. Die genannten Security-Betriebsrisiken vor oeffentlichem Einsatz bzw.
horizontaler Skalierung gesondert priorisieren.
