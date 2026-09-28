package org.thomcgn.backend.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Login Response contract")
public record LoginResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String refreshToken,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tokenType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long accessExpiresInSeconds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long refreshExpiresInSeconds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String role,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName
) {
    @Override
    public String toString() {
        return "LoginResponse[REDACTED]";
    }
}
