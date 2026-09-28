package org.thomcgn.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.thomcgn.backend.auth.JwtTokenService;
import org.thomcgn.backend.auth.domain.AppUser;
import org.thomcgn.backend.auth.domain.UserRole;
import org.thomcgn.backend.support.PostgresIntegrationTest;
import org.thomcgn.backend.table.service.TableService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "debug=false", "trace=false",
        "app.jwt.secret=observability-fixture-signing-key-for-disposable-tests-only",
        "app.bootstrap.admin-email=", "app.bootstrap.admin-password=",
        "app.auth.cleanup.enabled=false", "app.reservation.no-show-cron=-",
        "app.reservation.mail.enabled=false", "management.health.mail.enabled=false"
})
@ActiveProfiles("prod")
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIntegrationTest extends PostgresIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired MeterRegistry registry;
    @MockitoSpyBean TableService tables;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void healthIsMinimalAndMetricsRequireAdmin() throws Exception {
        var health = send("GET", "/actuator/health", null);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(json.readTree(health.body()).path("status").asText()).isEqualTo("UP");
        assertThat(json.readTree(health.body()).has("components")).isFalse();
        assertThat(json.readTree(health.body()).has("details")).isFalse();
        for (String endpoint : new String[]{"/actuator/metrics", "/actuator/prometheus"}) {
            assertThat(send("GET", endpoint, null).statusCode()).isEqualTo(401);
            assertThat(send("GET", endpoint, token(UserRole.STAFF)).statusCode()).isEqualTo(403);
            assertThat(send("GET", endpoint, token(UserRole.ADMIN)).statusCode()).isEqualTo(200);
        }
        var metrics = send("GET", "/actuator/prometheus", token(UserRole.ADMIN));
        assertThat(metrics.body()).contains("jvm_memory_used_bytes", "hikaricp_connections_active",
                "hikaricp_connections_pending", "http_server_requests_seconds_bucket");
        assertThat(send("GET", "/actuator/env", token(UserRole.ADMIN)).statusCode()).isEqualTo(403);
    }

    @Test
    void serverErrorHasCorrelatedJsonDiagnosticWithoutExceptionPayload(CapturedOutput output) throws Exception {
        doThrow(new IllegalStateException("private-sql-password", new RuntimeException("private-cause-value")))
                .when(tables).list();
        var response = send("GET", "/api/tables?private-query=value", token(UserRole.ADMIN));
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("X-Request-Id")).contains("phase13-observability");
        var diagnostic = event(output, "api_processing_failed");
        assertThat(diagnostic.path("requestId").asText()).isEqualTo("phase13-observability");
        assertThat(diagnostic.path("diagnostic").asText()).contains("IllegalStateException", "RuntimeException", "org.thomcgn.backend.");
        var completion = event(output, "http_request_failed");
        assertThat(completion.path("status").asInt()).isEqualTo(500);
        assertThat(completion.path("route").asText()).isEqualTo("/api/tables");
        assertThat(output.getAll()).doesNotContain("private-sql-password", "private-cause-value", "private-query", "Bearer ");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(registry.find("http.server.requests").tag("uri", "/api/tables").tag("status", "500").timer()).isNotNull());
    }

    @Test
    void reservationFailuresUseRouteTemplateInLogsAndMetrics(CapturedOutput output) throws Exception {
        var response = send("POST", "/api/reservations/scan/private-qr-observability", token(UserRole.ADMIN));
        assertThat(response.statusCode()).isEqualTo(404);
        var completion = event(output, "http_request_failed");
        assertThat(completion.path("route").asText()).isEqualTo("/api/reservations/scan/{token}");
        assertThat(output.getAll()).doesNotContain("private-qr-observability");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(registry.find("http.server.requests").tag("uri", "/api/reservations/scan/{token}")
                        .tag("status", "404").timer()).isNotNull());
        assertThat(send("GET", "/actuator/prometheus", token(UserRole.ADMIN)).body())
                .doesNotContain("private-qr-observability", "phase13-observability", "@example.test");
    }

    @Test
    void databaseConflictDoesNotLogRejectedValues(CapturedOutput output) throws Exception {
        jdbc.update("insert into drink_categories(name,sort_order,active) values(?,0,true)", "private-duplicate-category");
        var response = send("POST", "/api/drink-categories", token(UserRole.ADMIN),
                "{\"name\":\"private-duplicate-category\",\"sortOrder\":0,\"active\":true}");
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(event(output, "api_processing_failed").path("diagnostic").asText())
                .contains("DataIntegrityViolationException", "PSQLException");
        assertThat(output.getAll()).doesNotContain("private-duplicate-category", "Key (name)=");
    }

    private JsonNode event(CapturedOutput output, String message) throws Exception {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(output.getAll()).contains(message));
        String line = output.getAll().lines().filter(value -> value.startsWith("{") && value.contains(message))
                .reduce((first, last) -> last).orElseThrow();
        return json.readTree(line);
    }

    private String token(UserRole role) {
        String email = "observability-" + role.name() + "@example.test";
        jdbc.update("""
                insert into app_users(name,email,password_hash,role,active)
                values('Observability',?,'not-a-login-hash',?,true)
                on conflict(email) do update set role=excluded.role, active=true
                """, email, role.name());
        AppUser user = new AppUser();
        user.setId(jdbc.queryForObject("select id from app_users where email=?", Long.class, email));
        user.setEmail(email);
        user.setRole(role);
        user.setActive(true);
        return tokens.createAccessToken(user).token();
    }

    private HttpResponse<String> send(String method, String path, String token) throws Exception {
        return send(method, path, token, null);
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("X-Request-Id", "phase13-observability")
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
