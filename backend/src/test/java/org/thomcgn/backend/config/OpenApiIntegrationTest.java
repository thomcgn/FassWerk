package org.thomcgn.backend.config;

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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiIntegrationTest extends PostgresIntegrationTest {
    @LocalServerPort
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs.yaml"})
    void anonymousExportReturnsAnOpenApiDocument(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("OpenAPI response for %s", path).isEqualTo(200);
        assertThat(response.body()).contains("openapi", "/api/auth/login", "/api/table-orders");
        assertThat(response.headers().firstValue("content-type").orElseThrow())
                .startsWith(path.endsWith(".yaml") ? "application/vnd.oai.openapi" : "application/json");
        assertThat(response.body()).startsWith(path.endsWith(".yaml") ? "openapi:" : "{\"openapi\":");
    }
}
