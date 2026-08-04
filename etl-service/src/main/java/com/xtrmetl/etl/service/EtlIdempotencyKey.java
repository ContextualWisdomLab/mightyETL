package com.xtrmetl.etl.service;

import org.springframework.lang.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Normalizes client idempotency keys and calculates compatibility-safe SHA-256 identifiers.
 *
 * <p>The synchronous ETL endpoint and durable asynchronous jobs share this utility so a quoted
 * RFC 9651 String and the retained legacy raw representation always identify the same semantic
 * client key. Hash composition uses explicit length prefixes to avoid ambiguous concatenation.
 * Raw keys and principal values remain caller-owned and are never retained by this class.</p>
 */
public final class EtlIdempotencyKey {

    private static final String VALUE_EXPRESSION = "[A-Za-z0-9._:-]{16,128}";
    private static final Pattern LEGACY_VALUE = Pattern.compile(VALUE_EXPRESSION);
    private static final Pattern STRUCTURED_FIELD_VALUE = Pattern.compile(
            "\"(" + VALUE_EXPRESSION + ")\""
    );

    private EtlIdempotencyKey() {
    }

    /**
     * Returns the semantic key represented by a quoted RFC 9651 String or supported legacy value.
     *
     * @param value wire representation supplied by a client
     * @return normalized safe-ASCII semantic key
     * @throws EtlRequestException when the representation is missing or outside the supported profile
     */
    public static String normalize(@Nullable String value) {
        if (value == null) {
            throw new EtlRequestException(EtlRequestError.INVALID_IDEMPOTENCY_KEY);
        }
        var structuredMatcher = STRUCTURED_FIELD_VALUE.matcher(value);
        if (structuredMatcher.matches()) {
            return structuredMatcher.group(1);
        }
        if (LEGACY_VALUE.matcher(value).matches()) {
            return value;
        }
        throw new EtlRequestException(EtlRequestError.INVALID_IDEMPOTENCY_KEY);
    }

    /**
     * Calculates the lowercase SHA-256 digest of one UTF-8 value.
     *
     * @param value non-null value to hash
     * @return lowercase 64-character hexadecimal digest
     * @throws NullPointerException when {@code value} is null
     */
    public static String sha256(String value) {
        String requiredValue = Objects.requireNonNull(value, "value must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(requiredValue.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    /**
     * Calculates the historical principal-scoped ledger hash used by synchronous ETL requests.
     *
     * <p>This exact composition is retained so existing committed ledger rows remain replayable.</p>
     *
     * @param scope authenticated principal namespace
     * @param semanticKey normalized client key
     * @return compatibility-safe scoped digest
     * @throws NullPointerException when either argument is null
     */
    public static String scopedHash(String scope, String semanticKey) {
        String requiredScope = Objects.requireNonNull(scope, "scope must not be null");
        String requiredKey = Objects.requireNonNull(semanticKey, "semanticKey must not be null");
        return sha256(requiredScope.length() + ":" + requiredScope + ":" + requiredKey);
    }

    /**
     * Calculates a domain-separated hash for a scoped value used by durable job infrastructure.
     *
     * @param domain stable hash-domain label
     * @param scope owner or namespace value
     * @param value value within that namespace
     * @return lowercase SHA-256 digest
     * @throws NullPointerException when any argument is null
     */
    public static String domainScopedHash(String domain, String scope, String value) {
        String requiredDomain = Objects.requireNonNull(domain, "domain must not be null");
        String requiredScope = Objects.requireNonNull(scope, "scope must not be null");
        String requiredValue = Objects.requireNonNull(value, "value must not be null");
        return sha256(
                requiredDomain.length() + ":" + requiredDomain + ":"
                        + requiredScope.length() + ":" + requiredScope + ":"
                        + requiredValue
        );
    }
}
