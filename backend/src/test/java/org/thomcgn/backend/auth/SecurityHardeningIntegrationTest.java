package org.thomcgn.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.thomcgn.backend.auth.domain.AppUser;
import org.thomcgn.backend.auth.domain.UserRole;
import org.thomcgn.backend.auth.repository.AppUserRepository;
import org.thomcgn.backend.auth.service.AuthService;
import org.thomcgn.backend.auth.service.ClientMetadata;
import org.thomcgn.backend.auth.api.dto.LoginRequest;
import org.thomcgn.backend.auth.api.dto.RefreshTokenRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.thomcgn.backend.support.PostgresIntegrationTest;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityHardeningIntegrationTest extends PostgresIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired JwtTokenService tokens;
    @Autowired JwtProperties properties;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthService auth;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;
    private AppUser admin;
    private AppUser staff;
    private AppUser barchef;
    private final ObjectMapper json = new ObjectMapper();
    private final ClientMetadata metadata = new ClientMetadata("Security test", "127.0.0.1");

    @BeforeEach
    void fixtures() {
        jdbc.execute("truncate table app_users restart identity cascade");
        admin = user("admin@example.test", UserRole.ADMIN);
        staff = user("staff@example.test", UserRole.STAFF);
        barchef = user("barchef@example.test", UserRole.BARCHEF);
    }

    private AppUser user(String email, UserRole role) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setName("Security test");
        user.setRole(role);
        user.setActive(true);
        user.setPasswordHash(encoder.encode("Only-a-security-test"));
        return users.save(user);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "POST|/api/reorder/suppliers|400",
            "PUT|/api/reorder/suppliers/999999|400",
            "POST|/api/reorder/orders|400",
            "PUT|/api/reorder/orders/999999/status?status=PENDING|404",
            "POST|/api/inventory/999999/calculate-reorder|404",
            "POST|/api/inventory/configuration/manual-day-close|200",
            "PUT|/api/inventory/configuration|400",
            "GET|/api/volume-prices|200",
            "GET|/actuator/metrics|200"
    })
    void administrativeEndpointsEnforceRoles(String method, String path, int allowedStatus) throws Exception {
        assertError(send(method, path, null), 401);
        assertError(send(method, path, tokens.createAccessToken(users.findById(staff.getId()).orElseThrow()).token()), 403);
        assertThat(send(method, path, tokens.createAccessToken(users.findById(admin.getId()).orElseThrow()).token()).statusCode()).isEqualTo(allowedStatus);
        boolean barManagement = path.startsWith("/api/reorder/")
                || path.contains("calculate-reorder") || path.contains("manual-day-close");
        assertThat(send(method, path, tokens.createAccessToken(users.findById(barchef.getId()).orElseThrow()).token()).statusCode())
                .isEqualTo(barManagement ? allowedStatus : 403);
    }

    @ParameterizedTest
    @CsvSource(value = {"/api/tables", "/api/inventory", "/api/reorder/suppliers", "/api/auth/sessions",
            "/api/reservations", "/api/table-orders/archive", "/api/reports/revenue-overview",
            "/api/shift-settlements?from=2035-01-01&to=2035-01-02"})
    void operationalReadsRequireKnownRole(String path) throws Exception {
        assertError(send("GET", path, null), 401);
        assertError(send("GET", path, signed("test-issuer", "ROLE_OTHER", true, "access")), 403);
        assertThat(send("GET", path, tokens.createAccessToken(users.findById(staff.getId()).orElseThrow()).token()).statusCode()).isEqualTo(200);
        assertThat(send("GET", path, tokens.createAccessToken(users.findById(barchef.getId()).orElseThrow()).token()).statusCode()).isEqualTo(200);
    }

    @Test
    void invalidTokenClaimsAndDeactivatedAccountCannotAuthenticate() throws Exception {
        for (String token : List.of("malformed-private-token",
                signed("wrong-issuer", "ROLE_ADMIN", true, "access"),
                signed("test-issuer", "ROLE_ADMIN", false, "access"),
                signed("test-issuer", "ROLE_ADMIN", true, "refresh"))) {
            assertError(send("GET", "/api/inventory", token), 401);
        }
        String access = tokens.createAccessToken(users.findById(admin.getId()).orElseThrow()).token();
        admin.setActive(false);
        users.save(admin);
        assertError(send("GET", "/api/inventory", access), 401);
    }

    @Test
    void roleDowngradeTakesEffectForExistingToken() throws Exception {
        String access = tokens.createAccessToken(users.findById(admin.getId()).orElseThrow()).token();
        admin.setRole(UserRole.STAFF);
        users.save(admin);
        assertError(send("GET", "/actuator/metrics", access), 403);
    }

    @Test
    void replayCommitsRevocationOfSuccessor() {
        var first = auth.login(new LoginRequest(admin.getEmail(), "Only-a-security-test"), metadata);
        var next = auth.refresh(new RefreshTokenRequest(first.refreshToken()), metadata);
        assertThatThrownBy(() -> auth.refresh(new RefreshTokenRequest(first.refreshToken()), metadata))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where revoked_at is null", Integer.class)).isZero();
        assertThatThrownBy(() -> auth.refresh(new RefreshTokenRequest(next.refreshToken()), metadata))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void inactiveUserCannotRefresh() {
        var first = auth.login(new LoginRequest(admin.getEmail(), "Only-a-security-test"), metadata);
        admin.setActive(false);
        users.save(admin);
        assertThatThrownBy(() -> auth.refresh(new RefreshTokenRequest(first.refreshToken()), metadata))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void unknownRoutesAreDeniedAndSecurityErrorsHaveSafeEnvelopeAndHeaders() throws Exception {
        assertError(send("GET", "/api/not-a-registered-resource", tokens.createAccessToken(users.findById(admin.getId()).orElseThrow()).token()), 403);
        var response = send("GET", "/api/inventory", null);
        assertError(response, 401);
        assertThat(response.headers().firstValue("www-authenticate")).contains("Bearer");
        assertThat(response.headers().firstValue("x-content-type-options")).contains("nosniff");
        assertThat(response.headers().firstValue("x-frame-options")).contains("DENY");
    }

    @Test
    void anotherUsersSessionCannotBeRevoked() throws Exception {
        auth.login(new LoginRequest(staff.getEmail(), "Only-a-security-test"), metadata);
        Long id = jdbc.queryForObject("select id from refresh_tokens where user_id=?", Long.class, staff.getId());
        assertError(send("DELETE", "/api/auth/sessions/" + id, tokens.createAccessToken(users.findById(admin.getId()).orElseThrow()).token()), 404);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where revoked_at is null", Integer.class)).isEqualTo(1);
    }

    @Test
    void barchefLoginReturnsRoleAndCanCreateSupplierButNotEditCatalog() throws Exception {
        var login = auth.login(new LoginRequest(barchef.getEmail(), "Only-a-security-test"), metadata);
        assertThat(login.role()).isEqualTo("BARCHEF");
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/reorder/suppliers"))
                .header("Authorization", "Bearer " + login.accessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Barchef supplier\"}")).build();
        assertThat(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        assertError(send("POST", "/api/drinks", login.accessToken()), 403);
    }

    @Test
    void healthIsPublicButDetailsAndCrossOriginCredentialsAreNotExposed() throws Exception {
        var health = send("GET", "/actuator/health", null);
        assertThat(health.statusCode()).isIn(200, 503);
        assertThat(health.body()).doesNotContain("components", "jdbc", "password", "details");
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/inventory"))
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "PUT")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        assertThat(response.statusCode()).isIn(401, 403);
    }

    @Test
    void failedSuccessorPersistenceRollsBackRotation() {
        var login = auth.login(new LoginRequest(admin.getEmail(), "Only-a-security-test"), metadata);
        assertThatThrownBy(() -> auth.refresh(new RefreshTokenRequest(login.refreshToken()),
                new ClientMetadata("Security test", "x".repeat(100))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where revoked_at is null", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isEqualTo(1);
    }

    @Test
    void allRegisteredBusinessEndpointsEnforceTheRoleMatrix() throws Exception {
        // Logout-all intentionally revokes the supplied access token; issue fresh tokens per probe.

        int checked = 0;
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getPackageName().startsWith("org.thomcgn.backend.")) continue;
            for (String pattern : entry.getKey().getPatternValues()) {
                for (var verb : entry.getKey().getMethodsCondition().getMethods()) {
                    String method = verb.name();
                    String path = pattern.replace("{date}", "2035-01-01").replaceAll("\\{[^}]+}", "999999");
                    boolean publicEndpoint = method.equals("POST")
                            && List.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/reservations").contains(pattern)
                            || method.equals("GET") && List.of("/api/drinks", "/api/drink-categories", "/api/drink-variants", "/api/reservations/settings").contains(pattern);
                    if (publicEndpoint) {
                        assertThat(send(method, path, null).statusCode()).as("%s %s public", method, path).isNotIn(401, 403);
                        continue;
                    }
                    assertThat(send(method, path, null).statusCode()).as("%s %s anonymous", method, path).isEqualTo(401);
                    assertThat(send(method, path, signed("test-issuer", "ROLE_OTHER", true, "access")).statusCode()).as("%s %s unknown role", method, path).isEqualTo(403);
                    assertThat(send(method, path, tokens.createAccessToken(users.findById(admin.getId()).orElseThrow()).token()).statusCode()).as("%s %s ADMIN", method, path).isNotIn(401, 403);
                    boolean adminOnly = pattern.startsWith("/api/volume-prices")
                            || pattern.startsWith("/api/drink")
                            || pattern.startsWith("/api/reports/") && !pattern.endsWith("revenue-overview")
                            || pattern.startsWith("/api/inventory") && !method.equals("GET");
                    boolean barManagement = pattern.startsWith("/api/reorder") && !method.equals("GET")
                            || pattern.endsWith("/calculate-reorder") || pattern.endsWith("/manual-day-close");
                    boolean staffAllowed = !adminOnly && !barManagement;
                    boolean barchefAllowed = !adminOnly || barManagement;
                    int staffStatus = send(method, path, tokens.createAccessToken(users.findById(staff.getId()).orElseThrow()).token()).statusCode();
                    int barchefStatus = send(method, path, tokens.createAccessToken(users.findById(barchef.getId()).orElseThrow()).token()).statusCode();
                    if (staffAllowed) assertThat(staffStatus).as("%s %s STAFF", method, path).isNotIn(401, 403);
                    else assertThat(staffStatus).as("%s %s STAFF", method, path).isEqualTo(403);
                    if (barchefAllowed) assertThat(barchefStatus).as("%s %s BARCHEF", method, path).isNotIn(401, 403);
                    else assertThat(barchefStatus).as("%s %s BARCHEF", method, path).isEqualTo(403);
                    checked++;
                }
            }
        }
        assertThat(checked).isGreaterThan(60);
    }

    private String signed(String issuer, String role, boolean expires, String type) {
        var builder = Jwts.builder().issuer(issuer).subject(admin.getEmail())
                .issuedAt(new Date()).id(UUID.randomUUID().toString())
                .claim("roles", List.of(role)).claim("tokenType", type)
                .claim("accessVersion", users.findById(admin.getId()).orElseThrow().getAccessVersion());
        if (expires) builder.expiration(Date.from(Instant.now().plusSeconds(60)));
        return builder.signWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8))).compact();
    }

    private void assertError(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        var body = json.readTree(response.body());
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("requestId").asText()).isEqualTo("phase6-security");
        assertThat(response.body()).doesNotContain("malformed-private-token", "org.thomcgn", "Exception");
    }

    private HttpResponse<String> send(String method, String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Request-Id", "phase6-security")
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.method(method, HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
