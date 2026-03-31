package org.thomcgn.backend.billing.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.thomcgn.backend.auth.domain.AppUser;
import org.thomcgn.backend.auth.domain.UserRole;
import org.thomcgn.backend.auth.repository.AppUserRepository;
import org.thomcgn.backend.auth.repository.RefreshTokenRepository;
import org.thomcgn.backend.auth.repository.RevokedAccessTokenRepository;
import org.thomcgn.backend.billing.domain.TableOrder;
import org.thomcgn.backend.billing.domain.TableOrderItem;
import org.thomcgn.backend.billing.domain.TableOrderStatus;
import org.thomcgn.backend.billing.repository.TableOrderItemRepository;
import org.thomcgn.backend.billing.repository.TableOrderRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TableOrderArchiveApiIntegrationTest {

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

    @Autowired
    private TableOrderRepository orderRepository;

    @Autowired
    private TableOrderItemRepository itemRepository;

    private HttpClient httpClient;

    @BeforeEach
    void initClient() {
        httpClient = HttpClient.newHttpClient();
    }

    @BeforeEach
    void setUpAuthUser() {
        refreshTokenRepository.deleteAll();
        revokedAccessTokenRepository.deleteAll();
        appUserRepository.deleteAll();

        AppUser admin = new AppUser();
        admin.setName("Archive Admin");
        admin.setEmail("archive-api-test@fasswerk.local");
        admin.setPasswordHash(passwordEncoder.encode("Secret123!"));
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        appUserRepository.save(admin);
    }

    @Test
    void archiveEndpoint_returns200AndFallbackData_whenArchiveContainsCorruptRelations() throws Exception {
        String accessToken = loginAndGetAccessToken("archive-api-test@fasswerk.local", "Secret123!");

        TableOrder corruptOrder = new TableOrder();
        corruptOrder.setId(900L);
        corruptOrder.setStatus(TableOrderStatus.CLOSED);
        corruptOrder.setPaid(false);
        corruptOrder.setOpenedAt(LocalDateTime.now().minusHours(1));
        corruptOrder.setClosedAt(LocalDateTime.now());

        TableOrderItem corruptItem = new TableOrderItem();
        corruptItem.setId(501L);
        corruptItem.setQuantity(2);
        corruptItem.setUnitPrice(new BigDecimal("3.50"));
        corruptItem.setTotalPrice(new BigDecimal("7.00"));
        corruptItem.setDeductedVolumeMl(new BigDecimal("500.00"));

        when(orderRepository.findAllByStatusAndPaidFalseOrderByClosedAtDesc(eq(TableOrderStatus.CLOSED))).thenReturn(List.of(corruptOrder));
        when(itemRepository.findByTableOrderId(900L)).thenReturn(List.of(corruptItem));
        when(itemRepository.getTotalByTableOrderId(900L)).thenReturn(new BigDecimal("7.00"));

        HttpResponse<String> response = send("GET", "/api/table-orders/archive?payment=UNPAID", null, accessToken);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\":900"));
        assertTrue(response.body().contains("\"tableName\":\"Unbekannter Tisch\""));
        assertTrue(response.body().contains("\"drinkLabel\":\"Geloeschte Variante\""));
        assertTrue(response.body().contains("\"drinkVariantId\":null"));
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
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class MockRepositoryConfig {

        @Bean
        @Primary
        TableOrderRepository tableOrderRepositoryMock() {
            return mock(TableOrderRepository.class);
        }

        @Bean
        @Primary
        TableOrderItemRepository tableOrderItemRepositoryMock() {
            return mock(TableOrderItemRepository.class);
        }
    }
}

