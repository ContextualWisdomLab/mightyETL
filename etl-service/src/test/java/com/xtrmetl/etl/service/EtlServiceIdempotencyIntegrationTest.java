package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the durable ETL idempotency ledger, transaction, principal scope, and concurrency contract.
 */
@SpringJUnitConfig(EtlServiceIdempotencyIntegrationTest.TestConfiguration.class)
class EtlServiceIdempotencyIntegrationTest {

    private static final String IDEMPOTENCY_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String PRINCIPAL_SCOPE = "tenant_alpha";

    private final EtlService etlService;
    private final JdbcTemplate jdbcTemplate;
    private ExecutorService executorService;

    @Autowired
    EtlServiceIdempotencyIntegrationTest(EtlService etlService, JdbcTemplate jdbcTemplate) {
        this.etlService = etlService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void createTargetAndLedgerTables() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS etl_idempotency_records");
        jdbcTemplate.execute("DROP TABLE IF EXISTS processed_data");
        jdbcTemplate.execute("""
                CREATE TABLE processed_data (
                    processed_data_key UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
                    data VARCHAR(4096) NOT NULL,
                    CONSTRAINT valid_processed_data CHECK (data NOT LIKE '%NAME:FAIL,%')
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE etl_idempotency_records (
                    idempotency_key_hash CHAR(64) PRIMARY KEY,
                    request_digest CHAR(64) NOT NULL,
                    response_body VARCHAR(8192) NOT NULL,
                    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
                """);
        executorService = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executorService.shutdownNow();
        assertTrue(executorService.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void replaysTheStoredResponseWithoutWritingTheBatchAgain() {
        String payload = "[{\"id\":\"record_alpha\",\"name\":\"accepted\"}]";

        EtlIdempotencyResult first = etlService.processDataIdempotently(
                payload,
                IDEMPOTENCY_KEY,
                PRINCIPAL_SCOPE
        );
        EtlIdempotencyResult replay = etlService.processDataIdempotently(
                payload,
                IDEMPOTENCY_KEY,
                PRINCIPAL_SCOPE
        );

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.responseBody(), replay.responseBody());
        assertEquals(1, countRows("processed_data"));
        assertEquals(1, countRows("etl_idempotency_records"));
    }

    @Test
    void rejectsReuseOfTheSameScopedKeyWithDifferentPayload() {
        etlService.processDataIdempotently(
                "[{\"id\":\"record_alpha\"}]",
                IDEMPOTENCY_KEY,
                PRINCIPAL_SCOPE
        );

        EtlRequestException exception = assertThrows(
                EtlRequestException.class,
                () -> etlService.processDataIdempotently(
                        "[{\"id\":\"record_beta\"}]",
                        IDEMPOTENCY_KEY,
                        PRINCIPAL_SCOPE
                )
        );

        assertEquals(EtlRequestError.IDEMPOTENCY_KEY_REUSED, exception.error());
        assertEquals(1, countRows("processed_data"));
        assertEquals(1, countRows("etl_idempotency_records"));
    }

    @Test
    void rollsBackBothTheTargetRowsAndLedgerWhenTheBatchFails() {
        String payload = """
                [
                  {"id":"record_alpha","name":"accepted"},
                  {"id":"record_beta","name":"fail"}
                ]
                """;

        assertThrows(
                RuntimeException.class,
                () -> etlService.processDataIdempotently(
                        payload,
                        IDEMPOTENCY_KEY,
                        PRINCIPAL_SCOPE
                )
        );

        assertEquals(0, countRows("processed_data"));
        assertEquals(0, countRows("etl_idempotency_records"));
    }

    @Test
    void isolatesTheSameClientKeyAcrossAuthenticatedPrincipalScopes() {
        etlService.processDataIdempotently(
                "[{\"id\":\"record_alpha\"}]",
                IDEMPOTENCY_KEY,
                "tenant_alpha"
        );
        etlService.processDataIdempotently(
                "[{\"id\":\"record_beta\"}]",
                IDEMPOTENCY_KEY,
                "tenant_beta"
        );

        assertEquals(2, countRows("processed_data"));
        assertEquals(2, countRows("etl_idempotency_records"));
    }

    @Test
    void serializesConcurrentRequestsForTheSameScopedKey() throws Exception {
        String payload = "[{\"id\":\"record_alpha\"}]";
        CountDownLatch startGate = new CountDownLatch(1);

        Future<EtlIdempotencyResult> firstFuture = executorService.submit(() -> {
            startGate.await();
            return etlService.processDataIdempotently(payload, IDEMPOTENCY_KEY, PRINCIPAL_SCOPE);
        });
        Future<EtlIdempotencyResult> secondFuture = executorService.submit(() -> {
            startGate.await();
            return etlService.processDataIdempotently(payload, IDEMPOTENCY_KEY, PRINCIPAL_SCOPE);
        });

        startGate.countDown();
        List<EtlIdempotencyResult> results = List.of(
                firstFuture.get(10, TimeUnit.SECONDS),
                secondFuture.get(10, TimeUnit.SECONDS)
        );

        assertEquals(1, results.stream().filter(EtlIdempotencyResult::replayed).count());
        assertEquals(1, results.stream().filter(result -> !result.replayed()).count());
        assertEquals(1, countRows("processed_data"));
        assertEquals(1, countRows("etl_idempotency_records"));
    }

    @Test
    void rejectsUnsafeOrLowEntropyClientKeysBeforeDatabaseWork() {
        for (String invalidKey : List.of("short", " leading-key-123456", "key with spaces 123456")) {
            EtlRequestException exception = assertThrows(
                    EtlRequestException.class,
                    () -> etlService.processDataIdempotently(
                            "[{\"id\":\"record_alpha\"}]",
                            invalidKey,
                            PRINCIPAL_SCOPE
                    )
            );
            assertEquals(EtlRequestError.INVALID_IDEMPOTENCY_KEY, exception.error());
        }

        assertEquals(0, countRows("processed_data"));
        assertEquals(0, countRows("etl_idempotency_records"));
    }

    private int countRows(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    /**
     * Minimal transaction-enabled context with a transaction-lifetime in-memory request lock.
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
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        EtlBatchProperties etlBatchProperties() {
            return new EtlBatchProperties();
        }

        @Bean
        EtlRequestLock etlRequestLock() {
            return new InMemoryTransactionRequestLock();
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
    }

    /**
     * Test-only lock that mirrors a PostgreSQL transaction advisory lock's release semantics.
     */
    private static final class InMemoryTransactionRequestLock implements EtlRequestLock {

        private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

        @Override
        public void lock(String idempotencyKeyHash) {
            ReentrantLock lock = locks.computeIfAbsent(idempotencyKeyHash, ignored -> new ReentrantLock());
            lock.lock();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    lock.unlock();
                    if (!lock.hasQueuedThreads()) {
                        locks.remove(idempotencyKeyHash, lock);
                    }
                }
            });
        }
    }
}
