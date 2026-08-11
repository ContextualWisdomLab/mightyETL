package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.EtlRequestLock;
import org.junit.jupiter.api.AfterEach;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves concurrent owner cancellation requests converge on one authoritative terminal transition.
 */
@SpringJUnitConfig(EtlJobCancellationConcurrencyIntegrationTest.TestConfiguration.class)
class EtlJobCancellationConcurrencyIntegrationTest {

    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String SUBMISSION_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String CANCELLATION_KEY = "70dc8b50-e8b2-4e1a-8c5f-d84814708a77";
    private static final String OTHER_CANCELLATION_KEY =
            "a52b165f-9d45-4399-ae84-1e93e8fe1e68";

    private final EtlJobService jobService;
    private final JdbcTemplate jdbcTemplate;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    EtlJobCancellationConcurrencyIntegrationTest(
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

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void concurrentIdenticalRequestsProduceOneTransitionAndOneReplay() throws Exception {
        UUID jobRecordId = submitPendingJob();
        List<Future<EtlJobCancellation>> futures = startTogether(
                jobRecordId,
                List.of(CANCELLATION_KEY, CANCELLATION_KEY)
        );

        List<EtlJobCancellation> results = List.of(
                futures.get(0).get(10, TimeUnit.SECONDS),
                futures.get(1).get(10, TimeUnit.SECONDS)
        );

        assertEquals(1, results.stream().filter(result -> !result.replayed()).count());
        assertEquals(1, results.stream().filter(EtlJobCancellation::replayed).count());
        assertTrue(results.stream().allMatch(
                result -> result.snapshot().jobStatus() == EtlJobStatus.CANCELLED
        ));
        assertEquals(1, cancelledRowCount(jobRecordId));
    }

    @Test
    void concurrentDifferentKeysProduceOneTransitionAndOneStableConflict() throws Exception {
        UUID jobRecordId = submitPendingJob();
        List<Future<EtlJobCancellation>> futures = startTogether(
                jobRecordId,
                List.of(CANCELLATION_KEY, OTHER_CANCELLATION_KEY)
        );

        int successes = 0;
        int keyConflicts = 0;
        for (Future<EtlJobCancellation> future : futures) {
            try {
                EtlJobCancellation result = future.get(10, TimeUnit.SECONDS);
                assertFalse(result.replayed());
                successes++;
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                EtlRequestException requestException = assertThrows(
                        EtlRequestException.class,
                        () -> {
                            throw cause;
                        }
                );
                assertEquals(
                        EtlRequestError.JOB_CANCELLATION_KEY_REUSED,
                        requestException.error()
                );
                keyConflicts++;
            }
        }

        assertEquals(1, successes);
        assertEquals(1, keyConflicts);
        assertEquals(1, cancelledRowCount(jobRecordId));
    }

    private UUID submitPendingJob() {
        return jobService.submit(PAYLOAD, SUBMISSION_KEY, "tenant_alpha").jobRecordId();
    }

    private List<Future<EtlJobCancellation>> startTogether(
            UUID jobRecordId,
            List<String> cancellationKeys
    ) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(cancellationKeys.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EtlJobCancellation>> futures = new ArrayList<>();
        for (String cancellationKey : cancellationKeys) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("concurrent cancellation start timed out");
                }
                return jobService.cancelOwned(
                        jobRecordId,
                        cancellationKey,
                        "tenant_alpha"
                );
            }));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        return List.copyOf(futures);
    }

    private int cancelledRowCount(UUID jobRecordId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM etl_job_records
                WHERE job_record_id = ?
                  AND job_status = 'CANCELLED'
                  AND request_payload IS NULL
                  AND lease_claim_id IS NULL
                  AND lease_owner_id IS NULL
                  AND lease_expires_at IS NULL
                  AND cancellation_key_hash IS NOT NULL
                  AND cancellation_code = ?
                  AND job_cancelled_at IS NOT NULL
                """,
                Integer.class,
                jobRecordId,
                EtlJobService.CANCELLED_BY_OWNER_CODE
        );
        return count == null ? 0 : count;
    }

    /** Minimal transaction-enabled context for concurrent cancellation integration. */
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
