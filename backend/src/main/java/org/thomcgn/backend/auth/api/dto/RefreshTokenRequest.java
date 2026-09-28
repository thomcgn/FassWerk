package org.thomcgn.backend.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[REDACTED]";
    }
}
