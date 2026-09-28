package org.thomcgn.backend.common.application;

import org.thomcgn.backend.common.exception.BadRequestException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class IdempotencyKeys {
    private IdempotencyKeys() {}

    public static String optional(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z0-9._:-]{8,80}")) {
            throw new BadRequestException("Idempotency-Key must contain 8-80 safe characters");
        }
        return normalized;
    }

    public static String fingerprint(String canonicalRequest) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
