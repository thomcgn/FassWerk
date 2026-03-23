package org.thomcgn.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.cleanup")
public record AuthCleanupProperties(
        boolean enabled,
        String cron,
        boolean cleanupExpiredRefreshTokens
) {
}

