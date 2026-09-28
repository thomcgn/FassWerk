package org.thomcgn.backend.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thomcgn.backend.common.logging.SafeExceptionDetails;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.thomcgn.backend.auth.repository.AppUserRepository;
import org.thomcgn.backend.auth.repository.RevokedAccessTokenRepository;
import org.thomcgn.backend.common.api.SecurityErrorResponseWriter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenService jwtTokenService;
    private final RevokedAccessTokenRepository revokedAccessTokenRepository;
    private final AppUserRepository appUserRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                authenticate(jwtTokenService.parseToken(authHeader.substring(7)));
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
            } catch (DataAccessException exception) {
                log.atError().addKeyValue("diagnostic", SafeExceptionDetails.describe(exception))
                        .log("authentication_store_failed");
                SecurityContextHolder.clearContext();
                SecurityErrorResponseWriter.write(request, response, 503);
                return;
            }
        }
        // Never catch exceptions from downstream controllers or invoke the chain twice.
        filterChain.doFilter(request, response);
    }

    private void authenticate(Claims claims) {
        if (!JwtTokenService.TOKEN_TYPE_ACCESS.equals(claims.get(JwtTokenService.CLAIM_TOKEN_TYPE, String.class))
                || revokedAccessTokenRepository.existsByTokenIdAndExpiresAtAfter(claims.getId(), OffsetDateTime.now())) {
            SecurityContextHolder.clearContext();
            return;
        }
        var account = appUserRepository.findByEmailIgnoreCaseAndActiveTrue(claims.getSubject());
        if (account.isEmpty()) {
            SecurityContextHolder.clearContext();
            return;
        }
        // Neither stale JWT privileges nor a database promotion alone grant new token privileges.
        String currentRole = "ROLE_" + account.get().getRole().name();
        Object rawRoles = claims.get("roles");
        var authorities = rawRoles instanceof List<?> roles && roles.contains(currentRole)
                ? List.of(new SimpleGrantedAuthority(currentRole)) : List.<SimpleGrantedAuthority>of();
        User principal = new User(account.get().getEmail(), "N/A", authorities);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
