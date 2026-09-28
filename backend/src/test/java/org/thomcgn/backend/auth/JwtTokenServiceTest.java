package org.thomcgn.backend.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thomcgn.backend.auth.api.dto.*;
import org.thomcgn.backend.auth.domain.AppUser;
import org.thomcgn.backend.auth.domain.UserRole;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class JwtTokenServiceTest {
    private final JwtProperties properties = new JwtProperties(
            "only-a-unit-test-signing-key-at-least-32-bytes", "test", 5, 1);
    private final JwtTokenService tokens = new JwtTokenService(properties);

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "expired", "expiration", "issuedAt", "subject", "id", "signature", "unsigned"})
    void rejectsInvalidOrMissingMandatoryClaims(String fault) {
        var builder = Jwts.builder().issuer(fault.equals("issuer") ? "other" : "test")
                .claim("tokenType", "access");
        if (!fault.equals("expiration")) builder.expiration(Date.from(Instant.now().plusSeconds(fault.equals("expired") ? -60 : 60)));
        if (!fault.equals("issuedAt")) builder.issuedAt(new Date());
        if (!fault.equals("subject")) builder.subject("test@example.test");
        if (!fault.equals("id")) builder.id("test-id");
        if (!fault.equals("unsigned")) {
            String secret = fault.equals("signature") ? "different-test-key-at-least-thirty-two-bytes" : properties.secret();
            builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)));
        }
        String token = builder.compact();
        assertThatThrownBy(() -> tokens.parseToken(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void barchefTokensCarryOnlyTheRequestedRoleAndCorrectLifetimes() {
        AppUser user = new AppUser();
        user.setEmail("barchef@example.test");
        user.setName("Barchef");
        user.setRole(UserRole.BARCHEF);
        var access = tokens.parseToken(tokens.createAccessToken(user).token());
        var refresh = tokens.parseToken(tokens.createRefreshToken(user, "refresh-id"));
        assertThat(access.get("roles")).isEqualTo(java.util.List.of("ROLE_BARCHEF"));
        assertThat(access.getExpiration().getTime() - access.getIssuedAt().getTime()).isEqualTo(300_000);
        assertThat(refresh.getExpiration().getTime() - refresh.getIssuedAt().getTime()).isEqualTo(86_400_000);
        assertThat(refresh.get("roles")).isNull();
    }

    @Test
    void secretRecordsNeverIncludeCredentialsInToString() {
        assertThat(new LoginRequest("private-email", "private-password").toString()).doesNotContain("private");
        assertThat(new RefreshTokenRequest("private-token").toString()).doesNotContain("private");
        assertThat(new LogoutRequest("private-token").toString()).doesNotContain("private");
        assertThat(new LoginResponse("private-access", "private-refresh", "Bearer", 1, 2, "ADMIN", "private-name").toString())
                .doesNotContain("private");
    }
}
