package com.xtrmetl.etl.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Produces lowercase SHA-256 hexadecimal digests for durable ETL persistence identities.
 *
 * <p>SHA-256 is required by the Java platform. The defensive exception branch therefore indicates
 * a broken runtime rather than invalid customer input.</p>
 */
public final class Sha256Digest {

    private Sha256Digest() {
        // Utility class.
    }

    /**
     * Hashes one UTF-8 string into lowercase 64-character SHA-256 hexadecimal text.
     *
     * @param value text to hash
     * @return lowercase SHA-256 hexadecimal digest
     * @throws NullPointerException when the value is {@code null}
     * @throws IllegalStateException when the Java runtime lacks mandatory SHA-256 support
     */
    public static String digest(String value) {
        return digest(value, () -> MessageDigest.getInstance("SHA-256"));
    }

    static String digest(String value, MessageDigestFactory messageDigestFactory) {
        String requiredValue = Objects.requireNonNull(value, "value must not be null");
        MessageDigestFactory requiredFactory = Objects.requireNonNull(
                messageDigestFactory,
                "messageDigestFactory must not be null"
        );
        try {
            MessageDigest messageDigest = requiredFactory.create();
            byte[] digest = messageDigest.digest(requiredValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    @FunctionalInterface
    interface MessageDigestFactory {

        MessageDigest create() throws NoSuchAlgorithmException;
    }
}
