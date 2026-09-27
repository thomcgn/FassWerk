package org.thomcgn.backend.common.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "invalid value", "injected\r\nheader", "valid-request_123.abc"})
    void correlationIdIsSafeAndMdcIsCleared(String input) throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        if (input != null) request.addHeader("X-Request-Id", input);
        new RequestCorrelationFilter().doFilter(request, response, (req, res) -> {
            String id = MDC.get("requestId");
            assertThat(id).matches("[A-Za-z0-9._-]{1,128}");
            assertThat(req.getAttribute("requestId")).isEqualTo(id);
            if ("valid-request_123.abc".equals(input)) assertThat(id).isEqualTo(input);
        });
        assertThat(response.getHeader("X-Request-Id")).isEqualTo(request.getAttribute("requestId"));
        assertThat(MDC.get("requestId")).isNull();
    }
}
