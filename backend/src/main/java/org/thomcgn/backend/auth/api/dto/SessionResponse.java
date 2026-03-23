package org.thomcgn.backend.auth.api.dto;

import java.time.OffsetDateTime;

public record SessionResponse(
        Long id,
        String tokenId,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        OffsetDateTime lastUsedAt,
        String userAgent,
        String ipAddress,
        boolean current
) {
}

