package org.thomcgn.backend.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.auth.AuthCleanupProperties;
import org.thomcgn.backend.auth.repository.RefreshTokenRepository;
import org.thomcgn.backend.auth.repository.RevokedAccessTokenRepository;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthCleanupProperties authCleanupProperties;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${app.auth.cleanup.cron:0 */30 * * * *}")
    @Transactional
    public void cleanupExpiredTokens() {
        if (!authCleanupProperties.enabled()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        long removedRevokedAccessTokens = revokedAccessTokenRepository.deleteByExpiresAtBefore(now);
        long removedRefreshTokens = 0;

        if (authCleanupProperties.cleanupExpiredRefreshTokens()) {
            removedRefreshTokens = refreshTokenRepository.deleteByExpiresAtBefore(now);
        }

        if (removedRevokedAccessTokens > 0 || removedRefreshTokens > 0) {
            meterRegistry.counter("auth.cleanup.revoked_access_tokens.removed").increment(removedRevokedAccessTokens);
            meterRegistry.counter("auth.cleanup.refresh_tokens.removed").increment(removedRefreshTokens);
            log.info(
                    "Token cleanup removed {} revoked access tokens and {} refresh tokens",
                    removedRevokedAccessTokens,
                    removedRefreshTokens
            );
        }
    }
}

