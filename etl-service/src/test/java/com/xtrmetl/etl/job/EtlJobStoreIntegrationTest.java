package com.xtrmetl.etl.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises queue claiming, lease reclaim, retry, and terminal failure against a real database.
 */
@SpringJUnitConfig(EtlJobStoreIntegrationTest.TestConfiguration.class)
class EtlJobStoreIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";

    private final EtlJobStore jobStore;
    private final EtlJobWorkerProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    @Autowired
    EtlJobStoreIntegrationTest(
            EtlJobStore jobStore,
            EtlJobWorkerProperties properties,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this.jobStore = jobStore;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    @BeforeEach
    void resetSchema() {
        EtlJobTestDatabase.createSchema(jdbcTemplate);
        properties.setLeaseDurationMillis(300_000L);
        properties.setRetryDelayMillis(5_000L);
        properties.setMaxAttempts(3);
    }

    @Test
    void claimsTheOldestEligibleJobAndRecordsAnOpaqueLease() {
        UUID older = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID newer = UUID.fromString("22222222-2222-4222-8222-222222222222");
        EtlJobTestDatabase.insertPending(
                jdbcTemplate,
                newer,
                PAYLOAD,
                0,
                NOW.minusSeconds(1),
                NOW.minusSeconds(30)
        );
        EtlJobTestDatabase.insertPending(
                jdbcTemplate,
                older,
                PAYLOAD,
                0,
                NOW.minusSeconds(1),
                NOW.minusSeconds(60)
        );

        EtlJobClaim claim = jobStore.claimNext();

        assertEquals(older, claim.jobRecordId());
        assertEquals(PAYLOAD, claim.requestPayload());
        assertEquals(1, claim.attemptCount());
        assertEquals(NOW, claim.claimedAt());
        assertEquals(NOW.plusMillis(300_000L), claim.leaseExpiresAt());
        assertEquals(
                "RUNNING",
                jdbcTemplate.queryForObject(
                        "SELECT job_status FROM etl_job_records WHERE job_record_id = ?",
                        String.class,
                        older
                )
        );
        assertEquals(
                claim.leaseToken(),
                jdbcTemplate.queryForObject(
                        "SELECT lease_token FROM etl_job_records WHERE job_record_id = ?",
                        UUID.class,
                        older
                )
        );
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT attempt_count FROM etl_job_records WHERE job_record_id = ?",
                        Integer.class,
                        newer
                )
        );
    }

    @Test
    void skipsARowLockedByAnotherTransactionWithoutWaiting() throws Exception {
        UUID jobRecordId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        EtlJobTestDatabase.insertPending(
                jdbcTemplate,
                jobRecordId,
                PAYLOAD,
                0,
                NOW.minusSeconds(1),
                NOW.minusSeconds(1)
        );
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT job_record_id FROM etl_job_records "
                                + "WHERE job_record_id = ? FOR UPDATE",
                        UUID.class,
                        jobRecordId
                );
                locked.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));

            assertTrue(locked.await(5, TimeUnit.SECONDS));
            long startedAt = System.nanoTime();
            EtlJobClaim claim = jobStore.claimNext();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);

            assertNull(claim);
            assertTrue(elapsedMillis < 2_000L);
        }
    }

    @Test
    void reclaimsAnExpiredRunningJobWithANewTokenAndIncrementedAttempt() {
        UUID jobRecordId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        UUID expiredToken = UUID.fromString("55555555-5555-4555-8555-555555555555");
        EtlJobTestDatabase.insertRunning(
                jdbcTemplate,
                jobRecordId,
                expiredToken,
                PAYLOAD,
                1,
                NOW.minusSeconds(1),
                NOW.minusSeconds(60)
        );

        EtlJobClaim claim = jobStore.claimNext();

        assertEquals(jobRecordId, claim.jobRecordId());
        assertEquals(2, claim.attemptCount());
        assertNotEquals(expiredToken, claim.leaseToken());
        assertEquals(NOW.plusMillis(300_000L), claim.leaseExpiresAt());
    }

    @Test
    void ignoresFuturePendingAndAttemptExhaustedRows() {
        EtlJobTestDatabase.insertPending(
                jdbcTemplate,
                UUID.fromString("66666666-6666-4666-8666-666666666666"),
                PAYLOAD,
                0,
                NOW.plusSeconds(1),
                NOW.minusSeconds(1)
        );
        EtlJobTestDatabase.insertPending(
                jdbcTemplate,
                UUID.fromString("77777777-7777-4777-8777-777777777777"),
                PAYLOAD,
                3,
                NOW.minusSeconds(1),
                NOW.minusSeconds(1)
        );

        assertNull(jobStore.claimNext());
    }

    @Test
    void requeuesRetryableFailureThenFailsWhenAttemptsAreExhausted() {
        properties.setMaxAttempts(2);
        UUID jobRecordId = UUID.fromString("88888888-8888-4888-8888-888888888888");
        EtlJobTestDatabase.insertPending(
                jdbcTemplate,
                jobRecordId,
                PAYLOAD,
                0,
                NOW.minusSeconds(1),
                NOW.minusSeconds(1)
        );
        EtlJobClaim firstClaim = jobStore.claimNext();

        assertTrue(jobStore.recordFailure(firstClaim, "etl_target_unavailable", true));
        assertEquals(
                "PENDING",
                jdbcTemplate.queryForObject(
                        "SELECT job_status FROM etl_job_records WHERE job_record_id = ?",
                        String.class,
                        jobRecordId
                )
        );
        assertEquals(
                PAYLOAD,
                jdbcTemplate.queryForObject(
                        "SELECT request_payload FROM etl_job_records WHERE job_record_id = ?",
                        String.class,
                        jobRecordId
                )
        );
        assertNull(jdbcTemplate.queryForObject(
                "SELECT lease_token FROM etl_job_records WHERE job_record_id = ?",
                UUID.class,
                jobRecordId
        ));
        assertEquals(
                NOW.plusMillis(5_000L),
                jdbcTemplate.queryForObject(
                        "SELECT next_attempt_at FROM etl_job_records WHERE job_record_id = ?",
                        Timestamp.class,
                        jobRecordId
                ).toInstant()
        );
        assertNull(jobStore.claimNext());

        jdbcTemplate.update(
                "UPDATE etl_job_records SET next_attempt_at = ? WHERE job_record_id = ?",
                Timestamp.from(NOW.minusMillis(1)),
                jobRecordId
        );
        EtlJobClaim secondClaim = jobStore.claimNext();
        assertEquals(2, secondClaim.attemptCount());
        assertTrue(jobStore.recordFailure(secondClaim, "etl_target_unavailable", true));

        assertEquals(
                "FAILED",
                jdbcTemplate.queryForObject(
                        "SELECT job_status FROM etl_job_records WHERE job_record_id = ?",
                        String.class,
                        jobRecordId
                )
        );
        assertNull(jdbcTemplate.queryForObject(
                "SELECT request_payload FROM etl_job_records WHERE job_record_id = ?",
                String.class,
                jobRecordId
        ));
        assertEquals(
                "etl_target_unavailable",
                jdbcTemplate.queryForObject(
                        "SELECT failure_code FROM etl_job_records WHERE job_record_id = ?",
                        String.class,
                        jobRecordId
                )
        );
    }

    @Test
    void recordsImmediateTerminalFailureAndRejectsAStaleToken() {
        UUID jobRecordId = UUID.fromString("99999999-9999-4999-8999-999999999999");
        EtlJobTestDatabase.insertPending(
                jdbcTemplate,
                jobRecordId,
                PAYLOAD,
                0,
                NOW.minusSeconds(1),
                NOW.minusSeconds(1)
        );
        EtlJobClaim claim = jobStore.claimNext();
        EtlJobClaim stale = new EtlJobClaim(
                claim.jobRecordId(),
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                claim.requestPayload(),
                claim.attemptCount(),
                claim.claimedAt(),
                claim.leaseExpiresAt()
        );

        assertFalse(jobStore.recordFailure(stale, "etl_internal_error", false));
        assertEquals(
                "RUNNING",
                jdbcTemplate.queryForObject(
                        "SELECT job_status FROM etl_job_records WHERE job_record_id = ?",
                        String.class,
                        jobRecordId
                )
        );
        assertTrue(jobStore.recordFailure(claim, "etl_invalid_record", false));
        assertEquals(
                "FAILED",
                jdbcTemplate.queryForObject(
                        "SELECT job_status FROM etl_job_records WHERE job_record_id = ?",
                        String.class,
                        jobRecordId
                )
        );
        assertEquals(
                "etl_invalid_record",
                jdbcTemplate.queryForObject(
                        "SELECT failure_code FROM etl_job_records WHERE job_record_id = ?",
                        String.class,
                        jobRecordId
                )
        );
    }

    /**
     * Transaction-enabled worker-store test context with a deterministic clock.
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
        EtlJobWorkerProperties etlJobWorkerProperties() {
            return new EtlJobWorkerProperties();
        }

        @Bean
        Clock etlJobWorkerClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        EtlJobStore etlJobStore(
                JdbcTemplate jdbcTemplate,
                EtlJobWorkerProperties properties,
                Clock etlJobWorkerClock
        ) {
            return new EtlJobStore(jdbcTemplate, properties, etlJobWorkerClock);
        }
    }
}
