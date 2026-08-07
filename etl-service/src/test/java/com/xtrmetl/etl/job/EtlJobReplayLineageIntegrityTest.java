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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves replay admission rejects inherited roots that are themselves replay rows.
 *
 * <p>This is a defense-in-depth contract for repositories or migrations that import historical
 * rows before PostgreSQL's lineage trigger is available. A replay root must be the immutable first
 * job in the lineage, never another derived replay row.</p>
 */
@SpringJUnitConfig(EtlJobReplayLineageIntegrityTest.TestConfiguration.class)
class EtlJobReplayLineageIntegrityTest {

    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String REPLAY_KEY = "94ccf28c-9649-4a06-b06f-11e70c57c5d2";
    private static final String PRINCIPAL_SCOPE = "tenant_alpha";

    private final EtlJobReplayService replayService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobReplayLineageIntegrityTest(
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
                    replay_source_job_record_id UUID,
                    replay_root_job_record_id UUID,
                    replay_generation_count INTEGER,
                    CONSTRAINT etl_job_submission_scope_unique
                        UNIQUE (principal_scope_hash, submission_key_hash)
                )
                """);
    }

    @Test
    void rejectsAnInheritedRootThatIsItselfAReplay() {
        UUID rootJobRecordId = insertFailedJob(null, null, null);
        UUID generationOneJobRecordId = insertFailedJob(
                rootJobRecordId,
                rootJobRecordId,
                1
        );
        UUID malformedGenerationTwoJobRecordId = insertFailedJob(
                generationOneJobRecordId,
                generationOneJobRecordId,
                2
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> replayService.replayOwned(
                        malformedGenerationTwoJobRecordId,
                        PAYLOAD,
                        REPLAY_KEY,
                        PRINCIPAL_SCOPE
                )
        );

        assertEquals("Replay root is not a lineage root", exception.getMessage());
    }

    private UUID insertFailedJob(
            UUID replaySourceJobRecordId,
            UUID replayRootJobRecordId,
            Integer replayGenerationCount
    ) {
        UUID jobRecordId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO etl_job_records (
                    job_record_id,
                    principal_scope_hash,
                    submission_key_hash,
                    request_digest,
                    request_payload,
                    job_status,
                    attempt_count,
                    failure_code,
                    replay_source_job_record_id,
                    replay_root_job_record_id,
                    replay_generation_count
                ) VALUES (?, ?, ?, ?, NULL, 'FAILED', 0, ?, ?, ?, ?)
                """,
                jobRecordId,
                Sha256Digest.digest(PRINCIPAL_SCOPE),
                Sha256Digest.digest(UUID.randomUUID().toString()),
                Sha256Digest.digest(PAYLOAD),
                "etl_target_failure",
                replaySourceJobRecordId,
                replayRootJobRecordId,
                replayGenerationCount
        );
        return jobRecordId;
    }

    /** Minimal transaction-enabled context for replay-lineage integrity verification. */
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
