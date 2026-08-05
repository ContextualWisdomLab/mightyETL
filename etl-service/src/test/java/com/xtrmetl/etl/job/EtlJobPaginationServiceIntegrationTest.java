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
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defines stable keyset traversal, bounded validation, and tenant isolation for job discovery.
 */
@SpringJUnitConfig(EtlJobPaginationServiceIntegrationTest.TestConfiguration.class)
class EtlJobPaginationServiceIntegrationTest {

    private static final UUID OLDEST_JOB = UUID.fromString(
            "00000000-0000-0000-0000-000000000000"
    );
    private static final UUID TIED_LOWER_JOB = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID TIED_HIGHER_JOB = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );
    private static final UUID NEWEST_JOB = UUID.fromString(
            "00000000-0000-0000-0000-000000000003"
    );
    private static final UUID FOREIGN_JOB = UUID.fromString(
            "00000000-0000-0000-0000-000000000004"
    );

    private final EtlJobService etlJobService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobPaginationServiceIntegrationTest(
            EtlJobService etlJobService,
            JdbcTemplate jdbcTemplate
    ) {
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
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    CONSTRAINT etl_job_submission_scope_unique
                        UNIQUE (principal_scope_hash, submission_key_hash)
                )
                """);
    }

    @Test
    void traversesAnUnchangedTenantDatasetWithoutDuplicatesOrOmissions() {
        insertJob(OLDEST_JOB, "tenant_alpha", Instant.parse("2026-08-05T01:00:00Z"));
        insertJob(TIED_LOWER_JOB, "tenant_alpha", Instant.parse("2026-08-05T02:00:00Z"));
        insertJob(TIED_HIGHER_JOB, "tenant_alpha", Instant.parse("2026-08-05T02:00:00Z"));
        insertJob(NEWEST_JOB, "tenant_alpha", Instant.parse("2026-08-05T03:00:00Z"));
        insertJob(FOREIGN_JOB, "tenant_beta", Instant.parse("2026-08-05T04:00:00Z"));

        EtlJobPage firstPage = etlJobService.listOwned("tenant_alpha", null, "2");
        assertEquals(List.of(NEWEST_JOB, TIED_HIGHER_JOB), ids(firstPage));
        assertNotNull(firstPage.nextCursor());

        EtlJobPage secondPage = etlJobService.listOwned(
                "tenant_alpha",
                firstPage.nextCursor(),
                "2"
        );
        assertEquals(List.of(TIED_LOWER_JOB, OLDEST_JOB), ids(secondPage));
        assertNull(secondPage.nextCursor());

        List<UUID> traversed = new ArrayList<>(ids(firstPage));
        traversed.addAll(ids(secondPage));
        assertEquals(
                List.of(NEWEST_JOB, TIED_HIGHER_JOB, TIED_LOWER_JOB, OLDEST_JOB),
                traversed
        );
        assertFalse(traversed.contains(FOREIGN_JOB));
    }

    @Test
    void usesTheBoundedDefaultAndReturnsAnEmptyTerminalPage() {
        EtlJobPage page = etlJobService.listOwned("tenant_alpha", null, null);

        assertTrue(page.jobs().isEmpty());
        assertNull(page.nextCursor());
    }

    @Test
    void rejectsMalformedLimitsAndCursorsBeforeDatabaseAccess() {
        jdbcTemplate.execute("DROP TABLE etl_job_records");

        for (String invalidLimit : List.of("0", "-1", "101", "abc", "01", " 2")) {
            EtlRequestException exception = assertThrows(
                    EtlRequestException.class,
                    () -> etlJobService.listOwned("tenant_alpha", null, invalidLimit)
            );
            assertEquals(EtlRequestError.INVALID_JOB_PAGE_LIMIT, exception.error());
        }

        List<String> invalidCursors = List.of(
                "",
                "!",
                encode("missing_separator"),
                encode("not-an-instant|00000000-0000-0000-0000-000000000000"),
                encode("2026-08-05T01:00:00Z|not-a-uuid"),
                encode("2026-08-05T01:00:00Z|00000000-0000-0000-0000-000000000000") + "=",
                encode("2026-08-05T01:00:00.000Z|00000000-0000-0000-0000-000000000000"),
                "a".repeat(193)
        );
        for (String invalidCursor : invalidCursors) {
            EtlRequestException exception = assertThrows(
                    EtlRequestException.class,
                    () -> etlJobService.listOwned("tenant_alpha", invalidCursor, "2")
            );
            assertEquals(EtlRequestError.INVALID_JOB_PAGE_CURSOR, exception.error());
        }

        EtlRequestException missingPrincipal = assertThrows(
                EtlRequestException.class,
                () -> etlJobService.listOwned(null, null, "2")
        );
        assertEquals(EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED, missingPrincipal.error());
    }

    private void insertJob(UUID jobRecordId, String principalScope, Instant createdAt) {
        String identity = jobRecordId.toString();
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
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                """,
                jobRecordId,
                Sha256Digest.digest(principalScope),
                Sha256Digest.digest("submission:" + identity),
                Sha256Digest.digest("payload:" + identity),
                "[{\"id\":\"" + identity + "\"}]",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private static List<UUID> ids(EtlJobPage page) {
        return page.jobs().stream().map(EtlJobSnapshot::jobRecordId).toList();
    }

    private static String encode(String cursorPayload) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(cursorPayload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Minimal transaction-enabled test context for job pagination.
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
