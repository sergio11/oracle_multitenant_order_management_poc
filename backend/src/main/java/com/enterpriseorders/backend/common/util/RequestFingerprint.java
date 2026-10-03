package com.enterpriseorders.backend.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Request fingerprint for idempotency (V024, closure fix C4).
 *
 * <p>SHA-256 hex over the canonical request parameters, unit-separated so
 * ("AB","C") and ("A","BC") can never collide. A retry with the same
 * idempotency key but different params gets a different fingerprint and the
 * executor rejects it with IDEMPOTENCY_KEY_CONFLICT instead of replaying the
 * first response.
 *
 * <p>Kept as a standalone utility so the application services only compose
 * their canonical parameter list and do not carry the hashing concern.
 */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    /**
     * @param parts canonical request parameters, in a stable order
     * @return 64-char lowercase hex SHA-256 over the unit-separated parts
     */
    public static String of(Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder(64);
            for (Object part : parts) {
                String text = part == null ? "" : part.toString();
                digest.update(text.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1F);
            }
            for (byte b : digest.digest()) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for idempotency fingerprint", e);
        }
    }
}
