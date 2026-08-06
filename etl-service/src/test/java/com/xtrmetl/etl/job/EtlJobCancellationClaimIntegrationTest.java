package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
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
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that a pending cancellation removes the job from the database-owned worker queue.
 */
@SpringJUnitConfig(EtlJobCancellationClaimIntegrationTest.TestConfiguration.class)
class EtlJobCancellationClaimIntegrationTest {

    private static final String SUBMISSION_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String CANCELLATION_KEY = "70dc8b50-e8b2-4e1a-8c5f-d84814708a77";
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";

    private final EtlJobService jobService;
    private final EtlJobLeaseRepository leaseRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobCancellationClaimIntegrationTest(
            EtlJobService jobService,
            EtlJobLeaseRepository leaseRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.jobService = jobService;
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
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT etl_job_submission_scope_unique
                        UNIQUE (principal_scope_hash, submission_key_hash)
                )
                """);
    }

    @Test
    void cancelledPendingJobCannotBeClaimedByAnyWorker() {
        EtlJobSubmission submission = jobService.submit(
                PAYLOAD,
                SUBMISSION_KEY,
                "tenant_alpha"
        );

        EtlJobCancellation cancellation = jobService.cancelOwned(
                submission.jobRecordId(),
                CANCELLATION_KEY,
                "tenant_alpha"
        );

        assertEquals(EtlJobStatus.CANCELLED, cancellation.snapshot().jobStatus());
        assertTrue(leaseRepository.claimNext(
                "worker-alpha",
                Duration.ofMinutes(5),
                3
        ).isEmpty());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM etl_job_records WHERE job_record_id = ?",
                Integer.class,
                submission.jobRecordId()
        ));
    }

    /** Minimal transaction-enabled context for cancellation and worker claim integration. */
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
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        EtlBatchProperties etlBatchProperties() {
            return new EtlBatchProperties();
        }

        @Bean
        EtlRequestLock etlRequestLock() {
            return lockHash -> true;
        }

        @Bean
        EtlJobService etlJobService(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper,
                EtlBatchProperties batchProperties,
                EtlRequestLock requestLock
        ) {
            return new EtlJobService(
                    jdbcTemplate,
                    objectMapper,
                    batchProperties,
                    requestLock
            );
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
