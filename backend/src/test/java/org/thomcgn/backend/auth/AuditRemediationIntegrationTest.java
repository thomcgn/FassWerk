package org.thomcgn.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.thomcgn.backend.inventory.service.SalesConfigurationService;
import org.thomcgn.backend.support.PostgresIntegrationTest;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuditRemediationIntegrationTest extends PostgresIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PasswordEncoder passwords;
    @Autowired SalesConfigurationService settings;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired JwtProperties jwtProperties;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void fixture() {
        jdbc.execute("truncate app_users restart identity cascade");
        jdbc.update("insert into app_users(name,email,password_hash,role,active) values('Audit','audit@example.test',?,'ADMIN',true)", passwords.encode("Audit-test-password!"));
    }
    @Test void twoLimiterInstancesShareDatabaseCountsAndStoreNoIdentifiers() {
        var left = new org.thomcgn.backend.auth.service.AuthRateLimiter(jdbc, transactionManager, jwtProperties, 100, 2);
        var right = new org.thomcgn.backend.auth.service.AuthRateLimiter(jdbc, transactionManager, jwtProperties, 100, 2);
        boolean checked = false;
        for (int attempt = 0; attempt < 3 && !checked; attempt++) {
            jdbc.execute("truncate auth_rate_buckets");
            var window = jdbc.queryForObject("select date_trunc('minute', current_timestamp)::text", String.class);
            left.check("login", "address-one", "private@example.test");
            right.check("login", "address-two", "PRIVATE@example.test");
            boolean denied = false;
            try { left.check("login", "address-three", "private@example.test"); }
            catch (org.thomcgn.backend.common.exception.ApiException e) {
                assertThat(e.getStatus().value()).isEqualTo(429); denied = true;
            }
            var after = jdbc.queryForObject("select date_trunc('minute', current_timestamp)::text", String.class);
            if (window.equals(after)) { assertThat(denied).isTrue(); checked = true; }
        }
        assertThat(checked).as("A complete shared-limit check within one database minute").isTrue();
        assertThat(jdbc.queryForList("select bucket_key from auth_rate_buckets", String.class))
                .allMatch(key -> key.matches("[0-9a-f]{64}"));
    }

    @Test void wrongUnknownAndInactiveAccountsAllPerformPasswordVerification() throws Exception {
        org.mockito.Mockito.clearInvocations(passwords);
        assertThat(send("POST", "/api/auth/login", "{\"email\":\"audit@example.test\",\"password\":\"wrong\"}", null, false).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/auth/login", "{\"email\":\"unknown@example.test\",\"password\":\"wrong\"}", null, false).statusCode()).isEqualTo(401);
        jdbc.update("update app_users set active=false");
        assertThat(send("POST", "/api/auth/login", "{\"email\":\"audit@example.test\",\"password\":\"wrong\"}", null, false).statusCode()).isEqualTo(401);
        org.mockito.Mockito.verify(passwords, org.mockito.Mockito.times(3)).matches(
                org.mockito.ArgumentMatchers.eq("wrong"), org.mockito.ArgumentMatchers.startsWith("$2"));
    }

    @Test void upgradeRevokedTokenCannotInvalidateANewLogin() throws Exception {
        var legacy = login(false);
        jdbc.update("update refresh_tokens set revoked_at=current_timestamp, revoked_reason='SECURITY_UPGRADE' where revoked_at is null");
        var current = login(true);
        assertThat(refresh(legacy).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/tables", null, legacy.path("accessToken").asText(), false).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/tables", null, current.path("accessToken").asText(), false).statusCode()).isEqualTo(200);
        assertThat(refresh(current).statusCode()).isEqualTo(200);
    }

    @Test void logoutAllInvalidatesEveryPreviouslyIssuedAccessToken() throws Exception {
        var first = login(false); var second = login(false);
        assertThat(send("POST", "/api/auth/logout-all", null, first.path("accessToken").asText(), false).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/tables", null, second.path("accessToken").asText(), false).statusCode()).isEqualTo(401);
    }
    @Test void logoutWithRotatedPredecessorRevokesSuccessorAndOldAccess() throws Exception {
        var first = login(false);
        var next = json.readTree(send("POST", "/api/auth/refresh", tokenBody(first), null, false).body());
        assertThat(send("POST", "/api/auth/logout", tokenBody(first), null, false).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/tables", null, next.path("accessToken").asText(), false).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/auth/refresh", tokenBody(next), null, false).statusCode()).isEqualTo(401);
    }
    @Test void browserSessionSurvivesParallelRefreshButNeverLogout() throws Exception {
        var first = login(true);
        var left = java.util.concurrent.CompletableFuture.supplyAsync(() -> refresh(first));
        var right = java.util.concurrent.CompletableFuture.supplyAsync(() -> refresh(first));
        var a = left.get(); var b = right.get();
        assertThat(a.statusCode()).isEqualTo(200); assertThat(b.statusCode()).isEqualTo(200);
        assertThat(json.readTree(a.body()).path("refreshToken").asText()).isEqualTo(first.path("refreshToken").asText());
        send("POST", "/api/auth/logout", tokenBody(first), null, false);
        assertThat(send("GET", "/api/tables", null, json.readTree(b.body()).path("accessToken").asText(), false).statusCode()).isEqualTo(401);
        assertThat(refresh(first).statusCode()).isEqualTo(401);
    }
    @Test void allSalesConfigurationFieldsSurviveReadAfterWrite() {
        var before = settings.getConfiguration();
        try {
            settings.updateConfiguration(new SalesConfigurationService.SalesConfigurationDto(9, new BigDecimal("2.25"), 11,
                    before.businessTimezone(), before.businessDayEndsAt(), before.manualBusinessDate()));
            var actual = settings.getConfiguration();
            assertThat(actual.weeksLookback()).isEqualTo(9);
            assertThat(actual.defaultSafetyFactor()).isEqualByComparingTo("2.25");
            assertThat(actual.defaultLeadTimeDays()).isEqualTo(11);
        } finally { settings.updateConfiguration(before); }
    }
    private HttpResponse<String> refresh(JsonNode payload) {
        try { return send("POST", "/api/auth/refresh", tokenBody(payload), null, false); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private String tokenBody(JsonNode p) { return "{\"refreshToken\":\"" + p.path("refreshToken").asText() + "\"}"; }
    private JsonNode login(boolean browser) throws Exception {
        var r=send("POST", "/api/auth/login", "{\"email\":\"audit@example.test\",\"password\":\"Audit-test-password!\"}", null, browser);
        assertThat(r.statusCode()).isEqualTo(200); return json.readTree(r.body());
    }
    private HttpResponse<String> send(String method, String path, String body, String access, boolean browser) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");
        if(access!=null)b.header("Authorization","Bearer "+access);
        if(browser)b.header("X-Auth-Session","browser");
        return HttpClient.newHttpClient().send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
