# Security Review (Phase 6)

> Historischer Phase-6-Bericht. Die aktuellen Änderungen an Auth-Limits,
> Access-Widerruf und Browser-Sitzungen sowie die genehmigten Upgrade-Regeln
> stehen in [AUDIT_REMEDIATION.md](AUDIT_REMEDIATION.md).


Stand: 2026-09-27. Review von Identity & Access, REST-Security und dem
cookiebasierten Next.js-Adapter. Dies ist kein Penetrationstest und keine
pauschale Produktionsfreigabe.

## Verbindliche Rollenmatrix

Nutzerentscheidung: Lieferanten-/Nachbestellverwaltung, Nachbestellberechnung und
manueller Geschaeftstagsabschluss sind fuer **ADMIN und BARCHEF**, nicht STAFF.
BARCHEF ist neu; sonstige ADMIN-exklusive Rechte bleiben unveraendert.

| Endpunktgruppe / Operation | Anonym | STAFF | BARCHEF | ADMIN |
| --- | --- | --- | --- | --- |
| Login, Refresh, Logout (Token im Request erforderlich) | Aufruf erlaubt | Ja | Ja | Ja |
| Reservierung erstellen | Ja | Ja | Ja | Ja |
| GET Kategorien/Getraenke/Varianten | Ja | Ja | Ja | Ja |
| GET OpenAPI JSON/YAML und Swagger UI | Ja | Ja | Ja | Ja |
| GET /actuator/health ohne Details | Ja | Ja | Ja | Ja |
| Eigene Sessions lesen/widerrufen, Logout-all | Nein | Ja | Ja | Ja |
| Reservierungen lesen, QR, Scan, Check-in, Confirm, Cancel | Nein | Ja | Ja | Ja |
| Tische lesen/anlegen/aendern, Bon- und Zahlungsoperationen | Nein | Ja | Ja | Ja |
| Inventar inkl. Verkaufsdaten/Metadaten/Konfiguration lesen | Nein | Ja | Ja | Ja |
| Lieferanten und Nachbestellungen lesen | Nein | Ja | Ja | Ja |
| Lieferanten/Nachbestellungen erstellen/aendern, Lieferstatus | Nein | Nein | Ja | Ja |
| Nachbestellung berechnen, manueller Geschaeftstagsabschluss | Nein | Nein | Ja | Ja |
| Inventar anlegen/anpassen/loeschen, Metadaten/Konfiguration schreiben | Nein | Nein | Nein | Ja |
| Catalog schreiben, Standardpreise lesen/schreiben | Nein | Nein | Nein | Ja |
| Umsatzuebersicht und Schichtabrechnung lesen/schreiben | Nein | Ja | Ja | Ja |
| Sonstige Reports, insbesondere Nachbestell-PDF | Nein | Nein | Nein | Ja |
| Actuator metrics/prometheus | Nein | Nein | Nein | Ja |
| Nicht zugeordnete Routen | Nein | Nein | Nein | Nein |

Die Backend-Filterkette entscheidet, nicht sichtbare/unsichtbare UI-Schaltflaechen.
Unbekannte Rollen besitzen keine fachlichen Rechte. Neu registrierte Endpunkte
werden im Rollenmatrix-Test automatisch mitgeprueft. Autorisierungs-Tests nutzen
bei Schreiboperationen teils bewusst fehlende Bodies/nicht existente IDs: 400/404
belegen dort den erreichten Adapter, nicht fachlichen Use-Case-Erfolg. Zusaetzliche
gueltige Lese-, Login-, Lieferanten- und Sessiontests pruefen echte Erfolgspfade.

## Neue Rolle und Migration

UserRole enthaelt ADMIN, BARCHEF, STAFF. V22 fuegt einen CHECK fuer diese Werte hinzu;
bestehende Rollen, Konten und Passwort-Hashes bleiben unveraendert. Die vorherige
varchar-Spalte erlaubte beliebige Texte; unbekannte Bestandswerte muessen vor einem
Upgrade geprueft werden, statt still umgeschrieben zu werden.

Kein neues Standardkonto, kein Defaultpasswort und keine automatische Befoerderung.
Eine vorhandene Benutzerverwaltung existiert nicht. Ein berechtigter Betreiber kann
einem explizit ausgewaehlten bestehenden Konto kontrolliert BARCHEF zuweisen.
Alle Backend-Instanzen zuerst aktualisieren: alte Java-Versionen kennen den Enumwert
noch nicht. Nach Rollenwechsel neu anmelden/Token erneuern. Der Frontend-Vertrag
verwendet bereits role: string und benoetigt keinen zweiten Rollen-Enum.

## JWT und aktive Identitaet

- HMAC-Signaturpruefung mit validiertem Secret; JJWT waehlt beim Signieren einen
  zur Schluessellaenge passenden HMAC-Algorithmus. Keine unsignierten JWTs.
- Issuer muss exakt der Konfiguration entsprechen. Ablaufpruefung bleibt JJWT.
  expiration, issuedAt, subject und jti sind jetzt Pflichtclaims.
