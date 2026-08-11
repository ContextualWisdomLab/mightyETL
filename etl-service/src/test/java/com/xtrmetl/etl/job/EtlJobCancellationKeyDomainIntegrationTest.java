package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestLock;
import com.xtrmetl.etl.service.Sha256Digest;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves durable cancellation idempotency hashes are purpose-, owner-, and job-bound without
 * persisting the raw cancellation key.
 */
@SpringJUnitConfig(EtlJobCancellationKeyDomainIntegrationTest.TestConfiguration.class)
class EtlJobCancellationKeyDomainIntegrationTest {

    private static final String CANCELLATION_DOMAIN =
            "mightyetl:durable-job-cancellation:v1:";
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String CANCELLATION_KEY =
            "70dc8b50-e8b2-4e1a-8c5f-d84814708a77";
    private static final String SUBMISSION_KEY_ALPHA =
            "550e8400-e29b-41d4-a716-446655440000";
    private static final String SUBMISSION_KEY_BETA =
            "1d38ad67-48d8-446c-bca1-76bfe2ba8eef";
    private static final String SUBMISSION_KEY_OTHER_OWNER =
            "11cf0982-4fe9-4a35-b981-58390596163f";

    private final EtlJobService jobService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobCancellationKeyDomainIntegrationTest(
            EtlJobService jobService,
            JdbcTemplate jdbcTemplate
    ) {
        this.jobService = jobService;
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
    void bindsTheSameRawCancellationKeyToItsPurposeOwnerAndJob() {
        UUID alphaJob = submit(SUBMISSION_KEY_ALPHA, "tenant_alpha");
        UUID betaJob = submit(SUBMISSION_KEY_BETA, "tenant_alpha");
        UUID otherOwnerJob = submit(SUBMISSION_KEY_OTHER_OWNER, "tenant_beta");

        jobService.cancelOwned(alphaJob, CANCELLATION_KEY, "tenant_alpha");
        jobService.cancelOwned(betaJob, CANCELLATION_KEY, "tenant_alpha");
        jobService.cancelOwned(otherOwnerJob, CANCELLATION_KEY, "tenant_beta");

        String alphaHash = cancellationHash(alphaJob);
        String betaHash = cancellationHash(betaJob);
        String otherOwnerHash = cancellationHash(otherOwnerJob);

        assertEquals(expectedHash("tenant_alpha", alphaJob), alphaHash);
        assertEquals(expectedHash("tenant_alpha", betaJob), betaHash);
        assertEquals(expectedHash("tenant_beta", otherOwnerJob), otherOwnerHash);
        assertNotEquals(alphaHash, betaHash);
        assertNotEquals(alphaHash, otherOwnerHash);
        assertNotEquals(betaHash, otherOwnerHash);
        assertNotEquals(CANCELLATION_KEY, alphaHash);
        assertEquals(64, alphaHash.length());
    }

    @Test
    void normalizesQuotedAndLegacyRawKeysToOneReplayIdentity() {
        UUID jobRecordId = submit(SUBMISSION_KEY_ALPHA, "tenant_alpha");

        EtlJobCancellation first = jobService.cancelOwned(
                jobRecordId,
                CANCELLATION_KEY,
                "tenant_alpha"
        );
        String firstHash = cancellationHash(jobRecordId);
        EtlJobCancellation replay = jobService.cancelOwned(
                jobRecordId,
                "\"" + CANCELLATION_KEY + "\"",
                "tenant_alpha"
        );

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.snapshot(), replay.snapshot());
        assertEquals(firstHash, cancellationHash(jobRecordId));
    }

    private UUID submit(String submissionKey, String principalScope) {
        return jobService.submit(PAYLOAD, submissionKey, principalScope).jobRecordId();
    }

    private String cancellationHash(UUID jobRecordId) {
        return jdbcTemplate.queryForObject(
                "SELECT cancellation_key_hash FROM etl_job_records WHERE job_record_id = ?",
                String.class,
                jobRecordId
        );
    }

    private static String expectedHash(String principalScope, UUID jobRecordId) {
        String principalScopeHash = Sha256Digest.digest(principalScope);
        return Sha256Digest.digest(
                CANCELLATION_DOMAIN
                        + principalScopeHash
                        + ":"
                        + jobRecordId
                        + ":"
                        + CANCELLATION_KEY
        );
    }

    /** Minimal transaction-enabled context for cancellation-domain integration tests. */
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
    }
}
