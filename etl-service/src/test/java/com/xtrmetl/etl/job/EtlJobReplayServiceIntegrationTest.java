package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers immutable replay admission, owner isolation, payload proof, and lineage generation.
 */
@SpringJUnitConfig(EtlJobReplayServiceIntegrationTest.TestConfiguration.class)
class EtlJobReplayServiceIntegrationTest {

    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String OTHER_PAYLOAD = "[{\"id\":\"record_beta\"}]";
    private static final String REPLAY_KEY = "1e05bdca-447c-4ad3-882c-e33963ce517c";
    private static final String OTHER_REPLAY_KEY = "519bc126-1398-4b4e-a4e3-1fb18a00f19b";

    private final EtlJobReplayService replayService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobReplayServiceIntegrationTest(
            EtlJobReplayService replayService,
            JdbcTemplate jdbcTemplate
    ) {
        this.replayService = replayService;
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
    void createsOnePendingReplayFromAFailedSourceAndReplaysIt() {
        UUID sourceId = insertTerminalSource(EtlJobStatus.FAILED, "tenant_alpha", PAYLOAD);
        Instant sourceUpdated = instantColumn(sourceId, "updated_at");

        EtlJobReplay first = replayService.replayOwned(
                sourceId,
                PAYLOAD,
                REPLAY_KEY,
                "tenant_alpha"
        );
        EtlJobReplay replay = replayService.replayOwned(
                sourceId,
                PAYLOAD,
                "\"" + REPLAY_KEY + "\"",
                "tenant_alpha"
        );

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.jobRecordId(), replay.jobRecordId());
        assertEquals(EtlJobStatus.PENDING, first.jobStatus());
        assertEquals(sourceId, uuidColumn(first.jobRecordId(), "replay_source_job_record_id"));
        assertEquals(sourceId, uuidColumn(first.jobRecordId(), "replay_root_job_record_id"));
        assertEquals(1, integerColumn(first.jobRecordId(), "replay_generation_count"));
        assertEquals(PAYLOAD, textColumn(first.jobRecordId(), "request_payload"));
        assertEquals("FAILED", textColumn(sourceId, "job_status"));
        assertNull(textColumn(sourceId, "request_payload"));
        assertEquals(sourceUpdated, instantColumn(sourceId, "updated_at"));
    }

    @Test
    void createsAReplayFromCancelledSourceAndKeepsCancellationEvidence() {
        UUID sourceId = insertTerminalSource(EtlJobStatus.CANCELLED, "tenant_alpha", PAYLOAD);
        jdbcTemplate.update(
                "UPDATE etl_job_records SET cancellation_key_hash=?, cancellation_code=?, "
                        + "job_cancelled_at=CURRENT_TIMESTAMP WHERE job_record_id=?",
                "d".repeat(64),
                EtlJobService.CANCELLED_BY_OWNER_CODE,
                sourceId
        );

        EtlJobReplay replay = replayService.replayOwned(
                sourceId,
                PAYLOAD,
                REPLAY_KEY,
                "tenant_alpha"
        );

        assertEquals(EtlJobStatus.PENDING, replay.jobStatus());
        assertEquals("CANCELLED", textColumn(sourceId, "job_status"));
        assertEquals(
                EtlJobService.CANCELLED_BY_OWNER_CODE,
                textColumn(sourceId, "cancellation_code")
        );
        assertNull(textColumn(sourceId, "request_payload"));
    }

