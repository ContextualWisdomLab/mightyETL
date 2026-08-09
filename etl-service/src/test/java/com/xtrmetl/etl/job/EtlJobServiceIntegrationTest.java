package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.EtlRequestLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defines the durable, principal-scoped asynchronous ETL job intake and cancellation contract.
 */
@SpringJUnitConfig(EtlJobServiceIntegrationTest.TestConfiguration.class)
class EtlJobServiceIntegrationTest {

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String CANCELLATION_KEY = "70dc8b50-e8b2-4e1a-8c5f-d84814708a77";
    private static final String SECOND_CANCELLATION_KEY =
            "a52b165f-9d45-4399-ae84-1e93e8fe1e68";
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\",\"name\":\"accepted\"}]";

    private final EtlJobService etlJobService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobServiceIntegrationTest(EtlJobService etlJobService, JdbcTemplate jdbcTemplate) {
        this.etlJobService = etlJobService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void createJobTable() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS etl_job_records");
        jdbcTemplate.execute("""
                CREATE TABLE etl_job_records (
                    job_record_id UUID PRIMARY KEY,
                    principal_scope_hash CHAR(64) NOT NULL,
                    submission_key_hash CHAR(64) NOT NULL,
                    request_digest CHAR(64) NOT NULL,
                    request_payload VARCHAR(8192),
                    job_status VARCHAR(32) NOT NULL,
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    failure_code VARCHAR(128),
                    lease_claim_id UUID,
                    lease_owner_id VARCHAR(128),
                    lease_expires_at TIMESTAMP WITH TIME ZONE,
                    cancellation_key_hash CHAR(64),
                    cancellation_code VARCHAR(128),
                    job_cancelled_at TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT etl_job_submission_scope_unique
                        UNIQUE (principal_scope_hash, submission_key_hash)
                )
                """);
    }

    @Test
    void replaysTheSamePrincipalKeyAndPayloadAsOneDurableJob() {
        EtlJobSubmission first = etlJobService.submit(PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha");
        EtlJobSubmission replay = etlJobService.submit(PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha");

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.jobRecordId(), replay.jobRecordId());
        assertEquals(EtlJobStatus.PENDING, first.jobStatus());
        assertEquals(1, countJobRows());
    }

    @Test
    void normalizesQuotedAndLegacyRawIdempotencyKeysToTheSameJob() {
        EtlJobSubmission first = etlJobService.submit(PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha");
        EtlJobSubmission replay = etlJobService.submit(
                PAYLOAD,
                "\"" + IDEMPOTENCY_KEY + "\"",
                "tenant_alpha"
        );

        assertEquals(first.jobRecordId(), replay.jobRecordId());
        assertTrue(replay.replayed());
        assertEquals(1, countJobRows());
    }

    @Test
    void rejectsTheSamePrincipalKeyWhenTheJsonTextChanges() {
        etlJobService.submit(PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha");

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.submit(
                        "[{\"id\":\"record_beta\"}]",
                        IDEMPOTENCY_KEY,
                        "tenant_alpha"
                )
        );

        assertEquals(EtlRequestError.JOB_SUBMISSION_KEY_REUSED, exception.error());
        assertEquals(1, countJobRows());
    }

    @Test
    void isolatesTheSameSubmissionKeyAcrossPrincipals() {
        EtlJobSubmission alpha = etlJobService.submit(PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha");
        EtlJobSubmission beta = etlJobService.submit(PAYLOAD, IDEMPOTENCY_KEY, "tenant_beta");

        assertNotEquals(alpha.jobRecordId(), beta.jobRecordId());
        assertEquals(2, countJobRows());
    }

    @Test
    void returnsOnlyJobsOwnedByTheAuthenticatedPrincipal() {
        EtlJobSubmission created = etlJobService.submit(PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha");

        EtlJobSnapshot snapshot = etlJobService.findOwned(
                created.jobRecordId(),
                "tenant_alpha"
        );
        EtlRequestException hidden = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.findOwned(created.jobRecordId(), "tenant_beta")
        );
        EtlRequestException missing = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.findOwned(UUID.randomUUID(), "tenant_alpha")
        );

        assertEquals(created.jobRecordId(), snapshot.jobRecordId());
        assertEquals(EtlJobStatus.PENDING, snapshot.jobStatus());
        assertEquals(0, snapshot.attemptCount());
        assertEquals(EtlRequestError.JOB_NOT_FOUND, hidden.error());
        assertEquals(EtlRequestError.JOB_NOT_FOUND, missing.error());
    }

    @Test
    void rejectsMalformedOrOversizedPayloadsBeforePersistence() {
        EtlRequestException malformed = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.submit("not-json", IDEMPOTENCY_KEY, "tenant_alpha")
        );
        EtlRequestException oversized = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.submit(
                        "[\"" + "x".repeat(1_100_000) + "\"]",
                        IDEMPOTENCY_KEY,
                        "tenant_alpha"
                )
        );

        assertEquals(EtlRequestError.INVALID_JSON, malformed.error());
        assertEquals(EtlRequestError.PAYLOAD_TOO_LARGE, oversized.error());
        assertEquals(0, countJobRows());
    }

