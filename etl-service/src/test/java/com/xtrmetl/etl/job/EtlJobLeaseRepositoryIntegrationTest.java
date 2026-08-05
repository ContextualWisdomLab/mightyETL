package com.xtrmetl.etl.job;

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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves exclusive durable-job claims and exact-live-lease state transitions against SQL.
 */
@SpringJUnitConfig(EtlJobLeaseRepositoryIntegrationTest.TestConfiguration.class)
class EtlJobLeaseRepositoryIntegrationTest {

    private static final Duration LEASE_DURATION = Duration.ofMinutes(5);
    private static final String OWNER_ALPHA = "worker-alpha";
    private static final String OWNER_BETA = "worker-beta";
    private static final String PRINCIPAL_SCOPE_HASH = "a".repeat(64);
    private static final String REQUEST_DIGEST = "b".repeat(64);
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";

    private final EtlJobLeaseRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private ExecutorService executorService;

    @Autowired
    EtlJobLeaseRepositoryIntegrationTest(
            EtlJobLeaseRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void createJobTable() {
        executorService = Executors.newFixedThreadPool(2);
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
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
    }

    @AfterEach
    void closeExecutor() {
        executorService.close();
    }

    @Test
    void claimsTheOldestEligibleJobAndIncrementsItsAttempt() {
        UUID newerJobId = insertPending(Instant.parse("2026-08-05T00:01:00Z"), 0);
        UUID olderJobId = insertPending(Instant.parse("2026-08-05T00:00:00Z"), 1);

        EtlJobLease lease = repository.claimNext(OWNER_ALPHA, LEASE_DURATION, 3).orElseThrow();

        assertEquals(olderJobId, lease.jobRecordId());
        assertEquals(OWNER_ALPHA, lease.leaseOwnerId());
        assertEquals(PRINCIPAL_SCOPE_HASH, lease.principalScopeHash());
        assertTrue(lease.submissionKeyHash().matches("[0-9a-f]{64}"));
        assertEquals(REQUEST_DIGEST, lease.requestDigest());
        assertEquals(PAYLOAD, lease.requestPayload());
        assertEquals(2, lease.attemptCount());
        assertNotNull(lease.leaseClaimId());
        assertTrue(lease.leaseExpiresAt().isAfter(Instant.now()));
        assertEquals("RUNNING", textColumn(olderJobId, "job_status"));
        assertEquals(2, integerColumn(olderJobId, "attempt_count"));
        assertEquals("PENDING", textColumn(newerJobId, "job_status"));
    }

    @Test
    void skipsLiveClaimsAndReclaimsExpiredClaimsWithFreshFencing() {
        UUID liveJobId = insertRunning(
                Instant.parse("2026-08-05T00:00:00Z"),
                1,
                OWNER_ALPHA,
                UUID.randomUUID(),
                Instant.now().plusSeconds(300)
        );
        UUID priorClaimId = UUID.randomUUID();
        UUID expiredJobId = insertRunning(
                Instant.parse("2026-08-05T00:01:00Z"),
                1,
                OWNER_ALPHA,
                priorClaimId,
                Instant.now().minusSeconds(60)
        );

        EtlJobLease reclaimed = repository.claimNext(OWNER_BETA, LEASE_DURATION, 3).orElseThrow();

        assertEquals(expiredJobId, reclaimed.jobRecordId());
        assertEquals(OWNER_BETA, reclaimed.leaseOwnerId());
        assertNotEquals(priorClaimId, reclaimed.leaseClaimId());
        assertEquals(2, reclaimed.attemptCount());
        assertEquals("RUNNING", textColumn(liveJobId, "job_status"));
        assertEquals(OWNER_ALPHA, textColumn(liveJobId, "lease_owner_id"));
    }

    @Test
    void returnsEmptyWhenEveryClaimIsLive() {
        insertRunning(
                Instant.now(),
                1,
                OWNER_ALPHA,
                UUID.randomUUID(),
                Instant.now().plusSeconds(300)
        );

        Optional<EtlJobLease> lease = repository.claimNext(OWNER_BETA, LEASE_DURATION, 3);

        assertTrue(lease.isEmpty());
    }

    @Test
    void onlyOneConcurrentWorkerCanClaimOnePendingJob() throws Exception {
        UUID jobRecordId = insertPending(Instant.now(), 0);
        CountDownLatch start = new CountDownLatch(1);

        Future<Optional<EtlJobLease>> alpha = executorService.submit(() -> {
            start.await();
            return repository.claimNext(OWNER_ALPHA, LEASE_DURATION, 3);
        });
        Future<Optional<EtlJobLease>> beta = executorService.submit(() -> {
            start.await();
            return repository.claimNext(OWNER_BETA, LEASE_DURATION, 3);
        });
        start.countDown();

        List<EtlJobLease> claims = List.of(alpha.get(), beta.get()).stream()
                .flatMap(Optional::stream)
                .toList();

        assertEquals(1, claims.size());
        assertEquals(jobRecordId, claims.getFirst().jobRecordId());
        assertEquals(1, integerColumn(jobRecordId, "attempt_count"));
    }

    @Test
    void terminalizesExhaustedEligibleRowsBeforeLookingForWork() {
        UUID pendingJobId = insertPending(Instant.now(), 3);
        UUID expiredJobId = insertRunning(
                Instant.now().plusSeconds(1),
                3,
                OWNER_ALPHA,
                UUID.randomUUID(),
                Instant.now().minusSeconds(60)
        );

        Optional<EtlJobLease> lease = repository.claimNext(OWNER_BETA, LEASE_DURATION, 3);

        assertTrue(lease.isEmpty());
        assertTerminalExhaustion(pendingJobId);
        assertTerminalExhaustion(expiredJobId);
    }

    @Test
    void exactLiveLeaseCanSucceedRetryOrFailAndClearsTheRightFields() {
        UUID successJobId = insertPending(Instant.parse("2026-08-05T00:00:00Z"), 0);
        EtlJobLease successLease = repository.claimNext(OWNER_ALPHA, LEASE_DURATION, 3)
                .orElseThrow();
        repository.markSucceeded(successLease);
        assertEquals(successJobId, successLease.jobRecordId());
        assertEquals("SUCCEEDED", textColumn(successJobId, "job_status"));
        assertNull(textColumn(successJobId, "request_payload"));
        assertNull(textColumn(successJobId, "failure_code"));
        assertNull(textColumn(successJobId, "lease_owner_id"));

        UUID retryJobId = insertPending(Instant.parse("2026-08-05T00:01:00Z"), 0);
        EtlJobLease retryLease = repository.claimNext(OWNER_ALPHA, LEASE_DURATION, 3)
                .orElseThrow();
        repository.releaseForRetry(retryLease, 3);
        assertEquals(retryJobId, retryLease.jobRecordId());
        assertEquals("PENDING", textColumn(retryJobId, "job_status"));
        assertEquals(PAYLOAD, textColumn(retryJobId, "request_payload"));
        assertNull(textColumn(retryJobId, "failure_code"));
        assertNull(textColumn(retryJobId, "lease_owner_id"));

        UUID failedJobId = insertPending(Instant.parse("2026-08-05T00:00:30Z"), 2);
        EtlJobLease failedLease = repository.claimNext(OWNER_ALPHA, LEASE_DURATION, 3)
                .orElseThrow();
        repository.markFailed(failedLease, "etl_target_failure");
        assertEquals(failedJobId, failedLease.jobRecordId());
        assertEquals("FAILED", textColumn(failedJobId, "job_status"));
        assertNull(textColumn(failedJobId, "request_payload"));
        assertEquals("etl_target_failure", textColumn(failedJobId, "failure_code"));
        assertNull(textColumn(failedJobId, "lease_owner_id"));
    }

    @Test
    void rejectsExpiredSupersededOrExhaustedTransitions() {
        UUID jobRecordId = insertPending(Instant.now(), 0);
        EtlJobLease lease = repository.claimNext(OWNER_ALPHA, LEASE_DURATION, 1).orElseThrow();
        jdbcTemplate.update(
                "UPDATE etl_job_records SET lease_expires_at = ? WHERE job_record_id = ?",
                Instant.now().minusSeconds(1),
                jobRecordId
        );

        assertThrows(StaleEtlJobLeaseException.class, () -> repository.markSucceeded(lease));
        assertThrows(
                StaleEtlJobLeaseException.class,
                () -> repository.releaseForRetry(lease, 1)
        );
        assertThrows(
                StaleEtlJobLeaseException.class,
                () -> repository.markFailed(lease, "etl_target_failure")
        );

        jdbcTemplate.update(
                """
                UPDATE etl_job_records
                SET lease_claim_id = ?, lease_expires_at = ?
                WHERE job_record_id = ?
                """,
                UUID.randomUUID(),
                Instant.now().plusSeconds(300),
                jobRecordId
        );
        assertThrows(StaleEtlJobLeaseException.class, () -> repository.markSucceeded(lease));
    }

    @Test
    void rejectsInvalidPublicArgumentsBeforeSqlExecution() {
        assertThrows(NullPointerException.class, () -> repository.claimNext(null, LEASE_DURATION, 3));
        assertThrows(NullPointerException.class, () -> repository.claimNext(OWNER_ALPHA, null, 3));
        assertThrows(IllegalArgumentException.class, () -> repository.claimNext("short", LEASE_DURATION, 3));
        assertThrows(IllegalArgumentException.class, () -> repository.claimNext(OWNER_ALPHA, Duration.ZERO, 3));
        assertThrows(IllegalArgumentException.class, () -> repository.claimNext(OWNER_ALPHA, Duration.ofSeconds(-1), 3));
        assertThrows(IllegalArgumentException.class, () -> repository.claimNext(OWNER_ALPHA, LEASE_DURATION, 0));
        assertThrows(IllegalArgumentException.class, () -> repository.claimNext(OWNER_ALPHA, LEASE_DURATION, 101));
        assertThrows(NullPointerException.class, () -> repository.markSucceeded(null));
        assertThrows(NullPointerException.class, () -> repository.releaseForRetry(null, 3));
        assertThrows(IllegalArgumentException.class, () -> repository.releaseForRetry(sampleLease(), 0));
        assertThrows(NullPointerException.class, () -> repository.markFailed(null, "etl_target_failure"));
        assertThrows(NullPointerException.class, () -> repository.markFailed(sampleLease(), null));
        assertThrows(IllegalArgumentException.class, () -> repository.markFailed(sampleLease(), "UNSAFE FAILURE"));
    }

    private UUID insertPending(Instant createdAt, int attemptCount) {
        UUID jobRecordId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO etl_job_records (
                    job_record_id, principal_scope_hash, submission_key_hash,
                    request_digest, request_payload, job_status, attempt_count,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)
                """,
                jobRecordId,
                PRINCIPAL_SCOPE_HASH,
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                REQUEST_DIGEST,
                PAYLOAD,
                attemptCount,
                createdAt,
                createdAt
        );
        return jobRecordId;
    }

    private UUID insertRunning(
            Instant createdAt,
            int attemptCount,
            String ownerId,
            UUID claimId,
            Instant expiresAt
    ) {
        UUID jobRecordId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO etl_job_records (
                    job_record_id, principal_scope_hash, submission_key_hash,
                    request_digest, request_payload, job_status, attempt_count,
                    lease_claim_id, lease_owner_id, lease_expires_at,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?, ?, ?)
                """,
                jobRecordId,
                "c".repeat(64),
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                "d".repeat(64),
                PAYLOAD,
                attemptCount,
                claimId,
                ownerId,
                expiresAt,
                createdAt,
                createdAt
        );
        return jobRecordId;
    }

