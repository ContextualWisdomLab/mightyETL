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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises two worker instances against one shared durable queue and target database.
 */
@SpringJUnitConfig(EtlJobWorkerEndToEndTest.TestConfiguration.class)
class EtlJobWorkerEndToEndTest {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final UUID JOB_RECORD_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );

    private final EtlJobStore jobStore;
    private final EtlJobExecutionService executionService;
    private final EtlJobWorkerProperties properties;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EtlJobWorkerEndToEndTest(
            EtlJobStore jobStore,
            EtlJobExecutionService executionService,
            EtlJobWorkerProperties properties,
            JdbcTemplate jdbcTemplate
    ) {
        this.jobStore = jobStore;
        this.executionService = executionService;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void resetSchema() {
        EtlJobTestDatabase.createSchema(jdbcTemplate);
        properties.setMaxAttempts(3);
        properties.setLeaseDurationMillis(300_000L);
        properties.setRetryDelayMillis(5_000L);
        properties.setJobsPerPoll(1);
    }

    @Test
    void twoReplicasProduceExactlyOneTargetEffectForOneQueuedJob() throws Exception {
        EtlJobTestDatabase.insertPending(
                jdbcTemplate,
                JOB_RECORD_ID,
                "[{\"id\":\"record_alpha\",\"name\":\"Ada\"}]",
                0,
                NOW.minusSeconds(1),
                NOW.minusSeconds(1)
        );
        EtlJobWorker firstWorker = new EtlJobWorker(jobStore, executionService, properties);
        EtlJobWorker secondWorker = new EtlJobWorker(jobStore, executionService, properties);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return firstWorker.runOnce();
            });
            Future<Boolean> second = executor.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return secondWorker.runOnce();
            });
            start.countDown();

            boolean firstClaimed = first.get(10, TimeUnit.SECONDS);
            boolean secondClaimed = second.get(10, TimeUnit.SECONDS);

            assertNotEquals(firstClaimed, secondClaimed);
        }

        assertEquals(1, count("processed_data"));
        assertEquals(1, count("etl_idempotency_records"));
        assertEquals(
                "SUCCEEDED",
                jdbcTemplate.queryForObject(
                        "SELECT job_status FROM etl_job_records WHERE job_record_id = ?",
                        String.class,
                        JOB_RECORD_ID
                )
        );
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        "SELECT processed_record_count FROM etl_job_records WHERE job_record_id = ?",
                        Integer.class,
                        JOB_RECORD_ID
                )
        );
    }

    private int count(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    /**
     * Shared multi-worker transaction context.
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
        Clock etlJobWorkerClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
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
        EtlJobWorkerProperties etlJobWorkerProperties() {
            return new EtlJobWorkerProperties();
        }

        @Bean
        EtlJobStore etlJobStore(
                JdbcTemplate jdbcTemplate,
                EtlJobWorkerProperties properties,
                Clock etlJobWorkerClock
        ) {
            return new EtlJobStore(jdbcTemplate, properties, etlJobWorkerClock);
        }

        @Bean
        EtlJobExecutionService etlJobExecutionService(
                JdbcTemplate jdbcTemplate,
                EtlService etlService,
                Clock etlJobWorkerClock
        ) {
            return new EtlJobExecutionService(jdbcTemplate, etlService, etlJobWorkerClock);
        }
    }
}