    @Test
    void cancelsPendingWorkClearsThePayloadAndReplaysTheSameSemanticKey() {
        EtlJobSubmission created = etlJobService.submit(PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha");

        EtlJobCancellation cancelled = etlJobService.cancelOwned(
                created.jobRecordId(),
                CANCELLATION_KEY,
                "tenant_alpha"
        );
        EtlJobCancellation replayed = etlJobService.cancelOwned(
                created.jobRecordId(),
                "\"" + CANCELLATION_KEY + "\"",
                "tenant_alpha"
        );

        assertFalse(cancelled.replayed());
        assertTrue(replayed.replayed());
        assertEquals(created.jobRecordId(), cancelled.snapshot().jobRecordId());
        assertEquals(EtlJobStatus.CANCELLED, cancelled.snapshot().jobStatus());
        assertEquals(cancelled.snapshot(), replayed.snapshot());
        assertNull(column(created.jobRecordId(), "request_payload", String.class));
        assertEquals(
                EtlJobService.CANCELLED_BY_OWNER_CODE,
                column(created.jobRecordId(), "cancellation_code", String.class)
        );
        String keyHash = column(created.jobRecordId(), "cancellation_key_hash", String.class);
        assertNotNull(keyHash);
        assertEquals(64, keyHash.length());
        assertNotEquals(CANCELLATION_KEY, keyHash);
        assertNotNull(column(
                created.jobRecordId(),
                "job_cancelled_at",
                OffsetDateTime.class
        ));
    }

    @Test
    void rejectsASecondCancellationIdentityAfterCancellation() {
        EtlJobSubmission created = etlJobService.submit(PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha");
        etlJobService.cancelOwned(created.jobRecordId(), CANCELLATION_KEY, "tenant_alpha");

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.cancelOwned(
                        created.jobRecordId(),
                        SECOND_CANCELLATION_KEY,
                        "tenant_alpha"
                )
        );

        assertEquals(EtlRequestError.JOB_CANCELLATION_KEY_REUSED, exception.error());
        assertEquals(EtlJobStatus.CANCELLED, etlJobService.findOwned(
                created.jobRecordId(),
                "tenant_alpha"
        ).jobStatus());
    }

    @Test
    void keepsForeignOwnedAndMissingCancellationTargetsIndistinguishable() {
        EtlJobSubmission created = etlJobService.submit(PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha");

        EtlRequestException hidden = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.cancelOwned(
                        created.jobRecordId(),
                        CANCELLATION_KEY,
                        "tenant_beta"
                )
        );
        EtlRequestException missing = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.cancelOwned(
                        UUID.randomUUID(),
                        CANCELLATION_KEY,
                        "tenant_alpha"
                )
        );

        assertEquals(EtlRequestError.JOB_NOT_FOUND, hidden.error());
        assertEquals(EtlRequestError.JOB_NOT_FOUND, missing.error());
        assertEquals(EtlJobStatus.PENDING, etlJobService.findOwned(
                created.jobRecordId(),
                "tenant_alpha"
        ).jobStatus());
    }

