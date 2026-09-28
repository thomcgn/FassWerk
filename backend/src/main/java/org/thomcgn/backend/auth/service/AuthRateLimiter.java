package org.thomcgn.backend.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.thomcgn.backend.auth.JwtProperties;
import org.thomcgn.backend.common.exception.ApiException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

/** Shared fixed-window limits; only HMAC fingerprints, never credentials or addresses, are stored. */
@Service
public class AuthRateLimiter {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final JwtProperties jwt;
    private final int addressLimit;
    private final int identityLimit;

    public AuthRateLimiter(JdbcTemplate jdbc, PlatformTransactionManager manager, JwtProperties jwt,
            @Value("${app.auth.rate-limit.address-per-minute:120}") int addressLimit,
            @Value("${app.auth.rate-limit.identity-per-minute:15}") int identityLimit) {
        if (addressLimit < 1 || identityLimit < 1) throw new IllegalArgumentException("Auth limits must be positive");
        this.jdbc=jdbc; this.jwt=jwt; this.addressLimit=addressLimit; this.identityLimit=identityLimit;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void check(String operation, String address, String identity) {
        boolean allowed=Boolean.TRUE.equals(transaction.execute(status -> {
            jdbc.update("delete from auth_rate_buckets where window_start < date_trunc('minute', current_timestamp) - interval '2 minutes'");
            if (!consume(operation + ":address:" + address, addressLimit)) return false;
            // Refresh credentials are case-sensitive; login account names are not.
            String normalized=operation.equals("login") ? identity.trim().toLowerCase(Locale.ROOT) : identity;
            return consume(operation + ":identity:" + normalized, identityLimit);
        }));
        // Reject outside the transaction, so failed attempts cannot roll the limit back.
        if (!allowed) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many authentication attempts; retry in one minute");
    }

    private boolean consume(String value, int limit) {
        Integer attempts=jdbc.queryForObject("""
                insert into auth_rate_buckets(bucket_key,window_start,attempts)
                values(?, date_trunc('minute', current_timestamp), 1)
                on conflict(bucket_key,window_start) do update
                set attempts=least(auth_rate_buckets.attempts + 1, ?)
                returning attempts
                """,Integer.class, fingerprint(value),limit+1);
        return attempts != null && attempts <= limit;
    }

    private String fingerprint(String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwt.secret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException("Rate limit key unavailable",e); }
    }
}
