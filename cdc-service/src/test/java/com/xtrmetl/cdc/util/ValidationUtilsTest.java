package com.xtrmetl.cdc.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationUtilsTest {

    @Test
    void requireValidPortReturnsTrimmedPort() {
        assertEquals("5432", ValidationUtils.requireValidPort("5432", "PGPORT"));
        assertEquals("5432", ValidationUtils.requireValidPort(" 5432 ", "PGPORT"));
    }

    @Test
    void requireValidPortThrowsWhenMissingOrInvalid() {
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidPort(null, "PGPORT"));
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidPort(" ", "PGPORT"));
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidPort("0", "PGPORT"));
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidPort("65536", "PGPORT"));
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidPort("not-a-port", "PGPORT"));
    }

    @Test
    void requireValidHostReturnsTrimmedHost() {
        assertEquals("replica-host", ValidationUtils.requireValidHost("replica-host", "REPLICA_PGHOST"));
        assertEquals("replica-host", ValidationUtils.requireValidHost(" replica-host ", "REPLICA_PGHOST"));
        assertEquals("127.0.0.1", ValidationUtils.requireValidHost("127.0.0.1", "REPLICA_PGHOST"));
    }

    @Test
    void requireValidHostThrowsWhenMissingOrInvalid() {
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidHost(null, "REPLICA_PGHOST"));
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidHost(" ", "REPLICA_PGHOST"));
        assertThrows(
                IllegalStateException.class,
                () -> ValidationUtils.requireValidHost("replica-host?ssl=true", "REPLICA_PGHOST")
        );
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidHost("replica/host", "REPLICA_PGHOST"));
    }

    @Test
    void requireValidIdentifierReturnsTrimmedValue() {
        assertEquals("xtrmetl", ValidationUtils.requireValidIdentifier("xtrmetl", "REPLICA_PGDATABASE"));
        assertEquals("xtrmetl", ValidationUtils.requireValidIdentifier(" xtrmetl ", "REPLICA_PGDATABASE"));
        assertEquals("xtrmetl_db", ValidationUtils.requireValidIdentifier("xtrmetl_db", "REPLICA_PGDATABASE"));
    }

    @Test
    void requireValidIdentifierThrowsWhenMissingOrInvalid() {
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidIdentifier(null, "REPLICA_PGDATABASE"));
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidIdentifier(" ", "REPLICA_PGDATABASE"));
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidIdentifier("xtrmetl?evil", "REPLICA_PGDATABASE"));
        assertThrows(IllegalStateException.class, () -> ValidationUtils.requireValidIdentifier("xtrmetl/db", "REPLICA_PGDATABASE"));
    }

    @Test
    void invalidConfigurationDiagnosticsDoNotRepublishRejectedValues() {
        String sensitiveFragment = "password=secret-8472";

        IllegalStateException hostFailure = assertThrows(
                IllegalStateException.class,
                () -> ValidationUtils.requireValidHost(
                        "replica-host?" + sensitiveFragment + "\r\nforged-log-line",
                        "REPLICA_PGHOST"
                )
        );
        IllegalStateException portFailure = assertThrows(
                IllegalStateException.class,
                () -> ValidationUtils.requireValidPort("5432?" + sensitiveFragment, "REPLICA_PGPORT")
        );
        IllegalStateException identifierFailure = assertThrows(
                IllegalStateException.class,
                () -> ValidationUtils.requireValidIdentifier(
                        "customer_db?" + sensitiveFragment,
                        "REPLICA_PGDATABASE"
                )
        );

        assertSafeDiagnostic(hostFailure, "REPLICA_PGHOST", sensitiveFragment);
        assertSafeDiagnostic(portFailure, "REPLICA_PGPORT", sensitiveFragment);
        assertSafeDiagnostic(identifierFailure, "REPLICA_PGDATABASE", sensitiveFragment);
    }

    private static void assertSafeDiagnostic(
            IllegalStateException failure,
            String expectedKey,
            String sensitiveFragment
    ) {
        assertTrue(failure.getMessage().contains(expectedKey));
        assertFalse(failure.getMessage().contains(sensitiveFragment));
        assertFalse(failure.getMessage().contains("forged-log-line"));
    }
}