    @Test
    void rejectsCancellationAfterACommittedSuccessOrFailure() {
        EtlJobSubmission succeeded = etlJobService.submit(
                PAYLOAD,
                IDEMPOTENCY_KEY,
                "tenant_alpha"
        );
        EtlJobSubmission failed = etlJobService.submit(
                PAYLOAD,
                "1d38ad67-48d8-446c-bca1-76bfe2ba8eef",
                "tenant_alpha"
        );
        jdbcTemplate.update(
                "UPDATE etl_job_records SET job_status = 'SUCCEEDED', request_payload = NULL "
                        + "WHERE job_record_id = ?",
                succeeded.jobRecordId()
        );
        jdbcTemplate.update(
                "UPDATE etl_job_records SET job_status = 'FAILED', request_payload = NULL, "
                        + "failure_code = 'etl_target_failure' WHERE job_record_id = ?",
                failed.jobRecordId()
        );

        EtlRequestException successConflict = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.cancelOwned(
                        succeeded.jobRecordId(),
                        CANCELLATION_KEY,
                        "tenant_alpha"
                )
        );
        EtlRequestException failureConflict = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.cancelOwned(
                        failed.jobRecordId(),
                        CANCELLATION_KEY,
                        "tenant_alpha"
                )
        );

        assertEquals("etl_job_already_succeeded", successConflict.error().errorCode());
        assertEquals("etl_job_already_failed", failureConflict.error().errorCode());
    }

    @Test
    void cancelsRunningWorkAndInvalidatesEveryLeaseField() {
        EtlJobSubmission created = etlJobService.submit(PAYLOAD, IDEMPOTENCY_KEY, "tenant_alpha");
        jdbcTemplate.update(
                """
                UPDATE etl_job_records
                SET job_status = 'RUNNING',
                    attempt_count = 1,
                    lease_claim_id = ?,
                    lease_owner_id = 'worker_alpha',
                    lease_expires_at = DATEADD('MINUTE', 5, CURRENT_TIMESTAMP)
                WHERE job_record_id = ?
                """,
                UUID.fromString("7c10a65b-5791-4e0f-9fba-dadbb13971da"),
                created.jobRecordId()
        );

        EtlJobCancellation cancellation = etlJobService.cancelOwned(
                created.jobRecordId(),
                CANCELLATION_KEY,
                "tenant_alpha"
        );

        assertEquals(EtlJobStatus.CANCELLED, cancellation.snapshot().jobStatus());
        assertEquals(1, cancellation.snapshot().attemptCount());
        assertNull(column(created.jobRecordId(), "lease_claim_id", UUID.class));
        assertNull(column(created.jobRecordId(), "lease_owner_id", String.class));
        assertNull(column(created.jobRecordId(), "lease_expires_at", OffsetDateTime.class));
        assertNull(column(created.jobRecordId(), "request_payload", String.class));
    }

    @Test
    void rejectsInvalidCancellationKeysBeforeAnyTableAccess() {
        jdbcTemplate.execute("DROP TABLE etl_job_records");

        EtlRequestException absent = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.cancelOwned(UUID.randomUUID(), null, "tenant_alpha")
        );
        EtlRequestException malformed = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.cancelOwned(UUID.randomUUID(), "too-short", "tenant_alpha")
        );

        assertEquals(EtlRequestError.JOB_CANCELLATION_KEY_REQUIRED, absent.error());
        assertEquals(EtlRequestError.JOB_CANCELLATION_KEY_REQUIRED, malformed.error());
    }

    private int countJobRows() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM etl_job_records",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private <T> T column(UUID jobRecordId, String columnName, Class<T> valueType) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM etl_job_records WHERE job_record_id = ?",
                (resultSet, rowNumber) -> resultSet.getObject(columnName, valueType),
                jobRecordId
        );
    }

    /**
     * Minimal transaction-enabled test context for durable job resource services.
     */
    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2)
                    .build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        EtlBatchProperties etlBatchProperties() {
            return new EtlBatchProperties();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        EtlRequestLock etlRequestLock() {
            return idempotencyKeyHash -> true;
        }

        @Bean
        EtlJobService etlJobService(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper,
                EtlBatchProperties properties,
                EtlRequestLock requestLock
        ) {
            return new EtlJobService(jdbcTemplate, objectMapper, properties, requestLock);
        }
    }
}
