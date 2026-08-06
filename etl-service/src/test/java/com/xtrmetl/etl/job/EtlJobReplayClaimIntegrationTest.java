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
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves replay admission creates an ordinary pending job that the existing worker can claim.
 */
@SpringJUnitConfig(EtlJobReplayClaimIntegrationTest.TestConfiguration.class)
class EtlJobReplayClaimIntegrationTest {

    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String REPLAY_KEY = "1e05bdca-447c-4ad3-882c-e33963ce517c";

    private final EtlJobReplayService replayService;
    private final EtlJobLeaseRepository leaseRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobReplayClaimIntegrationTest(
            EtlJobReplayService replayService,
            EtlJobLeaseRepository leaseRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.replayService = replayService;
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
                    replay_source_job_record_id UUID,
                    replay_root_job_record_id UUID,
                    replay_generation_count INTEGER,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT etl_job_submission_scope_unique
                        UNIQUE (principal_scope_hash, submission_key_hash)
                )
                """);
    }

    @Test
    void replayCreatedPendingJobIsClaimedWithoutAReplaySpecificWorkerPath() {
        UUID sourceId = UUID.fromString("c0b2860a-fd63-431a-96cb-48f3f4d7b19d");
        jdbcTemplate.update(
                """
                INSERT INTO etl_job_records (
                    job_record_id, principal_scope_hash, submission_key_hash,
                    request_digest, request_payload, job_status, attempt_count,
                    failure_code
                ) VALUES (?, ?, ?, ?, NULL, 'FAILED', 1, 'etl_target_failure')
                """,
                sourceId,
                Sha256Digest.digest("tenant_alpha"),
                Sha256Digest.digest("source-key"),
                Sha256Digest.digest(PAYLOAD)
        );

        EtlJobReplay replay = replayService.replayOwned(
                sourceId,
                PAYLOAD,
                REPLAY_KEY,
                "tenant_alpha"
        );
        EtlJobLease lease = leaseRepository.claimNext(
                "worker-alpha",
                Duration.ofMinutes(5),
                3
        ).orElseThrow();

        assertEquals(replay.jobRecordId(), lease.jobRecordId());
        assertEquals(PAYLOAD, lease.requestPayload());
        assertEquals(1, lease.attemptCount());
        assertEquals(sourceId, jdbcTemplate.queryForObject(
                "SELECT replay_source_job_record_id FROM etl_job_records WHERE job_record_id=?",
                UUID.class,
                replay.jobRecordId()
        ));
    }

    /** Minimal transaction-enabled context for replay and worker claim integration. */
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
        EtlJobReplayService replayService(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper,
                EtlBatchProperties batchProperties,
                EtlRequestLock requestLock
        ) {
            return new EtlJobReplayService(
                    jdbcTemplate,
                    objectMapper,
                    batchProperties,
                    requestLock
            );
        }

        @Bean
        EtlJobLeaseRepository leaseRepository(
                JdbcTemplate jdbcTemplate,
                PlatformTransactionManager transactionManager
        ) {
            return new EtlJobLeaseRepository(jdbcTemplate, transactionManager);
        }
    }
}
