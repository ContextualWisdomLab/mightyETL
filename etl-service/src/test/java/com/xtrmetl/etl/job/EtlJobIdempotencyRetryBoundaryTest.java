package com.xtrmetl.etl.job;

import com.xtrmetl.etl.service.EtlRequestLock;
import com.xtrmetl.etl.service.EtlService;
import com.xtrmetl.etl.service.Sha256Digest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the retry boundary between durable database attempts and synchronous request retries.
 *
 * <p>A durable worker already owns bounded, persisted retries through {@code attempt_count}. It
 * must therefore invoke an ETL entry point that joins the current lease transaction exactly once,
 * rather than the synchronous {@code @Retryable} API whose advice is designed to wrap a fresh
 * transaction for each HTTP-request attempt.</p>
 */
class EtlJobIdempotencyRetryBoundaryTest {

    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String RESPONSE = "Processed: record_alpha";

    /**
     * Proves one durable attempt uses only the non-retrying current-transaction ETL entry point.
     */
    @Test
    void durableAttemptDoesNotInvokeTheSynchronousRetryableEntryPoint() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .build();
        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
            jdbcTemplate.execute("""
                    CREATE TABLE etl_idempotency_records (
                        idempotency_key_hash CHAR(64) PRIMARY KEY,
                        request_digest CHAR(64) NOT NULL,
                        response_body CLOB NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            EtlService etlService = mock(EtlService.class);
            EtlRequestLock requestLock = mock(EtlRequestLock.class);
            when(requestLock.tryLock(anyString())).thenReturn(true);
            when(etlService.processDataInExistingTransaction(PAYLOAD)).thenReturn(RESPONSE);
            EtlJobIdempotencyService service = new EtlJobIdempotencyService(
                    jdbcTemplate,
                    etlService,
                    requestLock
            );
            TransactionTemplate transactionTemplate = new TransactionTemplate(
                    new DataSourceTransactionManager(database)
            );

            String result = transactionTemplate.execute(status -> service.process(lease()));

            assertEquals(RESPONSE, result);
            verify(etlService).processDataInExistingTransaction(PAYLOAD);
            verify(etlService, never()).processData(anyString());
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM etl_idempotency_records",
                            Integer.class
                    )
            );
        } finally {
            database.shutdown();
        }
    }

    private static EtlJobLease lease() {
        return new EtlJobLease(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "worker-alpha",
                "a".repeat(64),
                "b".repeat(64),
                Sha256Digest.digest(PAYLOAD),
                PAYLOAD,
                1,
                Instant.now().plusSeconds(300)
        );
    }
}