- Access und Refresh sind getrennte Tokenarten. Refresh-Tokens besitzen keine Rollen
  und authentifizieren keinen normalen API-Aufruf.
- Access-Token wird gegen Widerruf und ein aktives Datenbankkonto geprueft.
  Erlaubte Authority ist die Schnittmenge aus JWT-Rolle und aktueller Kontorolle.
  Alte ADMIN-Claims behalten nach Herabstufung keine ADMIN-Rechte.
- JWT-Credentials werden nicht im Spring Authentication-Objekt gehalten.
- JWT-Parserfehler werden gezielt abgefangen. Datenbankfehler im Auth-Filter fuehren
  geschlossen zu generischem 503, nicht zu versehentlicher Freigabe.
- Filter wird ausschliesslich in der Security-Kette ausgefuehrt; Downstream-Fehler
  werden nicht verschluckt oder durch einen zweiten Chain-Aufruf wiederholt.

Bestehende Lebenszeiten bleiben unveraendert: standardmaessig 120 Minuten Access,
14 Tage Refresh, konfigurierbar. Es gibt keine neue Audience-/Key-Rotation- oder
absolute Session-Maximaldauer-Policy. Diese sind vor erweiterten Deployments zu
entscheiden. Der zusaetzliche Account-Lookup pro Request erhoeht DB-Last und bindet
Authentifizierung bewusst an die Verfuegbarkeit der DB.

## Rotation, Replay und Widerruf

Tokenmutationen werden pro Benutzer mit PostgreSQL-PESSIMISTIC_WRITE auf app_users
serialisiert. Login, Refresh, Logout, einzelner Sessionwiderruf und Logout-all nutzen
denselben Lock. Refresh prueft Tokenbesitzer und aktiven Benutzer.

Eine dedizierte AuthenticationRejectedException bewirkt ausschliesslich beim Refresh
kein Rollback. Absichtlich gespeicherter Ablauf-/Replay-Widerruf wird damit committed.
Andere Persistenzfehler rollen weiterhin die gesamte Rotation zurueck.
Der Rollback-Test provoziert einen Fehler beim Speichern des Nachfolgers.

Zwei gleichzeitige Requests mit demselben Refresh-Token erzeugen genau einen
Nachfolger. Der zweite Request wird als Replay abgewiesen und widerruft die aktiven
Refresh-Tokens des Benutzers einschliesslich dieses Nachfolgers. Das entspricht der
schon vorhandenen konservativen Absicht, die bisher durch Rollback unwirksam war.
Es ist kein still eingefuehrtes Grace Window auf dem Backend.

Next.js fasst parallele Refresh-Aufrufe desselben Tokens pro Prozess zusammen.
Schluessel ist SHA-256 des Tokens, maximal 256 Eintraege; erfolgreiche/abgewiesene
Antworten werden drei Sekunden fuer bereits gestartete Browserrequests gehalten
und per Timer entfernt. Transport-/Serverfehler werden nicht als ungueltige Session
behandelt. Cookieupdates erfolgen separat im jeweiligen Request-Kontext.

**Grenzen:** Diese Koordination ist weder clusterweit noch eine dauerhafte
Sessionverwaltung. Mehrere Next-Prozesse, sehr verspaetete Requests oder
Logout/Refresh-Rennen koennen weiterhin zu erneuter Anmeldung fuehren.
Vor horizontaler Skalierung ist eine gemeinsame Session-/Refresh-Koordination
erforderlich; Replay-Pruefung nicht lockern, um das zu kaschieren.

Logout widerruft den mitgegebenen Access-JTI und den Refresh-Token. Einzelner
Sessionwiderruf, Replay und Logout-all widerrufen Refresh-Sessions, aber es gibt
keine Verknuepfung aller bereits ausgegebenen Access-JTIs mit einer Session.
Andere Access-Tokens bleiben bis zum Ablauf gueltig, sofern das Konto aktiv bleibt.
Der Name Logout-all darf deshalb nicht als sofortiger Widerruf aller Access-Tokens
interpretiert werden. Sessionbindung/Tokenversionierung bleibt ein offener P1-Befund.

## Fehlervertrag

Nicht authentifiziert/ungueltiges Token: 401, WWW-Authenticate: Bearer.
Authentifiziert ohne erforderliche Rolle: 403. Security-Fehler verwenden das
bestehende JSON-Envelope und eine sichere Request-ID, keine Exceptiontexte.
RequestCorrelationFilter laeuft vor Spring Security; QR-Tokenpfade werden redigiert.
Cache-Control fuer Security-Fehler ist no-store.

Ungueltige Login-/Refresh-Credentials liefern jetzt ebenfalls 401 statt 400.
Fehlende DTO-Pflichtfelder bleiben 400. Nicht zugeordnete Routen liefern nun
401/403 vor dem Dispatcher statt z.B. 404 fuer angemeldete Nutzer.
Das ist eine beabsichtigte Security-Vertragskorrektur; bestehende Tests wurden auf
das strengere Soll umgestellt. Frontend-Refresh reagierte bereits nur auf 401.

