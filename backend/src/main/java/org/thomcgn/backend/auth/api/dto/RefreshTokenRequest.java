package org.thomcgn.backend.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh Token Request contract")
public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[REDACTED]";
    }
}
