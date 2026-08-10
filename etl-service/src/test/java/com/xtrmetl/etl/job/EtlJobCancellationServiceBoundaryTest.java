package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers fail-closed cancellation validation, transaction, and race classification boundaries.
 */
class EtlJobCancellationServiceBoundaryTest {

    private static final UUID JOB_RECORD_ID = UUID.fromString(
            "2f4e2926-03dd-461f-873a-a70ad2256680"
    );
    private static final String CANCELLATION_KEY =
            "5d09cd43-50d1-4b82-a94f-a43f5bc6e56b";

    @AfterEach
    void clearSyntheticTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void refusesCancellationWithoutAnActualTransactionBeforeDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlJobService service = service(jdbcTemplate);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.cancelOwned(JOB_RECORD_ID, CANCELLATION_KEY, "tenant_alpha")
        );

        assertEquals(
                "Durable ETL job cancellation requires an active transaction",
                exception.getMessage()
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsInvalidCancellationIdentityBeforeDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlJobService service = service(jdbcTemplate);

        assertThrows(
                NullPointerException.class,
                () -> service.cancelOwned(null, CANCELLATION_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.JOB_CANCELLATION_KEY_REQUIRED,
                () -> service.cancelOwned(JOB_RECORD_ID, null, "tenant_alpha")
        );
        assertError(
                EtlRequestError.JOB_CANCELLATION_KEY_REQUIRED,
                () -> service.cancelOwned(JOB_RECORD_ID, "unsafe key", "tenant_alpha")
        );
        assertError(
                EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED,
                () -> service.cancelOwned(JOB_RECORD_ID, CANCELLATION_KEY, null)
        );
        assertError(
                EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED,
                () -> service.cancelOwned(JOB_RECORD_ID, CANCELLATION_KEY, "   ")
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void reportsAnActiveRowThatDidNotTransitionAsCancellationInProgress() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        EtlJobService service = service(new UnchangedPendingJobJdbcTemplate(JOB_RECORD_ID));

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.cancelOwned(JOB_RECORD_ID, CANCELLATION_KEY, "tenant_alpha")
        );

        assertEquals(EtlRequestError.JOB_CANCELLATION_IN_PROGRESS, exception.error());
    }

    @Test
    void failsClosedWhenUpdateCountClaimsTransitionButReturnedRowIsStillActive() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        EtlJobService service = service(new UnchangedPendingJobJdbcTemplate(JOB_RECORD_ID, 1));

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> service.cancelOwned(JOB_RECORD_ID, CANCELLATION_KEY, "tenant_alpha")
        );

        assertEquals(EtlRequestError.JOB_CANCELLATION_IN_PROGRESS, exception.error());
    }

    private static EtlJobService service(JdbcTemplate jdbcTemplate) {
        return new EtlJobService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties(),
                idempotencyKeyHash -> true
        );
    }

    private static void assertError(EtlRequestError expected, Runnable invocation) {
        EtlRequestException exception = assertThrows(EtlRequestException.class, invocation::run);
        assertEquals(expected, exception.error());
    }

    /**
     * Deterministic JDBC double for concurrency boundaries that return an unchanged PENDING row.
     */
    private static final class UnchangedPendingJobJdbcTemplate extends JdbcTemplate {

        private final UUID jobRecordId;
        private final int updatedRows;

        private UnchangedPendingJobJdbcTemplate(UUID jobRecordId) {
            this(jobRecordId, 0);
        }

        private UnchangedPendingJobJdbcTemplate(UUID jobRecordId, int updatedRows) {
            this.jobRecordId = jobRecordId;
            this.updatedRows = updatedRows;
        }

        @Override
        public int update(String sql, Object... args) {
            return updatedRows;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            ResultSet resultSet = mock(ResultSet.class);
            try {
                when(resultSet.getObject("job_record_id", UUID.class)).thenReturn(jobRecordId);
                when(resultSet.getString("job_status")).thenReturn("PENDING");
                when(resultSet.getInt("attempt_count")).thenReturn(0);
                when(resultSet.getString("failure_code")).thenReturn(null);
                when(resultSet.getString("cancellation_key_hash")).thenReturn(null);
                when(resultSet.getTimestamp("created_at")).thenReturn(
                        Timestamp.from(Instant.parse("2026-08-06T00:00:00Z"))
                );
                when(resultSet.getTimestamp("updated_at")).thenReturn(
                        Timestamp.from(Instant.parse("2026-08-06T00:00:01Z"))
                );
                return List.of(rowMapper.mapRow(resultSet, 0));
            } catch (SQLException exception) {
                throw new AssertionError("row mapper unexpectedly rejected the test row", exception);
            }
        }
    }
}
