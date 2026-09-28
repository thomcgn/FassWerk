package org.thomcgn.backend.auth.service;

import io.jsonwebtoken.Claims;
import org.thomcgn.backend.auth.AuthenticationRejectedException;
import io.jsonwebtoken.JwtException;
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
    private final jakarta.persistence.EntityManager entityManager;

    private String dummyPasswordHash;

    @jakarta.annotation.PostConstruct
    void preparePasswordCheck() {
        dummyPasswordHash = passwordEncoder.encode("non-secret-dummy-password-for-equal-cost-check");
    }

    @Transactional
    public LoginResponse login(LoginRequest request, ClientMetadata metadata) {
        return login(request, metadata, false);
    }

    @Transactional
    public LoginResponse login(LoginRequest request, ClientMetadata metadata, boolean browser) {
        var candidate = appUserRepository.lockByEmail(request.email()).filter(AppUser::isActive);
        boolean lengthValid = request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
        boolean matches = passwordEncoder.matches(lengthValid ? request.password() : "oversized-input",
                candidate.map(AppUser::getPasswordHash).orElse(dummyPasswordHash));
        if (candidate.isEmpty() || !lengthValid || !matches) {
            meterRegistry.counter("auth.login.failure").increment();
            throw new AuthenticationRejectedException("Invalid credentials");
        }
        meterRegistry.counter("auth.login.success").increment();
        return issueTokenPair(candidate.get(), metadata, UUID.randomUUID().toString(), browser);
    }

    @Transactional(noRollbackFor = AuthenticationRejectedException.class)
    public LoginResponse refresh(RefreshTokenRequest request, ClientMetadata metadata) {
        if (isBrowserToken(request.refreshToken())) return refreshBrowser(request.refreshToken());
        Claims claims = parseAndValidateRefreshClaims(request.refreshToken());
        String tokenId = claims.get(JwtTokenService.CLAIM_TOKEN_ID, String.class);

        // Serialize all token mutations for the account, including replay revocation.
        AppUser user = lockActiveUser(claims.getSubject());
        RefreshToken tokenById = refreshTokenRepository.findByTokenId(tokenId)
                .orElseThrow(() -> {
                    meterRegistry.counter("auth.refresh.failure").increment();
                    return new AuthenticationRejectedException("Invalid refresh token");
                });

        if (!tokenById.getUser().getId().equals(user.getId())) {
            throw new AuthenticationRejectedException("Invalid refresh token");
        }
        if (tokenById.getRevokedAt() != null) {
            if ("SECURITY_UPGRADE".equals(tokenById.getRevokedReason())) {
                // Stale pre-upgrade cookies must not revoke sessions created by a new login.
                meterRegistry.counter("auth.refresh.failure").increment();
                throw new AuthenticationRejectedException("Session expired; sign in again");
            }
            revokeAllRefreshTokensForUser(tokenById.getUser());
            meterRegistry.counter("auth.refresh.replay_detected").increment();
            log.warn("refresh_replay_detected");
            throw new AuthenticationRejectedException("Refresh token reuse detected");
        }

        RefreshToken storedToken = tokenById;

        if (!storedToken.getExpiresAt().isAfter(OffsetDateTime.now())) {
            storedToken.setRevokedAt(OffsetDateTime.now());
            storedToken.setRevokedReason("EXPIRED");
            refreshTokenRepository.save(storedToken);
            meterRegistry.counter("auth.refresh.failure").increment();
            throw new AuthenticationRejectedException("Refresh token expired");
        }

        storedToken.setLastUsedAt(OffsetDateTime.now());
        storedToken.setRevokedAt(OffsetDateTime.now());
        storedToken.setRevokedReason("ROTATED");
        refreshTokenRepository.save(storedToken);
        meterRegistry.counter("auth.refresh.success").increment();
        return issueTokenPair(storedToken.getUser(), metadata, storedToken.getFamilyId(), false);
    }

    @Transactional
    public void logout(LogoutRequest request, String bearerAccessToken) {
        String id = refreshIdentifier(request.refreshToken());
        var existing = refreshTokenRepository.findByTokenId(id);
        if (existing.isPresent()) {
            AppUser user = lockActiveUser(existing.get().getUser().getEmail());
            markSessionsRevoked(refreshTokenRepository.findByUserAndFamilyId(user, existing.get().getFamilyId()),
                    OffsetDateTime.now(), "LOGOUT");
            invalidateAccess(user);
        }

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
        AppUser user = lockActiveUser(userEmail);
        RefreshToken session = refreshTokenRepository.findByIdAndUserAndRevokedAtIsNull(sessionId, user)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        markSessionsRevoked(refreshTokenRepository.findByUserAndFamilyId(user, session.getFamilyId()),
                OffsetDateTime.now(), "MANUAL");
        invalidateAccess(user);
        meterRegistry.counter("auth.session.revoke").increment();
    }

    @Transactional
    public void logoutAllSessions(String userEmail, String bearerAccessToken) {
        AppUser user = lockActiveUser(userEmail);
        OffsetDateTime now = OffsetDateTime.now();
        List<RefreshToken> activeSessions = refreshTokenRepository
                .findByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user, now);

        markSessionsRevoked(activeSessions, now, "LOGOUT_ALL");
        invalidateAccess(user);
        revokeAccessToken(bearerAccessToken);
        meterRegistry.counter("auth.logout.all").increment();
    }

    private LoginResponse issueTokenPair(AppUser user, ClientMetadata metadata, String familyId, boolean browser) {
        JwtTokenService.AccessTokenDetails accessToken = jwtTokenService.createAccessToken(user, familyId);
        String refreshTokenId = UUID.randomUUID().toString();
        String refreshTokenJwt = browser ? "fw_" + java.util.HexFormat.of().formatHex(randomBytes())
                : jwtTokenService.createRefreshToken(user, refreshTokenId);
        if (browser) refreshTokenId = hashBrowserToken(refreshTokenJwt);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenId(refreshTokenId);
        refreshToken.setFamilyId(familyId);
        refreshToken.setBrowserSession(browser);
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

    private void invalidateAccess(AppUser user) {
        user.setAccessVersion(user.getAccessVersion() + 1);
        appUserRepository.save(user);
    }

    private static byte[] randomBytes() {
        byte[] value = new byte[32];
        new java.security.SecureRandom().nextBytes(value);
        return value;
    }

    private static boolean isBrowserToken(String token) {
        return token != null && token.matches("fw_[0-9a-f]{64}");
    }

    private static String hashBrowserToken(String token) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private String refreshIdentifier(String token) {
        return isBrowserToken(token) ? hashBrowserToken(token) : parseAndValidateRefreshClaims(token).getId();
    }

    private LoginResponse refreshBrowser(String rawToken) {
        RefreshToken session = refreshTokenRepository.findByTokenId(hashBrowserToken(rawToken))
                .orElseThrow(() -> new AuthenticationRejectedException("Invalid session"));
        AppUser user = lockActiveUser(session.getUser().getEmail());
        // Reload after acquiring the account lock: logout may have committed while we waited.
        entityManager.refresh(session);
        if (!session.isBrowserSession() || session.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(OffsetDateTime.now())) {
            throw new AuthenticationRejectedException("Invalid session");
        }
        var access = jwtTokenService.createAccessToken(user, session.getFamilyId());
        session.setLastUsedAt(OffsetDateTime.now());
        meterRegistry.counter("auth.refresh.success").increment();
        return new LoginResponse(access.token(), rawToken, "Bearer", jwtProperties.accessTokenMinutes() * 60,
                Math.max(0, java.time.Duration.between(OffsetDateTime.now(), session.getExpiresAt()).toSeconds()),
                user.getRole().name(), user.getName());
    }

    private Claims parseAndValidateRefreshClaims(String refreshToken) {
        Claims claims;
        try {
            claims = jwtTokenService.parseToken(refreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AuthenticationRejectedException("Invalid refresh token");
        }
        String tokenType = claims.get(JwtTokenService.CLAIM_TOKEN_TYPE, String.class);
        if (!JwtTokenService.TOKEN_TYPE_REFRESH.equals(tokenType)) {
            throw new AuthenticationRejectedException("Invalid refresh token");
        }
        return claims;
    }

    private AppUser lockActiveUser(String email) {
        AppUser user = appUserRepository.lockByEmail(email)
                .orElseThrow(() -> new AuthenticationRejectedException("Invalid credentials"));
        entityManager.refresh(user);
        if (!user.isActive()) throw new AuthenticationRejectedException("Invalid credentials");
        return user;
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
            return refreshIdentifier(refreshToken);
        } catch (AuthenticationRejectedException ignored) {
            return null;
        }
    }

    private void revokeAllRefreshTokensForUser(AppUser user) {
        OffsetDateTime now = OffsetDateTime.now();
        List<RefreshToken> activeSessions = refreshTokenRepository
                .findByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(user, now);
        markSessionsRevoked(activeSessions, now, "REFRESH_REPLAY");
        invalidateAccess(user);
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
        } catch (JwtException | IllegalArgumentException ignored) {
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

