package org.thomcgn.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thomcgn.backend.auth.domain.AppUser;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    public static final String CLAIM_TOKEN_ID = "jti";

    public record AccessTokenDetails(String token, String tokenId, OffsetDateTime expiresAt) {
    }

    private final JwtProperties jwtProperties;

    public AccessTokenDetails createAccessToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.accessTokenMinutes(), ChronoUnit.MINUTES);
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_TOKEN_ID, tokenId)
                .claim("roles", List.of("ROLE_" + user.getRole().name()))
                .claim("name", user.getName())
                .signWith(signingKey())
                .compact();

        return new AccessTokenDetails(token, tokenId, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    public String createRefreshToken(AppUser user, String tokenId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(jwtProperties.refreshTokenDays(), ChronoUnit.DAYS);

        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .claim(CLAIM_TOKEN_ID, tokenId)
                .signWith(signingKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(jwtProperties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}

