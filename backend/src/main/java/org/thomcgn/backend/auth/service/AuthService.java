package org.thomcgn.backend.auth.service;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thomcgn.backend.auth.JwtProperties;
import org.thomcgn.backend.auth.JwtTokenService;
import org.thomcgn.backend.auth.api.dto.LogoutRequest;
import org.thomcgn.backend.auth.api.dto.LoginRequest;
import org.thomcgn.backend.auth.api.dto.LoginResponse;
import org.thomcgn.backend.auth.api.dto.RefreshTokenRequest;
import org.thomcgn.backend.auth.api.dto.SessionResponse;
import org.thomcgn.backend.auth.domain.AppUser;
import org.thomcgn.backend.auth.domain.RefreshToken;
import org.thomcgn.backend.auth.domain.RevokedAccessToken;
import org.thomcgn.backend.auth.repository.AppUserRepository;
import org.thomcgn.backend.auth.repository.RefreshTokenRepository;
import org.thomcgn.backend.auth.repository.RevokedAccessTokenRepository;
import org.thomcgn.backend.common.exception.BadRequestException;
import org.thomcgn.backend.common.exception.NotFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final MeterRegistry meterRegistry;
    private final DeviceFingerprintService deviceFingerprintService;

    @Transactional
    public LoginResponse login(LoginRequest request, ClientMetadata metadata) {
        AppUser user = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(request.email())
                .orElseThrow(() -> {
                    meterRegistry.counter("auth.login.failure").increment();
                    return new BadRequestException("Invalid credentials");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            meterRegistry.counter("auth.login.failure").increment();
            throw new BadRequestException("Invalid credentials");
        }

        meterRegistry.counter("auth.login.success").increment();
        return issueTokenPair(user, metadata);
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request, ClientMetadata metadata) {
        Claims claims = parseAndValidateRefreshClaims(request.refreshToken());
        String tokenId = claims.get(JwtTokenService.CLAIM_TOKEN_ID, String.class);

        RefreshToken tokenById = refreshTokenRepository.findByTokenId(tokenId)
                .orElseThrow(() -> {
                    meterRegistry.counter("auth.refresh.failure").increment();
                    return new BadRequestException("Invalid refresh token");
                });

        if (tokenById.getRevokedAt() != null) {
            revokeAllRefreshTokensForUser(tokenById.getUser());
            meterRegistry.counter("auth.refresh.replay_detected").increment();
            log.warn("refresh_replay_detected user={} tokenId={}", tokenById.getUser().getEmail(), tokenId);
            throw new BadRequestException("Refresh token reuse detected");
        }

        RefreshToken storedToken = refreshTokenRepository.findByTokenIdAndRevokedAtIsNull(tokenId)
                .orElseThrow(() -> {
                    meterRegistry.counter("auth.refresh.failure").increment();
                    return new BadRequestException("Invalid refresh token");
                });

        if (storedToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            storedToken.setRevokedAt(OffsetDateTime.now());
            storedToken.setRevokedReason("EXPIRED");
            refreshTokenRepository.save(storedToken);
            meterRegistry.counter("auth.refresh.failure").increment();
            throw new BadRequestException("Refresh token expired");
        }

        storedToken.setLastUsedAt(OffsetDateTime.now());
        storedToken.setRevokedAt(OffsetDateTime.now());
        storedToken.setRevokedReason("ROTATED");
        refreshTokenRepository.save(storedToken);
        meterRegistry.counter("auth.refresh.success").increment();
        return issueTokenPair(storedToken.getUser(), metadata);
    }

    @Transactional
    public void logout(LogoutRequest request, String bearerAccessToken) {
        Claims claims = parseAndValidateRefreshClaims(request.refreshToken());
        String tokenId = claims.get(JwtTokenService.CLAIM_TOKEN_ID, String.class);

        refreshTokenRepository.findByTokenIdAndRevokedAtIsNull(tokenId)
                .ifPresent(token -> {
                    token.setRevokedAt(OffsetDateTime.now());
                    token.setRevokedReason("LOGOUT");
                    refreshTokenRepository.save(token);
                });

        revokeAccessToken(bearerAccessToken);
        meterRegistry.counter("auth.logout.single").increment();
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(String userEmail, String currentRefreshToken) {
        AppUser user = getActiveUserByEmail(userEmail);
        String currentRefreshTokenId = resolveRefreshTokenId(currentRefreshToken);

        return refreshTokenRepository.findByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user, OffsetDateTime.now())
                .stream()
                .map(session -> new SessionResponse(
                        session.getId(),
                        session.getTokenId(),
                        session.getCreatedAt(),
                        session.getExpiresAt(),
                        session.getLastUsedAt(),
                        session.getUserAgent(),
                        session.getIpAddress(),
                        Objects.equals(currentRefreshTokenId, session.getTokenId())
                ))
                .toList();
    }

    @Transactional
    public void revokeSession(String userEmail, Long sessionId) {
        AppUser user = getActiveUserByEmail(userEmail);
        RefreshToken session = refreshTokenRepository.findByIdAndUserAndRevokedAtIsNull(sessionId, user)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        session.setRevokedAt(OffsetDateTime.now());
        session.setRevokedReason("MANUAL");
        refreshTokenRepository.save(session);
        meterRegistry.counter("auth.session.revoke").increment();
    }

    @Transactional
    public void logoutAllSessions(String userEmail, String bearerAccessToken) {
        AppUser user = getActiveUserByEmail(userEmail);
        OffsetDateTime now = OffsetDateTime.now();
        List<RefreshToken> activeSessions = refreshTokenRepository
                .findByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user, now);

        markSessionsRevoked(activeSessions, now, "LOGOUT_ALL");
        revokeAccessToken(bearerAccessToken);
        meterRegistry.counter("auth.logout.all").increment();
    }

    private LoginResponse issueTokenPair(AppUser user, ClientMetadata metadata) {
        JwtTokenService.AccessTokenDetails accessToken = jwtTokenService.createAccessToken(user);
        String refreshTokenId = UUID.randomUUID().toString();
        String refreshTokenJwt = jwtTokenService.createRefreshToken(user, refreshTokenId);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenId(refreshTokenId);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(OffsetDateTime.now().plusDays(jwtProperties.refreshTokenDays()));
        refreshToken.setLastUsedAt(OffsetDateTime.now());
        refreshToken.setUserAgent(deviceFingerprintService.toDeviceLabel(metadata.userAgent()));
        refreshToken.setIpAddress(trimToNull(metadata.ipAddress()));
        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(
                accessToken.token(),
                refreshTokenJwt,
                "Bearer",
                jwtProperties.accessTokenMinutes() * 60,
                jwtProperties.refreshTokenDays() * 24 * 60 * 60,
                user.getRole().name(),
                user.getName()
        );
    }

    private Claims parseAndValidateRefreshClaims(String refreshToken) {
        Claims claims = jwtTokenService.parseToken(refreshToken);
        String tokenType = claims.get(JwtTokenService.CLAIM_TOKEN_TYPE, String.class);
        if (!JwtTokenService.TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new BadRequestException("Invalid refresh token");
        }
        return claims;
    }

    private AppUser getActiveUserByEmail(String userEmail) {
        return appUserRepository.findByEmailIgnoreCaseAndActiveTrue(userEmail)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private String resolveRefreshTokenId(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }

        try {
            Claims claims = parseAndValidateRefreshClaims(refreshToken);
            return claims.get(JwtTokenService.CLAIM_TOKEN_ID, String.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void revokeAllRefreshTokensForUser(AppUser user) {
        OffsetDateTime now = OffsetDateTime.now();
        List<RefreshToken> activeSessions = refreshTokenRepository
                .findByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user, now);
        markSessionsRevoked(activeSessions, now, "REFRESH_REPLAY");
    }

    private void markSessionsRevoked(List<RefreshToken> sessions, OffsetDateTime revokedAt, String reason) {
        for (RefreshToken session : sessions) {
            session.setRevokedAt(revokedAt);
            session.setRevokedReason(reason);
        }
        if (!sessions.isEmpty()) {
            refreshTokenRepository.saveAll(sessions);
        }
    }

    private void revokeAccessToken(String bearerAccessToken) {
        if (bearerAccessToken == null || bearerAccessToken.isBlank()) {
            return;
        }

        try {
            Claims claims = jwtTokenService.parseToken(bearerAccessToken);
            String tokenType = claims.get(JwtTokenService.CLAIM_TOKEN_TYPE, String.class);
            if (!JwtTokenService.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                return;
            }

            String tokenId = claims.get(JwtTokenService.CLAIM_TOKEN_ID, String.class);
            if (tokenId == null || tokenId.isBlank()) {
                return;
            }

            revokedAccessTokenRepository.findByTokenId(tokenId).ifPresentOrElse(existing -> {
            }, () -> {
                RevokedAccessToken revoked = new RevokedAccessToken();
                revoked.setTokenId(tokenId);
                revoked.setExpiresAt(claims.getExpiration().toInstant().atOffset(ZoneOffset.UTC));
                revoked.setRevokedAt(OffsetDateTime.now());
                revokedAccessTokenRepository.save(revoked);
            });
        } catch (Exception ignored) {
            // Ignore malformed or expired access tokens during logout/revoke flows.
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

