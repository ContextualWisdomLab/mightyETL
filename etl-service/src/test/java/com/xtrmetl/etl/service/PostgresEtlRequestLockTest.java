package com.xtrmetl.etl.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Defines validation and deterministic advisory-key derivation for the PostgreSQL lock adapter.
 */
class PostgresEtlRequestLockTest {

    @Test
    void derivesTheAdvisoryKeyFromTheFirstSixtyFourHashBits() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        PostgresEtlRequestLock requestLock = new PostgresEtlRequestLock(jdbcTemplate);
        String hash = "ffffffffffffffff000000000000000000000000000000000000000000000000";

        requestLock.lock(hash);

        assertEquals("SELECT pg_advisory_xact_lock(?)", jdbcTemplate.sql);
        assertArrayEquals(new Object[]{-1L}, jdbcTemplate.arguments);
    }

    @Test
    void rejectsMissingOrMalformedHashesBeforeDatabaseAccess() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        PostgresEtlRequestLock requestLock = new PostgresEtlRequestLock(jdbcTemplate);

        assertThrows(NullPointerException.class, () -> requestLock.lock(null));
        assertThrows(IllegalArgumentException.class, () -> requestLock.lock("abcd"));
        assertThrows(
                IllegalArgumentException.class,
                () -> requestLock.lock(
                        "ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
                )
        );
        assertEquals(null, jdbcTemplate.sql);
    }

    @Test
    void rejectsMissingJdbcTemplate() {
        assertThrows(NullPointerException.class, () -> new PostgresEtlRequestLock(null));
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {

        private String sql;
        private Object[] arguments;

        @Override
        public <T> T query(
                String sql,
                ResultSetExtractor<T> resultSetExtractor,
                Object... arguments
        ) {
            this.sql = sql;
            this.arguments = arguments.clone();
            return null;
        }
    }
}
