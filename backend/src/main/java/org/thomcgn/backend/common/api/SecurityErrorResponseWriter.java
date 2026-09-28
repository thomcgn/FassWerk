package org.thomcgn.backend.common.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/** Security filter errors use the same envelope without exposing tokens or exception details. */
public final class SecurityErrorResponseWriter {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SecurityErrorResponseWriter() {}

    public static void write(HttpServletRequest request, HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        String message = switch (status) {
            case 401 -> "Authentication required";
            case 403 -> "Access denied";
            default -> "Authentication temporarily unavailable";
        };
        String path = request.getRequestURI();
        if (path.startsWith("/api/reservations/scan/")) path = "/api/reservations/scan/{token}";
        JSON.writeValue(response.getWriter(), Map.of(
                "timestamp", Instant.now().toString(), "status", status,
                "error", status == 401 ? "Unauthorized" : status == 403 ? "Forbidden" : "Service Unavailable",
                "message", message, "path", path,
                "requestId", String.valueOf(request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE))));
    }
}