    private void assertTerminalExhaustion(UUID jobRecordId) {
        assertEquals("FAILED", textColumn(jobRecordId, "job_status"));
        assertEquals("etl_worker_attempts_exhausted", textColumn(jobRecordId, "failure_code"));
        assertNull(textColumn(jobRecordId, "request_payload"));
        assertNull(textColumn(jobRecordId, "lease_owner_id"));
    }

    private String textColumn(UUID jobRecordId, String columnName) {
        return jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM etl_job_records WHERE job_record_id = ?",
                String.class,
                jobRecordId
        );
    }

    private int integerColumn(UUID jobRecordId, String columnName) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT " + columnName + " FROM etl_job_records WHERE job_record_id = ?",
                Integer.class,
                jobRecordId
        );
        return value == null ? -1 : value;
    }

    private static EtlJobLease sampleLease() {
        return new EtlJobLease(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OWNER_ALPHA,
                PRINCIPAL_SCOPE_HASH,
                "e".repeat(64),
                REQUEST_DIGEST,
                PAYLOAD,
                1,
                Instant.now().plusSeconds(300)
        );
    }

    /** Minimal transaction-enabled SQL context for durable lease persistence tests. */
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
        EtlJobLeaseRepository etlJobLeaseRepository(
                JdbcTemplate jdbcTemplate,
                PlatformTransactionManager transactionManager
        ) {
            return new EtlJobLeaseRepository(jdbcTemplate, transactionManager);
        }
    }
}
