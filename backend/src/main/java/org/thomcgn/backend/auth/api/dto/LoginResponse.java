package org.thomcgn.backend.auth.api.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessExpiresInSeconds,
        long refreshExpiresInSeconds,
        String role,
        String displayName
) {
}