    @Test
    void rejectsMismatchedPayloadAndReplayKeyReuse() {
        UUID sourceId = insertTerminalSource(EtlJobStatus.FAILED, "tenant_alpha", PAYLOAD);
        EtlRequestException mismatch = assertThrows(
                EtlRequestException.class,
                () -> replayService.replayOwned(
                        sourceId,
                        OTHER_PAYLOAD,
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );
        assertEquals(EtlRequestError.JOB_REPLAY_PAYLOAD_MISMATCH, mismatch.error());

        EtlJobReplay first = replayService.replayOwned(
                sourceId,
                PAYLOAD,
                REPLAY_KEY,
                "tenant_alpha"
        );
        UUID otherSource = insertTerminalSource(
                EtlJobStatus.FAILED,
                "tenant_alpha",
                PAYLOAD
        );
        EtlRequestException reused = assertThrows(
                EtlRequestException.class,
                () -> replayService.replayOwned(
                        otherSource,
                        PAYLOAD,
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );

        assertEquals(EtlRequestError.JOB_REPLAY_KEY_REUSED, reused.error());
        assertEquals(1, replayRowCount());
        assertNotEquals(sourceId, first.jobRecordId());
    }

    @Test
    void hidesForeignAndMissingSourcesAndRejectsUnsupportedStates() {
        UUID failed = insertTerminalSource(EtlJobStatus.FAILED, "tenant_alpha", PAYLOAD);
        assertError(
                EtlRequestError.JOB_NOT_FOUND,
                () -> replayService.replayOwned(failed, PAYLOAD, REPLAY_KEY, "tenant_beta")
        );
        assertError(
                EtlRequestError.JOB_NOT_FOUND,
                () -> replayService.replayOwned(
                        UUID.randomUUID(),
                        PAYLOAD,
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );
        assertError(
                EtlRequestError.JOB_REPLAY_SOURCE_ACTIVE,
                () -> replayService.replayOwned(
                        insertSource(EtlJobStatus.PENDING, "tenant_alpha", PAYLOAD, null, null),
                        PAYLOAD,
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );
        assertError(
                EtlRequestError.JOB_REPLAY_SOURCE_ACTIVE,
                () -> replayService.replayOwned(
                        insertSource(EtlJobStatus.RUNNING, "tenant_alpha", PAYLOAD, null, null),
                        PAYLOAD,
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );
        assertError(
                EtlRequestError.JOB_REPLAY_SOURCE_SUCCEEDED,
                () -> replayService.replayOwned(
                        insertTerminalSource(EtlJobStatus.SUCCEEDED, "tenant_alpha", PAYLOAD),
                        PAYLOAD,
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );
    }

    @Test
    void replayOfReplayPreservesRootAndBoundsGeneration() {
        UUID root = insertTerminalSource(EtlJobStatus.FAILED, "tenant_alpha", PAYLOAD);
        UUID generationOne = insertSource(
                EtlJobStatus.FAILED,
                "tenant_alpha",
                PAYLOAD,
                root,
                1
        );
        EtlJobReplay generationTwo = replayService.replayOwned(
                generationOne,
                PAYLOAD,
                REPLAY_KEY,
                "tenant_alpha"
        );

        assertEquals(generationOne, uuidColumn(
                generationTwo.jobRecordId(),
                "replay_source_job_record_id"
        ));
        assertEquals(root, uuidColumn(
                generationTwo.jobRecordId(),
                "replay_root_job_record_id"
        ));
        assertEquals(2, integerColumn(
                generationTwo.jobRecordId(),
                "replay_generation_count"
        ));

        UUID generationHundred = insertSource(
                EtlJobStatus.CANCELLED,
                "tenant_alpha",
                PAYLOAD,
                root,
                EtlJobReplayService.MAXIMUM_REPLAY_GENERATION
        );
        assertError(
                EtlRequestError.JOB_REPLAY_GENERATION_EXHAUSTED,
                () -> replayService.replayOwned(
                        generationHundred,
                        PAYLOAD,
                        OTHER_REPLAY_KEY,
                        "tenant_alpha"
                )
        );
    }

    private UUID insertTerminalSource(
            EtlJobStatus status,
            String principal,
            String payload
    ) {
        return insertSource(status, principal, payload, null, null);
    }

    private UUID insertSource(
            EtlJobStatus status,
            String principal,
            String payload,
            UUID replayRoot,
            Integer replayGeneration
    ) {
        UUID id = UUID.randomUUID();
        UUID replaySource = replayRoot == null ? null : replayRoot;
        jdbcTemplate.update(
                """
                INSERT INTO etl_job_records (
                    job_record_id, principal_scope_hash, submission_key_hash,
                    request_digest, request_payload, job_status, attempt_count,
                    failure_code, replay_source_job_record_id,
                    replay_root_job_record_id, replay_generation_count
                ) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)
                """,
                id,
                Sha256Digest.digest(principal),
                Sha256Digest.digest(UUID.randomUUID().toString()),
                Sha256Digest.digest(payload),
                status == EtlJobStatus.PENDING || status == EtlJobStatus.RUNNING
                        ? payload : null,
                status.name(),
                status == EtlJobStatus.FAILED ? "etl_target_failure" : null,
                replaySource,
                replayRoot,
                replayGeneration
        );
        return id;
    }

    private static void assertError(EtlRequestError expected, Runnable invocation) {
        EtlRequestException exception = assertThrows(EtlRequestException.class, invocation::run);
        assertEquals(expected, exception.error());
    }

    private int replayRowCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM etl_job_records WHERE replay_generation_count IS NOT NULL",
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private String textColumn(UUID id, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM etl_job_records WHERE job_record_id=?",
                String.class,
                id
        );
    }

    private UUID uuidColumn(UUID id, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM etl_job_records WHERE job_record_id=?",
                UUID.class,
                id
        );
    }

    private Integer integerColumn(UUID id, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM etl_job_records WHERE job_record_id=?",
                Integer.class,
                id
        );
    }

    private Instant instantColumn(UUID id, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM etl_job_records WHERE job_record_id=?",
                (resultSet, rowNumber) -> resultSet.getTimestamp(column).toInstant(),
                id
        );
    }

    /** Minimal transaction-enabled context for replay service integration. */
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
