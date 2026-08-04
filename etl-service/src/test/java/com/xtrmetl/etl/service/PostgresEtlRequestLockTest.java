package com.xtrmetl.etl.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defines validation and deterministic non-blocking advisory-key derivation for PostgreSQL.
 */
class PostgresEtlRequestLockTest {

    private static final String HASH =
            "ffffffffffffffff000000000000000000000000000000000000000000000000";

    @Test
    void returnsTrueWhenTheTransactionAdvisoryLockIsAcquired() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate(Boolean.TRUE);
        PostgresEtlRequestLock requestLock = new PostgresEtlRequestLock(jdbcTemplate);

        boolean acquired = requestLock.tryLock(HASH);

        assertTrue(acquired);
        assertEquals("SELECT pg_try_advisory_xact_lock(?)", jdbcTemplate.sql);
        assertEquals(Boolean.class, jdbcTemplate.requiredType);
        assertArrayEquals(new Object[]{-1L}, jdbcTemplate.arguments);
    }

    @Test
    void returnsFalseImmediatelyWhenAnotherTransactionHoldsTheLock() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate(Boolean.FALSE);
        PostgresEtlRequestLock requestLock = new PostgresEtlRequestLock(jdbcTemplate);

        boolean acquired = requestLock.tryLock(HASH);

        assertFalse(acquired);
        assertEquals("SELECT pg_try_advisory_xact_lock(?)", jdbcTemplate.sql);
        assertArrayEquals(new Object[]{-1L}, jdbcTemplate.arguments);
    }

    @Test
    void rejectsUnexpectedNullDatabaseResult() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate(null);
        PostgresEtlRequestLock requestLock = new PostgresEtlRequestLock(jdbcTemplate);

        assertThrows(IllegalStateException.class, () -> requestLock.tryLock(HASH));
    }

    @Test
    void rejectsMissingOrMalformedHashesBeforeDatabaseAccess() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate(Boolean.TRUE);
        PostgresEtlRequestLock requestLock = new PostgresEtlRequestLock(jdbcTemplate);

        assertThrows(NullPointerException.class, () -> requestLock.tryLock(null));
        assertThrows(IllegalArgumentException.class, () -> requestLock.tryLock("abcd"));
        assertThrows(
                IllegalArgumentException.class,
                () -> requestLock.tryLock(
                        "ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
                )
        );
        assertNull(jdbcTemplate.sql);
    }

    @Test
    void rejectsMissingJdbcTemplate() {
        assertThrows(NullPointerException.class, () -> new PostgresEtlRequestLock(null));
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {

        private final Boolean queryResult;
        private String sql;
        private Class<?> requiredType;
        private Object[] arguments;

        private CapturingJdbcTemplate(Boolean queryResult) {
            this.queryResult = queryResult;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... arguments) {
            this.sql = sql;
            this.requiredType = requiredType;
            this.arguments = arguments.clone();
            return queryResult == null ? null : requiredType.cast(queryResult);
        }
    }
}
