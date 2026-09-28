package org.thomcgn.backend.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank String refreshToken
) {
    @Override
    public String toString() {
        return "LogoutRequest[REDACTED]";
    }
}
