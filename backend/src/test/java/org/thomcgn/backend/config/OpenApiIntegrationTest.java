package org.thomcgn.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.thomcgn.backend.support.PostgresIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiIntegrationTest extends PostgresIntegrationTest {
    @LocalServerPort
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs.yaml"})
    void anonymousExportReturnsAnOpenApiDocument(String path) throws Exception {
        var response = get(path);
        assertThat(response.statusCode()).as("OpenAPI response for %s", path).isEqualTo(200);
        assertThat(response.body()).contains("openapi", "/api/auth/login", "/api/table-orders");
        assertThat(response.headers().firstValue("content-type").orElseThrow())
                .startsWith(path.endsWith(".yaml") ? "application/vnd.oai.openapi" : "application/json");
        assertThat(response.body()).startsWith(path.endsWith(".yaml") ? "openapi:" : "{\"openapi\":");
    }

    @Test
    void serverUrlDoesNotDependOnTheExportHostOrRandomPort() throws Exception {
        JsonNode root = new ObjectMapper().readTree(get("/v3/api-docs").body());
        assertThat(root.path("servers").size()).isEqualTo(1);
        assertThat(root.path("servers").get(0).path("url").asText()).isEqualTo("/");
        assertThat(get("/v3/api-docs.yaml").body()).contains("- url: /")
                .doesNotContain("127.0.0.1:", "localhost:");
    }

    @Test
    void jsonContractHasDocumentedOperationsAndCompleteResponseSchemas() throws Exception {
        JsonNode root = new ObjectMapper().readTree(get("/v3/api-docs").body());
        assertThat(root.path("openapi").asText()).startsWith("3.0.");

        Set<String> operationIds = new HashSet<>();
        root.path("paths").properties().forEach(path -> path.getValue().properties().forEach(method -> {
            if (!Set.of("get", "post", "put", "delete", "patch").contains(method.getKey())) return;
            JsonNode operation = method.getValue();
            assertThat(operation.path("summary").asText()).as("%s %s summary", method.getKey(), path.getKey()).isNotBlank();
            String operationId = operation.path("operationId").asText();
            assertThat(operationId).as("%s %s operationId", method.getKey(), path.getKey()).isNotBlank();
            assertThat(operationIds.add(operationId)).as("unique operationId %s", operationId).isTrue();
        }));

        JsonNode schemas = root.path("components").path("schemas");
        Set<String> schemaNames = new HashSet<>();
        schemas.properties().forEach(entry -> schemaNames.add(entry.getKey()));
        assertThat(schemaNames).contains(
                "LoginResponse", "ReservationResponse", "ReservationSettingsResponse", "TableOrderResponse",
                "InventoryItemResponse", "ShiftSettlementResponse", "RevenueOverviewResponse");
        schemas.properties().forEach(entry -> {
            JsonNode schema = entry.getValue();
            assertThat(schema.path("description").asText()).as("%s description", entry.getKey()).isNotBlank();
            if (!entry.getKey().endsWith("Response") || !schema.path("properties").isObject()) return;
            Set<String> properties = new HashSet<>();
            schema.path("properties").properties().forEach(property -> properties.add(property.getKey()));
            Set<String> required = new HashSet<>();
            schema.path("required").forEach(node -> required.add(node.asText()));
            assertThat(required).as("%s required properties", entry.getKey()).containsExactlyInAnyOrderElementsOf(properties);
        });

        assertThat(schemas.path("TableOrderResponse").path("properties").path("total").path("type").asText()).isEqualTo("number");
        assertThat(schemas.path("TableOrderResponse").path("properties").path("closedAt").path("nullable").asBoolean()).isTrue();
        assertThat(schemas.path("ReservationSettingsResponse").path("properties").path("durationMinutes").path("nullable").asBoolean()).isTrue();
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
