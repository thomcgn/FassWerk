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
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityRoleMatrixIntegrationTest {

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

        AppUser admin = new AppUser();
        admin.setName("Role Admin");
        admin.setEmail("role-admin@fasswerk.local");
        admin.setPasswordHash(passwordEncoder.encode("Secret123!"));
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        appUserRepository.save(admin);

        AppUser staff = new AppUser();
        staff.setName("Role Staff");
        staff.setEmail("role-staff@fasswerk.local");
        staff.setPasswordHash(passwordEncoder.encode("Secret123!"));
        staff.setRole(UserRole.STAFF);
        staff.setActive(true);
        appUserRepository.save(staff);
    }

    @Test
    void roleMatrixForInventoryAndReports() throws Exception {
        String adminToken = loginAndGetAccessToken("role-admin@fasswerk.local", "Secret123!");
        String staffToken = loginAndGetAccessToken("role-staff@fasswerk.local", "Secret123!");

        HttpResponse<String> adminInventoryRead = send("GET", "/api/inventory", null, adminToken);
        HttpResponse<String> staffInventoryRead = send("GET", "/api/inventory", null, staffToken);
        assertEquals(200, adminInventoryRead.statusCode());
        assertEquals(200, staffInventoryRead.statusCode());

        String inventoryCreatePayload = """
                {
                  "name":"Role Matrix Item",
                  "linkedDrinkId":null,
                  "linkedDrinkVariantId":null,
                  "packageType":"BOX",
                  "packagesInStock":%s,
                  "contentPerPackage":%s,
                  "contentUnit":"MILLILITER",
                  "reorderThreshold":%s,
                  "minimumStock":%s,
                  "recommendedReorderAmount":%s,
                  "supplier":"Role Matrix Supplier",
                  "active":true
                }
                """.formatted(
                BigDecimal.valueOf(2),
                BigDecimal.valueOf(1000),
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(250),
                BigDecimal.valueOf(500)
        );

        HttpResponse<String> adminInventoryWrite = send("POST", "/api/inventory", inventoryCreatePayload, adminToken);
        HttpResponse<String> staffInventoryWrite = send("POST", "/api/inventory", inventoryCreatePayload, staffToken);
        assertEquals(201, adminInventoryWrite.statusCode());
        assertEquals(403, staffInventoryWrite.statusCode());

        HttpResponse<String> adminReport = send("GET", "/api/reports/reorder-list.pdf", null, adminToken);
        HttpResponse<String> staffReport = send("GET", "/api/reports/reorder-list.pdf", null, staffToken);
        assertEquals(200, adminReport.statusCode());
        assertEquals(403, staffReport.statusCode());
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> response = send("POST", "/api/auth/login", body, null);
        assertEquals(200, response.statusCode());
        return extractString(response.body(), "accessToken");
    }

    private String extractString(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\":\\\"([^\\\"]+)\\\"").matcher(json);
        if (!matcher.find()) {
            throw new IllegalStateException("Missing key in JSON payload: " + key + " payload=" + json);
        }
        return matcher.group(1);
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
            case "POST" -> builder.POST(body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody()).build();
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

