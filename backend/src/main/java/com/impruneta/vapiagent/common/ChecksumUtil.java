package com.impruneta.vapiagent.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for computing SHA-256 checksums over document text.
 * Used during ingestion to detect whether a scraped page has changed
 * since the last run, avoiding unnecessary re-chunking and re-embedding.
 */
public final class ChecksumUtil {

    private ChecksumUtil() {
        // Utility class – not instantiable
    }

    /**
     * Computes the SHA-256 hex digest of the given text.
     *
     * @param text input string (must not be null)
     * @return 64-character lowercase hex string, e.g. "a3f1b2c4..."
     */
    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM specification – this branch is unreachable
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
