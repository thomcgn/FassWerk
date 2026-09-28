package org.thomcgn.backend.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Session Response contract")
public record SessionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tokenId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) OffsetDateTime lastUsedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String userAgent,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String ipAddress,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean current
) {
    @Override
    public String toString() { return "SessionResponse[REDACTED]"; }
}

