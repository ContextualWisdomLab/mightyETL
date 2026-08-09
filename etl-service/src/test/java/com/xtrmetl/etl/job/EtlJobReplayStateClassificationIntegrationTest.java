package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves stable owner-safe source-state and payload classifications before replay insertion.
 */
@SpringJUnitConfig(EtlJobReplayStateClassificationIntegrationTest.TestConfiguration.class)
class EtlJobReplayStateClassificationIntegrationTest {

    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String OTHER_PAYLOAD = "[{\"id\":\"record_beta\"}]";
    private static final String REPLAY_KEY = "1e05bdca-447c-4ad3-882c-e33963ce517c";
    private static final String PRINCIPAL = "tenant_alpha";
    private static final String FOREIGN_PRINCIPAL = "tenant_beta";

    private final EtlJobReplayService replayService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobReplayStateClassificationIntegrationTest(
            EtlJobReplayService replayService,
            JdbcTemplate jdbcTemplate
    ) {
        this.replayService = replayService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void createDurableJobTable() {
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
    void missingAndForeignSourcesShareOwnerSafeNotFoundClassification() {
        assertReplayError(
                "etl_job_not_found",
                UUID.randomUUID(),
                PAYLOAD,
                PRINCIPAL
        );

        UUID foreignSource = insertRootSource("FAILED", FOREIGN_PRINCIPAL, PAYLOAD);
        assertReplayError("etl_job_not_found", foreignSource, PAYLOAD, PRINCIPAL);
    }

    @Test
    void pendingAndRunningSourcesUseStableActiveConflict() {
        UUID pendingSource = insertRootSource("PENDING", PRINCIPAL, PAYLOAD);
        assertReplayError("etl_job_replay_source_active", pendingSource, PAYLOAD, PRINCIPAL);

        UUID runningSource = insertRootSource("RUNNING", PRINCIPAL, PAYLOAD);
        assertReplayError("etl_job_replay_source_active", runningSource, PAYLOAD, PRINCIPAL);
    }

    @Test
    void succeededSourceUsesStableSucceededConflict() {
        UUID succeededSource = insertRootSource("SUCCEEDED", PRINCIPAL, PAYLOAD);

        assertReplayError(
                "etl_job_replay_source_succeeded",
                succeededSource,
                PAYLOAD,
                PRINCIPAL
        );
    }

    @Test
    void failedSourceWithDifferentResuppliedPayloadUsesStableMismatch() {
        UUID failedSource = insertRootSource("FAILED", PRINCIPAL, PAYLOAD);

        assertReplayError(
                "etl_job_replay_payload_mismatch",
                failedSource,
                OTHER_PAYLOAD,
                PRINCIPAL
        );
    }

    private UUID insertRootSource(String status, String principal, String digestPayload) {
        UUID sourceJobRecordId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO etl_job_records (
                    job_record_id, principal_scope_hash, submission_key_hash,
                    request_digest, request_payload, job_status, attempt_count
                ) VALUES (?, ?, ?, ?, NULL, ?, 1)
                """,
                sourceJobRecordId,
                Sha256Digest.digest(principal),
                Sha256Digest.digest("submission-" + sourceJobRecordId),
                Sha256Digest.digest(digestPayload),
                status
        );
        return sourceJobRecordId;
    }

    private void assertReplayError(
            String expectedCode,
            UUID sourceJobRecordId,
            String payload,
            String principal
    ) {
        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> replayService.replayOwned(
                        sourceJobRecordId,
                        payload,
                        REPLAY_KEY,
                        principal
                )
        );
        assertEquals(expectedCode, exception.getMessage());
    }

    /** Minimal transaction-enabled Spring context for source-classification verification. */
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
    }
}
