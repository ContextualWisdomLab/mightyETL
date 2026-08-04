package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestLock;
import com.xtrmetl.etl.service.EtlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves target effects, the durable ledger, and terminal success share one transaction.
 */
@SpringJUnitConfig(EtlJobExecutionServiceIntegrationTest.TestConfiguration.class)
class EtlJobExecutionServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final UUID JOB_RECORD_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final UUID LEASE_TOKEN = UUID.fromString(
            "72ab52a5-b060-4a20-af99-e701722bb221"
    );
    private static final String TWO_RECORD_PAYLOAD = """
            [{"id":"record_alpha","name":"Ada"},{"id":"record_beta","name":"Grace"}]
            """.strip();

    private final EtlJobExecutionService executionService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobExecutionServiceIntegrationTest(
            EtlJobExecutionService executionService,
            JdbcTemplate jdbcTemplate
    ) {
        this.executionService = executionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void resetSchema() {
        EtlJobTestDatabase.createSchema(jdbcTemplate);
    }

    @Test
    void commitsTargetLedgerAndSuccessfulTerminalStateAtomically() {
        EtlJobClaim claim = runningClaim(TWO_RECORD_PAYLOAD, LEASE_TOKEN);

        int processedRecordCount = executionService.execute(claim);

        assertEquals(2, processedRecordCount);
        assertEquals(2, count("processed_data"));
        assertEquals(1, count("etl_idempotency_records"));
        assertEquals(
                "SUCCEEDED",
                scalar("job_status", String.class)
        );
        assertNull(scalar("request_payload", String.class));
        assertNull(scalar("lease_token", UUID.class));
        assertNull(scalar("lease_expires_at", Timestamp.class));
        assertNull(scalar("failure_code", String.class));
        assertEquals(2, scalar("processed_record_count", Integer.class));
    }

    @Test
    void reportsZeroRecordsForAnAcceptedEmptyBatch() {
        EtlJobClaim claim = runningClaim("[]", LEASE_TOKEN);

        assertEquals(0, executionService.execute(claim));
        assertEquals(0, count("processed_data"));
        assertEquals(1, count("etl_idempotency_records"));
        assertEquals(0, scalar("processed_record_count", Integer.class));
    }

    @Test
    void rejectsStaleAndExpiredLeasesBeforeTargetWork() {
        EtlJobClaim current = runningClaim(TWO_RECORD_PAYLOAD, LEASE_TOKEN);
        EtlJobClaim stale = new EtlJobClaim(
                current.jobRecordId(),
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                current.requestPayload(),
                current.attemptCount(),
                current.claimedAt(),
                current.leaseExpiresAt()
        );

        assertThrows(EtlJobLeaseLostException.class, () -> executionService.execute(stale));
        assertEquals(0, count("processed_data"));
        assertEquals(0, count("etl_idempotency_records"));

        jdbcTemplate.update(
                "UPDATE etl_job_records SET lease_expires_at = ? WHERE job_record_id = ?",
                Timestamp.from(NOW.minusSeconds(1)),
                JOB_RECORD_ID
        );
        assertThrows(EtlJobLeaseLostException.class, () -> executionService.execute(current));
        assertEquals(0, count("processed_data"));
        assertEquals(0, count("etl_idempotency_records"));
    }

    @Test
    void rollsBackTargetAndLedgerWhenTerminalSuccessCannotCommit() {
        EtlJobClaim claim = runningClaim(TWO_RECORD_PAYLOAD, LEASE_TOKEN);
        jdbcTemplate.execute("""
                ALTER TABLE etl_job_records
                ADD CONSTRAINT etl_job_forced_terminal_failure_check
                CHECK (job_status <> 'SUCCEEDED')
                """);

        assertThrows(DataAccessException.class, () -> executionService.execute(claim));

        assertEquals(0, count("processed_data"));
        assertEquals(0, count("etl_idempotency_records"));
        assertEquals("RUNNING", scalar("job_status", String.class));
        assertEquals(TWO_RECORD_PAYLOAD, scalar("request_payload", String.class));
        assertEquals(LEASE_TOKEN, scalar("lease_token", UUID.class));
        assertNull(scalar("processed_record_count", Integer.class));
    }

    @Test
    void replaysTheJobLedgerWithoutDuplicatingTargetEffects() {
        EtlJobClaim first = runningClaim(
                "[{\"id\":\"record_alpha\",\"name\":\"Ada\"}]",
                LEASE_TOKEN
        );
        executionService.execute(first);
        UUID recoveryToken = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        jdbcTemplate.update(
                """
                UPDATE etl_job_records
                SET job_status = 'RUNNING',
                    request_payload = ?,
                    lease_token = ?,
                    lease_expires_at = ?,
                    processed_record_count = NULL,
                    updated_at = ?
                WHERE job_record_id = ?
                """,
                first.requestPayload(),
                recoveryToken,
                Timestamp.from(NOW.plusSeconds(300)),
                Timestamp.from(NOW),
                JOB_RECORD_ID
        );
        EtlJobClaim recovered = new EtlJobClaim(
                JOB_RECORD_ID,
                recoveryToken,
                first.requestPayload(),
                2,
                NOW,
                NOW.plusSeconds(300)
        );

        assertEquals(1, executionService.execute(recovered));
        assertEquals(1, count("processed_data"));
        assertEquals(1, count("etl_idempotency_records"));
        assertEquals("SUCCEEDED", scalar("job_status", String.class));
    }

    private EtlJobClaim runningClaim(String payload, UUID leaseToken) {
        EtlJobTestDatabase.insertRunning(
                jdbcTemplate,
                JOB_RECORD_ID,
                leaseToken,
                payload,
                1,
                NOW.plusSeconds(300),
                NOW.minusSeconds(60)
        );
        return new EtlJobClaim(
                JOB_RECORD_ID,
                leaseToken,
                payload,
                1,
                NOW,
                NOW.plusSeconds(300)
        );
    }

    private int count(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private <T> T scalar(String columnName, Class<T> type) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM etl_job_records WHERE job_record_id = ?",
                type,
                JOB_RECORD_ID
        );
    }

    /**
     * Real transaction composition context with deterministic lease time.
     */
    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            return EtlJobTestDatabase.newDataSource();
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
            return idempotencyKeyHash -> true;
        }

        @Bean
        EtlService etlService(
                JdbcTemplate jdbcTemplate,
                ObjectMapper objectMapper,
                EtlBatchProperties properties,
                EtlRequestLock requestLock
        ) {
            return new EtlService(jdbcTemplate, objectMapper, properties, requestLock);
        }

        @Bean
        Clock etlJobExecutionClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        EtlJobExecutionService etlJobExecutionService(
                JdbcTemplate jdbcTemplate,
                EtlService etlService,
                Clock etlJobExecutionClock
        ) {
            return new EtlJobExecutionService(jdbcTemplate, etlService, etlJobExecutionClock);
        }
    }
}
