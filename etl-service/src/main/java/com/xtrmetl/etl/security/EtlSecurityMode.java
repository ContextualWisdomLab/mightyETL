package com.xtrmetl.etl.security;

import java.util.Locale;

/**
 * Supported ETL service authentication postures.
 *
 * <p>{@link #DENY} is the credential-free secure default. {@link #JWT} enables deployment-owned
 * OAuth 2.0 Resource Server validation.</p>
 */
enum EtlSecurityMode {
    DENY,
    JWT;

    /**
     * Converts external configuration into a fail-closed security mode.
     *
     * @param rawMode configured mode; null and blank values select {@link #DENY}
     * @return supported security mode
     * @throws IllegalArgumentException when the configured value is not supported
     */
    static EtlSecurityMode parse(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return DENY;
        }
        return switch (rawMode.trim().toLowerCase(Locale.ROOT)) {
            case "deny" -> DENY;
            case "jwt" -> JWT;
            default -> throw new IllegalArgumentException(
                    "Unsupported mightyETL security mode: " + rawMode
            );
        };
    }
}
