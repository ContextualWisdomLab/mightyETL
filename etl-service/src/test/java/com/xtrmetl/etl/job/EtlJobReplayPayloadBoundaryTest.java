package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Covers replay payload byte, record-count, and ordinary record-contract rejection before JDBC.
 */
class EtlJobReplayPayloadBoundaryTest {

    private static final UUID SOURCE_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final String REPLAY_KEY = "1e05bdca-447c-4ad3-882c-e33963ce517c";

    @AfterEach
    void clearSyntheticTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void rejectsOversizedPayloadBeforeLockOrTableAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlBatchProperties properties = new EtlBatchProperties();
        properties.setMaxPayloadBytes(8);
        EtlJobReplayService service = service(jdbcTemplate, properties);

        assertError(
                EtlRequestError.PAYLOAD_TOO_LARGE,
                () -> service.replayOwned(
                        SOURCE_ID,
                        "[{\"id\":\"record_alpha\"}]",
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsOversizedBatchAndInvalidRecordsBeforeLockOrTableAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlBatchProperties properties = new EtlBatchProperties();
        properties.setMaxBatchRecords(1);
        EtlJobReplayService service = service(jdbcTemplate, properties);

        assertError(
                EtlRequestError.BATCH_TOO_LARGE,
                () -> service.replayOwned(
                        SOURCE_ID,
                        "[{\"id\":\"a\"},{\"id\":\"b\"}]",
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );
        assertError(
                EtlRequestError.INVALID_RECORD,
                () -> service.replayOwned(
                        SOURCE_ID,
                        "[{}]",
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );
        verifyNoInteractions(jdbcTemplate);
    }

    private static EtlJobReplayService service(
            JdbcTemplate jdbcTemplate,
            EtlBatchProperties properties
    ) {
        return new EtlJobReplayService(
                jdbcTemplate,
                new ObjectMapper(),
                properties,
                lockHash -> true
        );
    }

    private static void assertError(EtlRequestError expected, Runnable invocation) {
        EtlRequestException exception = assertThrows(EtlRequestException.class, invocation::run);
        assertEquals(expected, exception.error());
    }
}
