package org.thomcgn.backend.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.thomcgn.backend.auth.domain.AppUser;
import org.thomcgn.backend.auth.domain.UserRole;
import org.thomcgn.backend.auth.repository.AppUserRepository;
import org.thomcgn.backend.auth.repository.RefreshTokenRepository;
import org.thomcgn.backend.auth.repository.RevokedAccessTokenRepository;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RevokedAccessTokenRepository revokedAccessTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private HttpClient httpClient;

    @BeforeEach
    void initClient() {
        httpClient = HttpClient.newHttpClient();
    }

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        revokedAccessTokenRepository.deleteAll();
        appUserRepository.deleteAll();

        AppUser user = new AppUser();
        user.setName("Test Admin");
        user.setEmail("auth-test@fasswerk.local");
        user.setPasswordHash(passwordEncoder.encode("Secret123!"));
        user.setRole(UserRole.ADMIN);
        user.setActive(true);
        appUserRepository.save(user);
    }

    @Test
    void logoutRevokesCurrentAccessToken() throws Exception {
        String loginPayload = login();
        String accessToken = extractString(loginPayload, "accessToken");
        String refreshToken = extractString(loginPayload, "refreshToken");

        HttpResponse<String> beforeLogout = send("GET", "/api/inventory", null, accessToken);
        assertEquals(200, beforeLogout.statusCode());

        HttpResponse<String> logoutResponse = send(
                "POST",
                "/api/auth/logout",
                "{\"refreshToken\":\"" + refreshToken + "\"}",
                accessToken
        );
        assertEquals(204, logoutResponse.statusCode());

        HttpResponse<String> afterLogout = send("GET", "/api/inventory", null, accessToken);
        assertEquals(403, afterLogout.statusCode());
    }

    @Test
    void refreshRotatesTokenAndRejectsOldRefreshToken() throws Exception {
        String loginPayload = login();
        String firstRefreshToken = extractString(loginPayload, "refreshToken");

        String refreshBody = "{\"refreshToken\":\"" + firstRefreshToken + "\"}";
        HttpResponse<String> firstRefresh = send("POST", "/api/auth/refresh", refreshBody, null);
        assertEquals(200, firstRefresh.statusCode());

        String rotatedRefreshToken = extractString(firstRefresh.body(), "refreshToken");

        HttpResponse<String> oldRefreshAgain = send("POST", "/api/auth/refresh", refreshBody, null);
        assertEquals(400, oldRefreshAgain.statusCode());

        HttpResponse<String> rotatedRefresh = send(
                "POST",
                "/api/auth/refresh",
                "{\"refreshToken\":\"" + rotatedRefreshToken + "\"}",
                null
        );
        assertEquals(200, rotatedRefresh.statusCode());
    }

    @Test
    void sessionsEndpointAllowsRevokingSingleSession() throws Exception {
        String loginPayload = login();
        String accessToken = extractString(loginPayload, "accessToken");

        HttpResponse<String> sessionsInitial = send("GET", "/api/auth/sessions", null, accessToken);
        assertEquals(200, sessionsInitial.statusCode());

        Long sessionId = extractFirstLong(sessionsInitial.body(), "id");
        assertTrue(sessionId > 0);

        HttpResponse<String> revoke = send("DELETE", "/api/auth/sessions/" + sessionId, null, accessToken);
        assertEquals(204, revoke.statusCode());

        HttpResponse<String> sessionsAfterRevoke = send("GET", "/api/auth/sessions", null, accessToken);
        assertEquals(200, sessionsAfterRevoke.statusCode());
        assertTrue(sessionsAfterRevoke.body().contains("[]"));
    }

    private String login() throws Exception {
        String loginBody = "{\"email\":\"auth-test@fasswerk.local\",\"password\":\"Secret123!\"}";
        HttpResponse<String> response = send("POST", "/api/auth/login", loginBody, null);
        assertEquals(200, response.statusCode());
        return response.body();
    }

    private String extractString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\":\\\"([^\\\"]+)\\\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Missing key in JSON payload: " + key + " payload=" + json);
        }
        return matcher.group(1);
    }

    private Long extractFirstLong(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\":(\\d+)").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Missing numeric key in JSON payload: " + key + " payload=" + json);
        }
        return Long.parseLong(matcher.group(1));
    }

    private HttpResponse<String> send(String method, String path, String body, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path));

        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }

        if (body != null) {
            builder.header("Content-Type", "application/json");
        }

        HttpRequest request = switch (method) {
            case "GET" -> builder.GET().build();
            case "POST" -> builder.POST(body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody()).build();
            case "DELETE" -> builder.DELETE().build();
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

