package org.thomcgn.backend.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Logout Request contract")
public record LogoutRequest(
        @NotBlank String refreshToken
) {
    @Override
    public String toString() {
        return "LogoutRequest[REDACTED]";
    }
}
