package org.thomcgn.backend.common.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || !requestId.matches("[A-Za-z0-9._-]{1,128}")) {
            requestId = UUID.randomUUID().toString();
        }

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        MDC.put(REQUEST_ID_ATTRIBUTE, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            try {
                if (response.getStatus() >= 400) {
                    Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                    log.atLevel(response.getStatus() >= 500 ? Level.ERROR : Level.WARN)
                            .addKeyValue("status", response.getStatus())
                            // Never fall back to the raw URI: it can contain a QR token.
                            .addKeyValue("route", route == null ? "UNMATCHED" : route.toString())
                            .log("http_request_failed");
                }
            } finally {
                MDC.remove(REQUEST_ID_ATTRIBUTE);
            }
        }
    }
}

