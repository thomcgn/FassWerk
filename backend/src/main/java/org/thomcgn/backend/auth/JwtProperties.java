package org.thomcgn.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        long accessTokenMinutes,
        long refreshTokenDays
) {
    public JwtProperties {
        if (secret == null || secret.isBlank() || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        if (issuer == null || issuer.isBlank() || accessTokenMinutes <= 0 || refreshTokenDays <= 0) {
            throw new IllegalArgumentException("JWT issuer and positive token lifetimes are required");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=REDACTED]";
    }
}

