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
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the first database-owned durable-job replay transition on the repaired stack.
 */
@SpringJUnitConfig(EtlJobReplayPersistenceIntegrationTest.TestConfiguration.class)
class EtlJobReplayPersistenceIntegrationTest {

    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String OTHER_PAYLOAD = "[{\"id\":\"record_beta\"}]";
    private static final String REPLAY_KEY = "1e05bdca-447c-4ad3-882c-e33963ce517c";
    private static final String OTHER_REPLAY_KEY = "519bc126-1398-4b4e-a4e3-1fb18a00f19b";
    private static final String PRINCIPAL = "tenant_alpha";

    private final EtlJobReplayService replayService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobReplayPersistenceIntegrationTest(
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
    void createsFirstGenerationPendingReplayWithoutMutatingTerminalSource() {
        UUID sourceJobRecordId = insertFailedSource("original-submission-key");
        Instant sourceUpdatedAt = instantColumn(sourceJobRecordId, "updated_at");

        EtlJobReplay replay = replayService.replayOwned(
                sourceJobRecordId,
                PAYLOAD,
                REPLAY_KEY,
                PRINCIPAL
        );

        assertFalse(replay.replayed());
        assertEquals(EtlJobStatus.PENDING, replay.jobStatus());
        assertNotEquals(sourceJobRecordId, replay.jobRecordId());
        assertEquals(
                sourceJobRecordId,
                uuidColumn(replay.jobRecordId(), "replay_source_job_record_id")
        );
        assertEquals(
                sourceJobRecordId,
                uuidColumn(replay.jobRecordId(), "replay_root_job_record_id")
        );
        assertEquals(1, integerColumn(replay.jobRecordId(), "replay_generation_count"));
        assertEquals(PAYLOAD, textColumn(replay.jobRecordId(), "request_payload"));
        assertEquals("FAILED", textColumn(sourceJobRecordId, "job_status"));
        assertNull(textColumn(sourceJobRecordId, "request_payload"));
        assertEquals(sourceUpdatedAt, instantColumn(sourceJobRecordId, "updated_at"));
    }

    @Test
    void replaysSameOwnerSourceKeyAndPayloadWithoutSecondInsert() {
        UUID sourceJobRecordId = insertFailedSource("idempotent-source-submission-key");

        EtlJobReplay first = replayService.replayOwned(
                sourceJobRecordId,
                PAYLOAD,
                REPLAY_KEY,
                PRINCIPAL
        );
        EtlJobReplay replay = replayService.replayOwned(
                sourceJobRecordId,
                PAYLOAD,
                "\"" + REPLAY_KEY + "\"",
                PRINCIPAL
        );

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.jobRecordId(), replay.jobRecordId());
        assertEquals(EtlJobStatus.PENDING, replay.jobStatus());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM etl_job_records "
                                + "WHERE replay_generation_count IS NOT NULL",
                        Integer.class
                )
        );
    }

    @Test
    void rejectsReplayKeyReuseAcrossDifferentSources() {
        UUID firstSourceJobRecordId = insertFailedSource("reused-key-first-source");
        replayService.replayOwned(
                firstSourceJobRecordId,
                PAYLOAD,
                REPLAY_KEY,
                PRINCIPAL
        );
        UUID secondSourceJobRecordId = insertFailedSource("reused-key-second-source");

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> replayService.replayOwned(
                        secondSourceJobRecordId,
                        PAYLOAD,
                        REPLAY_KEY,
                        PRINCIPAL
                )
        );

        assertEquals("etl_job_replay_key_reused", exception.getMessage());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM etl_job_records "
                                + "WHERE replay_generation_count IS NOT NULL",
                        Integer.class
                )
        );
    }

    @Test
    void rejectsReplayKeyReuseForDifferentPayloadOnSameSource() {
        UUID sourceJobRecordId = insertFailedSource("reused-key-payload-source");
        replayService.replayOwned(
                sourceJobRecordId,
                PAYLOAD,
                REPLAY_KEY,
                PRINCIPAL
        );

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> replayService.replayOwned(
                        sourceJobRecordId,
                        OTHER_PAYLOAD,
                        REPLAY_KEY,
                        PRINCIPAL
                )
        );

        assertEquals("etl_job_replay_key_reused", exception.getMessage());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM etl_job_records "
                                + "WHERE replay_generation_count IS NOT NULL",
                        Integer.class
                )
        );
    }

    @Test
    void acceptsStructuredReplayKeyForIndependentFailedSource() {
        UUID sourceJobRecordId = insertFailedSource("second-original-submission-key");

        EtlJobReplay replay = replayService.replayOwned(
                sourceJobRecordId,
                PAYLOAD,
                "\"" + OTHER_REPLAY_KEY + "\"",
                PRINCIPAL
        );

        assertFalse(replay.replayed());
        assertEquals(EtlJobStatus.PENDING, replay.jobStatus());
        assertEquals(
                sourceJobRecordId,
                uuidColumn(replay.jobRecordId(), "replay_source_job_record_id")
        );
    }

    @Test
    void createsFirstGenerationPendingReplayFromCancelledSourceWithoutMutatingCancellationEvidence() {
        UUID sourceJobRecordId = insertCancelledSource();
        Instant sourceUpdatedAt = instantColumn(sourceJobRecordId, "updated_at");

        EtlJobReplay replay = assertDoesNotThrow(() -> replayService.replayOwned(
                sourceJobRecordId,
                PAYLOAD,
                REPLAY_KEY,
                PRINCIPAL
        ));

        assertFalse(replay.replayed());
        assertEquals(EtlJobStatus.PENDING, replay.jobStatus());
        assertNotEquals(sourceJobRecordId, replay.jobRecordId());
        assertEquals(
                sourceJobRecordId,
                uuidColumn(replay.jobRecordId(), "replay_source_job_record_id")
        );
        assertEquals(
                sourceJobRecordId,
                uuidColumn(replay.jobRecordId(), "replay_root_job_record_id")
        );
        assertEquals(1, integerColumn(replay.jobRecordId(), "replay_generation_count"));
        assertEquals(PAYLOAD, textColumn(replay.jobRecordId(), "request_payload"));
        assertEquals("CANCELLED", textColumn(sourceJobRecordId, "job_status"));
        assertEquals(
                EtlJobService.CANCELLED_BY_OWNER_CODE,
                textColumn(sourceJobRecordId, "cancellation_code")
        );
        assertNull(textColumn(sourceJobRecordId, "request_payload"));
        assertEquals(sourceUpdatedAt, instantColumn(sourceJobRecordId, "updated_at"));
    }

    private UUID insertFailedSource(String submissionKey) {
        UUID sourceJobRecordId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO etl_job_records (
                    job_record_id, principal_scope_hash, submission_key_hash,
                    request_digest, request_payload, job_status, attempt_count,
                    failure_code
                ) VALUES (?, ?, ?, ?, NULL, 'FAILED', 1, ?)
                """,
                sourceJobRecordId,
                Sha256Digest.digest(PRINCIPAL),
                Sha256Digest.digest(submissionKey),
                Sha256Digest.digest(PAYLOAD),
                "etl_target_failure"
        );
        return sourceJobRecordId;
    }

    private UUID insertCancelledSource() {
        UUID sourceJobRecordId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO etl_job_records (
                    job_record_id, principal_scope_hash, submission_key_hash,
                    request_digest, request_payload, job_status, attempt_count,
                    cancellation_key_hash, cancellation_code, job_cancelled_at
                ) VALUES (?, ?, ?, ?, NULL, 'CANCELLED', 1, ?, ?, CURRENT_TIMESTAMP)
                """,
                sourceJobRecordId,
                Sha256Digest.digest(PRINCIPAL),
                Sha256Digest.digest("cancelled-original-submission-key"),
                Sha256Digest.digest(PAYLOAD),
                "d".repeat(64),
                EtlJobService.CANCELLED_BY_OWNER_CODE
        );
        return sourceJobRecordId;
    }

    private String textColumn(UUID jobRecordId, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM etl_job_records WHERE job_record_id=?",
                String.class,
                jobRecordId
        );
    }

    private UUID uuidColumn(UUID jobRecordId, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM etl_job_records WHERE job_record_id=?",
                UUID.class,
                jobRecordId
        );
    }

    private Integer integerColumn(UUID jobRecordId, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM etl_job_records WHERE job_record_id=?",
                Integer.class,
                jobRecordId
        );
    }

    private Instant instantColumn(UUID jobRecordId, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM etl_job_records WHERE job_record_id=?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(columnName).toInstant(),
                jobRecordId
        );
    }

    /** Minimal transaction-enabled Spring context for replay persistence verification. */
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