## CSRF und CORS

Backend: stateless Bearer-API, kein Cookie-/HTTP-Basic-Authentifizierungsfallback.
Daher bleibt CSRF dort deaktiviert. Keine CORS-Wildcards oder Cross-Origin-
Credentialfreigaben eingefuehrt; der Browser verwendet den gleich-originigen BFF.
Eine fremde Preflight-Anfrage erhaelt keine Access-Control-Allow-Origin-Freigabe.

Next.js: HttpOnly/SameSite=Lax und Secure in Produktion bleiben erhalten.
Unsafe /api-Requests benoetigen jetzt einen exakten HTTP(S)-Origin-Match;
fehlender/null/fremder Origin und Sec-Fetch-Site: cross-site werden abgewiesen.
Das gilt auch fuer Login. GET/HEAD/OPTIONS bleiben lesend erreichbar.
APP_ORIGIN ist die kanonische oeffentliche Origin; Forwarded-Header vom Client
werden fuer diese Entscheidung nicht uebernommen. Hinter TLS-/Reverse-Proxys
APP_ORIGIN explizit korrekt setzen. Ohne Variable gelten HTTP-Host und das von Next ermittelte Request-Schema.
NextURL normalisiert Loopback-Adressen; der unveraenderte Host verhindert dabei
einen falschen Vergleich zwischen 127.0.0.1 und localhost.

SameSite alleine ist kein vollstaendiges CSRF-Konzept. Grundlage:
[Spring Security CSRF](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html).
Die Pruefung ist im Next-16-Proxy fuer Route Handler angebunden, nicht als Ersatz
fuer Backend-Autorisierung:
[Next.js Proxy](https://nextjs.org/docs/app/api-reference/file-conventions/proxy).

## Header, Logging und Actuator

Spring-Security-Defaults liefern u.a. nosniff, DENY, Cache-Control und HSTS bei
sicheren HTTPS-Requests. Der BFF ergaenzt nosniff, DENY, Referrer-Policy und einen
begrenzten CSP fuer frame-ancestors/object-src/base-uri. Es wurde kein ungetesteter
strenger script-src eingefuehrt, der Next-Hydration brechen koennte.
TLS und HSTS am externen Reverse Proxy sind separat zu konfigurieren/pruefen.

LoginRequest, LoginResponse, RefreshTokenRequest, LogoutRequest und SessionResponse
haben redigierte toString-Ausgaben; JSON-Vertraege bleiben erhalten.
Replay-Logs enthalten keine E-Mail oder Token-ID mehr. Mailfehler loggen nur
Ereignis und Reservation-ID, nicht Empfaenger, Betreff oder Exceptionnachricht.
Beliebiges TRACE-/Body-/SQL-Bind-Logging und Proxy-URL-Logging koennen weiterhin
sensible Informationen offenlegen; in Produktion nicht einschalten.
Insbesondere QR-Tokens in URLs duerfen nicht unredigiert ins Access-Log.

Health ist anonym ohne Details erreichbar, metrics/prometheus ADMIN-exklusiv.
Eine Prometheus-Registry wurde nicht hinzugefuegt: konfigurierte Freigabe ist
kein Nachweis, dass ein scrape-faehiger Endpoint vorhanden ist.
OpenAPI bleibt fuer den bestehenden Export oeffentlich; keine Secrets im Schema.

## Passwoerter und Rate-Limit-Bedarf

BCryptPasswordEncoder bleibt erhalten (Default-Cost 10); Klartextpasswoerter werden
nicht persistiert. Ueberlange Eingaben oberhalb 72 UTF-8-Bytes werden beim Login
abgewiesen statt als BCrypt-Fehler zu enden. Bestehende Hashes/Accounts werden nicht
neu geschrieben. Legacy-Seed-Absicherung und Production-Secret-Validierung bleiben.

**Offen, vor oeffentlichem Betrieb zu loesen:** Login-/Refresh-Endpunkte besitzen
weiter keinen belastbaren Rate Limiter. Schutz muss ueber alle Instanzen gelten:
IP- und kontobezogene Limits, progressive Verzoegerung, sinnvolle 429/Retry-After,
Monitoring und vertrauenswuerdige Proxy-IP-Konfiguration. Keine permanente
Kontosperre allein anhand fremd ausloesbarer Fehlversuche. Auch die oeffentliche
Reservierung benoetigt Missbrauchsschutz. Der aktuelle fruehe Unknown-User-Pfad
hat ausserdem ein Timing-Unterscheidungsrisiko beim Login.

Kein scheinbar clusterweiter Schutz durch einen unkoordinierten In-Memory-Limiter
behauptet. Die BFF-Refresh-Koordination ist ausdruecklich kein Rate Limiter.
Frontend-Dependency-Audit (inkl. bekanntem critical-Befund) bleibt separat offen.
