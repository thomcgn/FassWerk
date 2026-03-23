package org.thomcgn.backend.auth.service;

public record ClientMetadata(
        String userAgent,
        String ipAddress
) {
}

