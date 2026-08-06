package com.xtrmetl.etl.job;

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
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves that a committed owner cancellation makes every previously issued lease stale.
 */
@SpringJUnitConfig(EtlJobCancellationLeaseFenceIntegrationTest.TestConfiguration.class)
class EtlJobCancellationLeaseFenceIntegrationTest {

    private static final String PRINCIPAL_SCOPE_HASH = "a".repeat(64);
    private static final String SUBMISSION_KEY_HASH = "b".repeat(64);
    private static final String REQUEST_DIGEST = "c".repeat(64);
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";

    private final EtlJobLeaseRepository leaseRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobCancellationLeaseFenceIntegrationTest(
            EtlJobLeaseRepository leaseRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.leaseRepository = leaseRepository;
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
                    request_payload CLOB,
                    job_status VARCHAR(32) NOT NULL,
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    failure_code VARCHAR(128),
                    lease_claim_id UUID,
                    lease_owner_id VARCHAR(128),
                    lease_expires_at TIMESTAMP WITH TIME ZONE,
                    cancellation_key_hash CHAR(64),
                    cancellation_code VARCHAR(128),
                    job_cancelled_at TIMESTAMP WITH TIME ZONE,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
    }

    @Test
    @Transactional
    void cancellationWinsBeforeSuccessAndThePriorLeaseCannotOverwriteIt() {
        UUID jobRecordId = UUID.fromString("66b818a1-fd36-4ea9-aac0-a4ad8ca05fc1");
        Instant createdAt = Instant.parse("2026-08-06T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO etl_job_records (
                    job_record_id, principal_scope_hash, submission_key_hash,
                    request_digest, request_payload, job_status, attempt_count,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                """,
                jobRecordId,
                PRINCIPAL_SCOPE_HASH,
                SUBMISSION_KEY_HASH,
                REQUEST_DIGEST,
                PAYLOAD,
                createdAt,
                createdAt
        );
        EtlJobLease lease = leaseRepository.claimNext(
                "worker-alpha",
                Duration.ofMinutes(5),
                3
        ).orElseThrow();

        int cancelledRows = jdbcTemplate.update(
                """
                UPDATE etl_job_records
                SET job_status = 'CANCELLED',
                    request_payload = NULL,
                    failure_code = NULL,
                    lease_claim_id = NULL,
                    lease_owner_id = NULL,
                    lease_expires_at = NULL,
                    cancellation_key_hash = ?,
                    cancellation_code = ?,
                    job_cancelled_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE job_record_id = ?
                  AND principal_scope_hash = ?
                  AND job_status IN ('PENDING', 'RUNNING')
                """,
                "d".repeat(64),
                EtlJobService.CANCELLED_BY_OWNER_CODE,
                jobRecordId,
                PRINCIPAL_SCOPE_HASH
        );

        assertEquals(1, cancelledRows);
        assertThrows(StaleEtlJobLeaseException.class, () -> leaseRepository.markSucceeded(lease));
        assertEquals("CANCELLED", textColumn(jobRecordId, "job_status"));
        assertNull(textColumn(jobRecordId, "request_payload"));
        assertNull(textColumn(jobRecordId, "lease_owner_id"));
        assertEquals(
                EtlJobService.CANCELLED_BY_OWNER_CODE,
                textColumn(jobRecordId, "cancellation_code")
        );
    }

    private String textColumn(UUID jobRecordId, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM etl_job_records WHERE job_record_id = ?",
                String.class,
                jobRecordId
        );
    }

    /** Minimal transaction-enabled SQL context for cancellation lease-fencing tests. */
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
        EtlJobLeaseRepository etlJobLeaseRepository(
                JdbcTemplate jdbcTemplate,
                PlatformTransactionManager transactionManager
        ) {
            return new EtlJobLeaseRepository(jdbcTemplate, transactionManager);
        }
    }
}
