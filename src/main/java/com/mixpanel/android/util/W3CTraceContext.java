package com.mixpanel.android.util;

import androidx.annotation.NonNull;

import java.security.SecureRandom;

/**
 * Utility class for generating W3C Trace Context traceparent headers.
 * See: https://www.w3.org/TR/trace-context/
 */
public class W3CTraceContext {
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a W3C traceparent header value.
     * Format: "00-{trace-id}-{span-id}-01"
     * where:
     * - 00 = version
     * - trace-id = 32 hex characters (128-bit random)
     * - span-id = 16 hex characters (64-bit random)
     * - 01 = trace-flags (sampled)
     *
     * @return A valid W3C traceparent header value
     */
    @NonNull
    public static String generateTraceparent() {
        return "00-" + generateTraceId() + "-" + generateSpanId() + "-01";
    }

    /**
     * Generates a 128-bit random trace ID as a 32-character hex string.
     */
    @NonNull
    private static String generateTraceId() {
        byte[] bytes = new byte[16];  // 128 bits = 16 bytes
        RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    /**
     * Generates a 64-bit random span ID as a 16-character hex string.
     */
    @NonNull
    private static String generateSpanId() {
        byte[] bytes = new byte[8];  // 64 bits = 8 bytes
        RANDOM.nextBytes(bytes);
        return bytesToHex(bytes);
    }

    /**
     * Converts a byte array to a lowercase hex string.
     */
    @NonNull
    private static String bytesToHex(@NonNull byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
