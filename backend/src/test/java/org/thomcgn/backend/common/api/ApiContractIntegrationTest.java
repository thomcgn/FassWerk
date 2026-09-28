package org.thomcgn.backend.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.thomcgn.backend.auth.JwtTokenService;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.thomcgn.backend.table.service.TableService;
import static org.mockito.Mockito.doThrow;
import org.thomcgn.backend.auth.domain.AppUser;
import org.thomcgn.backend.auth.domain.UserRole;
import org.thomcgn.backend.support.PostgresIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiContractIntegrationTest extends PostgresIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @MockitoSpyBean TableService tables;
    private String accessToken;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void fixtures() {
        AppUser admin = new AppUser();
        admin.setId(900L);
        admin.setName("API test");
        admin.setEmail("api@example.test");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        jdbc.update("""
                insert into app_users(name,email,password_hash,role,active)
                values('API test','api@example.test','not-a-login-hash','ADMIN',true)
                on conflict(email) do update set role='ADMIN', active=true
                """);
        accessToken = tokens.createAccessToken(admin).token();
        jdbc.execute("truncate table drink_categories, inventory_items, suppliers, tables restart identity cascade");
        jdbc.update("insert into drink_categories(name,sort_order,active) values('API category',0,true)");
        jdbc.update("insert into drinks(category_id,name,active) values(1,'API drink',true)");
        jdbc.update("insert into drink_variants(drink_id,display_volume_name,volume_ml,price,active) values(1,'Glass',250,3,true)");
        jdbc.update("""
                insert into inventory_items(name,linked_drink_id,linked_drink_variant_id,package_type,packages_in_stock,
                content_per_package,content_unit,total_stock_amount,reorder_threshold,minimum_stock,recommended_reorder_amount,active)
                values('API stock',1,1,'BARREL',1,10,'LITER',10,1,0,0,true)
                """);
        jdbc.update("insert into suppliers(name,active) values('API supplier',true)");
        jdbc.update("""
                insert into reorder_orders(inventory_item_id,supplier_id,ordered_quantity,ordered_unit,scheduled_delivery_date,status)
                values(1,1,2,'LITER',current_date+1,'PENDING')
                """);
        jdbc.update("insert into drink_sales_daily(drink_id,drink_variant_id,sale_date) values(1,1,current_date),(1,null,current_date)");
        jdbc.update("insert into drink_sales_weekly(drink_id,drink_variant_id,week_start_date) values(1,1,current_date)");
        jdbc.update("insert into consumption_metadata(inventory_item_id,lead_time_days,safety_stock_factor,weeks_lookback) values(1,7,2,6)");
        jdbc.update("""
                insert into reorder_calculations(inventory_item_id,calculation_date,current_stock_amount,
                weekly_average_consumption,recommended_reorder_amount,is_below_threshold)
                values(1,now(),10,1,0,true)
                """);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "GET|/api/inventory/sales/daily|400",
            "GET|/api/inventory/sales/daily?startDate=invalid&endDate=2035-01-01|400",
            "GET|/api/inventory/abc/reorder-calculation|400",
            "PATCH|/api/tables|405",
            "GET|/api/does-not-exist|403",
            "GET|/api/reservations/999999|404",
            "GET|/api/inventory/1/reorder-calculations/history?limit=-1|400",
            "GET|/api/inventory/sales/weekly/1?weeks=0|400",
            "GET|/api/inventory/sales/daily?startDate=2035-02-01&endDate=2035-01-01|400"
    })
    void requestErrorsKeepSafeConsistentEnvelope(String method, String path, int status) throws Exception {
        var response = send(method, path, null, "application/json");
        assertError(response, status);
        if (status == 405) assertThat(response.headers().firstValue("allow")).isPresent();
    }

    @ParameterizedTest
    @CsvSource({"ADMIN", "BARCHEF", "STAFF"})
    void directSaleIsAvailableToStaffAndValidatesPaymentPayload(String role) throws Exception {
        jdbc.update("update app_users set role=? where email='api@example.test'", role);
        jdbc.update("update drink_variants set use_volume_standard_price=false where id=1");
        AppUser actor = new AppUser();
        actor.setId(900L); actor.setEmail("api@example.test"); actor.setName("API test");
        actor.setRole(UserRole.valueOf(role)); actor.setActive(true);
        String token = tokens.createAccessToken(actor).token();
        String valid = "{\"paymentMethod\":\"CASH\",\"items\":[{\"drinkVariantId\":1,\"quantity\":1,\"expectedUnitPrice\":3}]}";
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/table-orders/direct"))
                .header("Content-Type", "application/json").header("Idempotency-Key", "direct-http-" + role);
        var client = HttpClient.newHttpClient();
        var anonymous = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(valid)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isIn(401, 403);
        builder.header("Authorization", "Bearer " + token);
        var invalid = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(valid.replace("\"quantity\":1", "\"quantity\":0"))).build(), HttpResponse.BodyHandlers.ofString());
        assertError(invalid, 400);
        var paid = client.send(builder.POST(HttpRequest.BodyPublishers.ofString(valid)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(paid.statusCode()).as("Direct sale response: %s", paid.body()).isEqualTo(200);
        assertThat(json.readTree(paid.body()).path("saleType").asText()).isEqualTo("DIRECT");
        assertThat(json.readTree(paid.body()).path("tableId").isNull()).isTrue();
    }

    @Test
    void malformedJsonIsBadRequestWithoutEchoingBody() throws Exception {
        var response = send("POST", "/api/tables", "{\"secret\":\"do-not-echo-this\",", "application/json");
        assertError(response, 400);
        assertThat(response.body()).doesNotContain("do-not-echo-this");
    }

    @Test
    void unsupportedContentTypeIs415() throws Exception {
        assertError(send("POST", "/api/tables", "private-input", "text/plain"), 415);
    }

    @Test
    void invalidDtoIs400() throws Exception {
        assertError(send("POST", "/api/tables", "{}", "application/json"), 400);
    }

    @Test
    void duplicateCategoryIs409WithoutDatabaseDetails() throws Exception {
        assertError(send("POST", "/api/drink-categories",
                "{\"name\":\"API category\",\"sortOrder\":0,\"active\":true}", "application/json"), 409);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/api/inventory/sales/daily?startDate=2000-01-01&endDate=2099-01-01|API drink",
            "/api/inventory/sales/weekly/1|Glass",
            "/api/inventory/1/reorder-calculation|API stock",
            "/api/inventory/1/reorder-calculations/history|API stock",
            "/api/inventory/reorder-calculations/below-threshold|API stock",
            "/api/inventory/1/consumption-metadata|leadTimeDays",
            "/api/reorder/orders/upcoming|API supplier",
            "/api/reorder/orders/by-date-range?startDate=2000-01-01&endDate=2099-01-01|API stock",
            "/api/reorder/orders/inventory/1|API supplier"
    })
    void inventoryResponsesAreMappedWithOsivDisabled(String path, String expected) throws Exception {
        var response = send("GET", path, null, "application/json");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains(expected);
    }

    @Test
    void unlinkedItemCalculationReturnsEmptySuccessInsteadOfNullPointerFailure() throws Exception {
        jdbc.update("update inventory_items set linked_drink_variant_id=null where id=1");
        var response = send("POST", "/api/inventory/1/calculate-reorder", null, "application/json");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void unexpectedServerErrorDoesNotExposeInternalDetails() throws Exception {
        doThrow(new IllegalStateException("SQL select password_hash; secret-private-value org.hibernate.Driver"))
                .when(tables).list();
        var response = send("GET", "/api/tables", null, "application/json");
        assertError(response, 500);
        assertThat(response.body()).doesNotContain("secret-private-value", "select", "Driver");
        assertThat(json.readTree(response.body()).path("message").asText()).isEqualTo("Unexpected server error");
    }

    @Test
    void malformedRefreshTokenIs401WithoutTokenEcho() throws Exception {
        var response = send("POST", "/api/auth/refresh", "{\"refreshToken\":\"private-invalid-token\"}", "application/json");
        assertError(response, 401);
        assertThat(response.body()).doesNotContain("private-invalid-token");
    }

    @Test
    void scanErrorDoesNotEchoQrTokenInPath() throws Exception {
        var response = send("POST", "/api/reservations/scan/private-qr-token", null, "application/json");
        assertError(response, 404);
        assertThat(response.body()).doesNotContain("private-qr-token");
        assertThat(json.readTree(response.body()).path("path").asText()).isEqualTo("/api/reservations/scan/{token}");
    }

    @Test
    void metadataUpdateAndReadPreserveDtoContract() throws Exception {
        var response = send("PUT", "/api/inventory/1/consumption-metadata",
                "{\"leadTimeDays\":4,\"safetyStockFactor\":1.8,\"weeksLookback\":8}", "application/json");
        assertThat(response.statusCode()).isEqualTo(200);
        var read = send("GET", "/api/inventory/1/consumption-metadata", null, "application/json");
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(json.readTree(read.body()).path("weeksLookback").asInt()).isEqualTo(8);
        assertThat(json.readTree(read.body()).path("leadTimeDays").asInt()).isEqualTo(4);
    }

    @Test
    void invalidMetadataAndTimezoneAreBadRequests() throws Exception {
        assertError(send("PUT", "/api/inventory/1/consumption-metadata",
                "{\"weeksLookback\":0,\"leadTimeDays\":-1}", "application/json"), 400);
        var response = send("PUT", "/api/inventory/configuration",
                "{\"businessTimezone\":\"private-invalid-zone\"}", "application/json");
        assertError(response, 400);
        assertThat(response.body()).doesNotContain("private-invalid-zone");
    }

    @Test
    void missingMetadataReturnsEmptyButMissingInventoryReturns404() throws Exception {
        jdbc.update("delete from consumption_metadata where inventory_item_id=1");
        var response = send("GET", "/api/inventory/1/consumption-metadata", null, "application/json");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
        assertError(send("GET", "/api/inventory/999999/consumption-metadata", null, "application/json"), 404);
        assertError(send("PUT", "/api/inventory/999999/consumption-metadata", "{}", "application/json"), 404);
    }

    @Test
    void invalidReorderRequestAndStatusAre400() throws Exception {
        assertError(send("POST", "/api/reorder/orders", "{}", "application/json"), 400);
        var response = send("PUT", "/api/reorder/orders/1/status?status=private-invalid-status", null, "application/json");
        assertError(response, 400);
        assertThat(response.body()).doesNotContain("private-invalid-status");
    }

    @Test
    void manualDayCloseRetryCannotAdvanceTwice() throws Exception {
        var previous = jdbc.queryForObject("select manual_business_date from inventory_business_settings order by id limit 1", java.time.LocalDate.class);
        try {
            jdbc.update("update inventory_business_settings set manual_business_date='2035-06-01'");
            var key = java.util.UUID.randomUUID().toString();
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/inventory/configuration/manual-day-close"))
                    .header("Authorization", "Bearer " + accessToken).header("Content-Type", "application/json")
                    .header("Idempotency-Key", key)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"expectedBusinessDate\":\"2035-06-01\"}")).build();
            var client = HttpClient.newHttpClient();
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            assertThat(jdbc.queryForObject("select manual_business_date from inventory_business_settings order by id limit 1", java.time.LocalDate.class))
                    .isEqualTo(java.time.LocalDate.of(2035, 6, 2));
        } finally { jdbc.update("update inventory_business_settings set manual_business_date=?", previous); }
    }

    @Test
    void staleInventoryFormCannotRestoreConsumedStock() throws Exception {
        var stale = inventoryForm();
        assertThat(send("POST", "/api/inventory/1/adjust", "{\"amount\":1,\"reason\":\"consumed\",\"increase\":false}", "application/json").statusCode()).isEqualTo(200);
        stale.put("name", "Older form");
        assertError(send("PUT", "/api/inventory/1", stale.toString(), "application/json"), 409);
        assertThat(jdbc.queryForObject("select total_stock_amount from inventory_items where id=1", java.math.BigDecimal.class)).isEqualByComparingTo("9");
    }

    @Test
    void metadataRoundtripPreservesOneMillilitreRemainder() throws Exception {
        jdbc.update("update inventory_items set total_stock_amount=9.999, packages_in_stock=1 where id=1");
        var form = inventoryForm();
        form.put("name", "Renamed stock");
        assertThat(send("PUT", "/api/inventory/1", form.toString(), "application/json").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select total_stock_amount from inventory_items where id=1", java.math.BigDecimal.class)).isEqualByComparingTo("9.999");
    }

    @Test
    void staleShiftFormCannotReplaceNewerCashOrWorkers() throws Exception {
        String path = "/api/shift-settlements/2040-06-01";
        jdbc.update("delete from shift_worker_entries where settlement_id in(select id from shift_settlements where settlement_date='2040-06-01')");
        jdbc.update("delete from shift_settlements where settlement_date='2040-06-01'");
        var empty = json.readTree(send("GET", path, null, "application/json").body());
        String first = "{\"openingCash\":100,\"otherExpenses\":5,\"entries\":[],\"expectedRevision\":" + empty.path("revision").asLong(0) + "}";
        assertThat(send("PUT", path, first, "application/json").statusCode()).isEqualTo(200);
        assertError(send("PUT", path, first.replace("100", "200"), "application/json"), 409);
        assertThat(json.readTree(send("GET", path, null, "application/json").body()).path("openingCash").decimalValue()).isEqualByComparingTo("100");
    }

    @Test
    void twoInventoryClientsCannotOverwriteEachOther() throws Exception {
        var firstForm = inventoryForm();
        var secondForm = firstForm.deepCopy();
        firstForm.put("name", "Client A"); secondForm.put("name", "Client B");
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> send("PUT", "/api/inventory/1", firstForm.toString(), "application/json"));
            var second = pool.submit(() -> send("PUT", "/api/inventory/1", secondForm.toString(), "application/json"));
            assertThat(java.util.List.of(first.get().statusCode(), second.get().statusCode())).containsExactlyInAnyOrder(200, 409);
            var current = json.readTree(send("GET", "/api/inventory", null, "application/json").body()).get(0);
            assertThat(current.path("revision").asLong()).isGreaterThan(firstForm.path("expectedRevision").asLong());
            assertThat(current.path("name").asText()).isEqualTo(json.readTree((first.get().statusCode() == 200 ? first : second).get().body()).path("name").asText());
            assertThat(current.path("totalStockAmount").decimalValue()).isEqualByComparingTo("10");
        }
    }

    @Test
    void twoShiftClientsConflictEvenForWorkerOnlyChanges() throws Exception {
        String path = "/api/shift-settlements/2041-06-01";
        jdbc.update("delete from shift_worker_entries where settlement_id in(select id from shift_settlements where settlement_date='2041-06-01')");
        jdbc.update("delete from shift_settlements where settlement_date='2041-06-01'");
        // Two clients first see an unsaved settlement at revision zero.
        for (int round = 0; round < 2; round++) {
            long revision = json.readTree(send("GET", path, null, "application/json").body()).path("revision").asLong();
            String body = "{\"expectedRevision\":" + revision + ",\"openingCash\":100,\"otherExpenses\":0,\"entries\":[{\"employeeName\":\"Client A\",\"shiftStart\":\"18:00\",\"shiftEnd\":\"20:00\",\"hourlyWage\":10}]}";
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var first = pool.submit(() -> send("PUT", path, body, "application/json"));
                var second = pool.submit(() -> send("PUT", path, body.replace("Client A", "Client B"), "application/json"));
                assertThat(java.util.List.of(first.get().statusCode(), second.get().statusCode())).containsExactlyInAnyOrder(200, 409);
                var current = json.readTree(send("GET", path, null, "application/json").body());
                assertThat(current.path("revision").asLong()).isEqualTo(revision + 1);
                assertThat(current.path("entries").size()).isEqualTo(1);
                assertThat(current.path("entries").get(0).path("employeeName").asText())
                    .isEqualTo(json.readTree((first.get().statusCode() == 200 ? first : second).get().body()).path("entries").get(0).path("employeeName").asText());
            }
        }
    }

    @Test
    void inventoryUpdateCannotSetStockAndStaleDeleteCannotDeactivateItem() throws Exception {
        var form = inventoryForm();
        form.put("packagesInStock", 9);
        assertError(send("PUT", "/api/inventory/1", form.toString(), "application/json"), 409);
        assertError(send("DELETE", "/api/inventory/1", null, "application/json"), 409);
        assertThat(send("POST", "/api/inventory/1/adjust", "{\"amount\":1,\"reason\":\"consume\",\"increase\":false}", "application/json").statusCode()).isEqualTo(200);
        assertError(send("DELETE", "/api/inventory/1?expectedRevision=" + form.path("expectedRevision").asLong(), null, "application/json"), 409);
        assertThat(jdbc.queryForObject("select active from inventory_items where id=1", Boolean.class)).isTrue();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode inventoryForm() throws Exception {
        var form = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(send("GET", "/api/inventory", null, "application/json").body()).get(0);
        form.put("expectedRevision", form.path("revision").asLong(0));
        form.remove(java.util.List.of("id", "revision", "totalStockAmount"));
        return form;
    }

    private void assertError(HttpResponse<String> response, int expected) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
        JsonNode body = json.readTree(response.body());
        assertThat(body.path("status").asInt()).isEqualTo(expected);
        for (String field : new String[]{"timestamp", "error", "message", "path", "requestId"}) {
            assertThat(body.path(field).asText()).as(field).isNotBlank();
        }
        assertThat(body.path("requestId").asText()).isEqualTo(response.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(response.body()).doesNotContain("org.thomcgn", "org.hibernate", "SQLException", "SQLState", "password_hash", "stackTrace");
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", contentType).header("X-Request-Id", "phase5-contract")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
