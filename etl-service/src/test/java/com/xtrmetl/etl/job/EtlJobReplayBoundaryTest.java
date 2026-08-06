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
 * Covers immutable replay-result construction and fail-closed admission before persistence.
 */
class EtlJobReplayBoundaryTest {

    private static final UUID SOURCE_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String REPLAY_KEY = "1e05bdca-447c-4ad3-882c-e33963ce517c";

    @AfterEach
    void clearSyntheticTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void replayResultRequiresIdentityAndStatusButPreservesCurrentState() {
        EtlJobReplay pending = new EtlJobReplay(SOURCE_ID, EtlJobStatus.PENDING, false);
        EtlJobReplay terminalReplay = new EtlJobReplay(
                SOURCE_ID,
                EtlJobStatus.SUCCEEDED,
                true
        );

        assertEquals(EtlJobStatus.PENDING, pending.jobStatus());
        assertEquals(EtlJobStatus.SUCCEEDED, terminalReplay.jobStatus());
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplay(null, EtlJobStatus.PENDING, false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplay(SOURCE_ID, null, false)
        );
    }

    @Test
    void validatesIdentityKeyPrincipalAndPayloadBeforeDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlJobReplayService service = service(jdbcTemplate, lockHash -> true);

        assertThrows(
                NullPointerException.class,
                () -> service.replayOwned(null, PAYLOAD, REPLAY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.JOB_REPLAY_KEY_REQUIRED,
                () -> service.replayOwned(SOURCE_ID, PAYLOAD, null, "tenant_alpha")
        );
        assertError(
                EtlRequestError.JOB_REPLAY_KEY_REQUIRED,
                () -> service.replayOwned(SOURCE_ID, PAYLOAD, "unsafe key", "tenant_alpha")
        );
        assertError(
                EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED,
                () -> service.replayOwned(SOURCE_ID, PAYLOAD, REPLAY_KEY, null)
        );
        assertError(
                EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED,
                () -> service.replayOwned(SOURCE_ID, PAYLOAD, REPLAY_KEY, " ".repeat(513))
        );
        assertError(
                EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED,
                () -> service.replayOwned(SOURCE_ID, PAYLOAD, REPLAY_KEY, "a".repeat(513))
        );
        assertError(
                EtlRequestError.INVALID_JSON,
                () -> service.replayOwned(SOURCE_ID, null, REPLAY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_JSON,
                () -> service.replayOwned(SOURCE_ID, "", REPLAY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_JSON,
                () -> service.replayOwned(SOURCE_ID, "null", REPLAY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_JSON,
                () -> service.replayOwned(SOURCE_ID, "not-json", REPLAY_KEY, "tenant_alpha")
        );
        assertError(
                EtlRequestError.INVALID_JSON,
                () -> service.replayOwned(SOURCE_ID, "{}", REPLAY_KEY, "tenant_alpha")
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void requiresAnActualTransactionBeforeLockOrTableAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlJobReplayService service = service(jdbcTemplate, lockHash -> true);

        IllegalStateException nonEmptyBatch = assertThrows(
                IllegalStateException.class,
                () -> service.replayOwned(
                        SOURCE_ID,
                        PAYLOAD,
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );
        IllegalStateException emptyBatch = assertThrows(
                IllegalStateException.class,
                () -> service.replayOwned(
                        SOURCE_ID,
                        "[]",
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );

        assertEquals(
                "Durable ETL job replay requires an active transaction",
                nonEmptyBatch.getMessage()
        );
        assertEquals(nonEmptyBatch.getMessage(), emptyBatch.getMessage());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void reportsAnUnavailableReplayLockWithoutTableAccess() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlJobReplayService service = service(jdbcTemplate, lockHash -> false);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertError(
                EtlRequestError.JOB_REPLAY_IN_PROGRESS,
                () -> service.replayOwned(
                        SOURCE_ID,
                        PAYLOAD,
                        REPLAY_KEY,
                        "tenant_alpha"
                )
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void constructorsRejectMissingCollaborators() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        EtlBatchProperties properties = new EtlBatchProperties();

        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplayService(null, mapper, properties, hash -> true)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplayService(jdbcTemplate, null, properties, hash -> true)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplayService(jdbcTemplate, mapper, null, hash -> true)
        );
        assertThrows(
                NullPointerException.class,
                () -> new EtlJobReplayService(jdbcTemplate, mapper, properties, null)
        );
        new EtlJobReplayService(jdbcTemplate, mapper, properties);
    }

    private static EtlJobReplayService service(
            JdbcTemplate jdbcTemplate,
            com.xtrmetl.etl.service.EtlRequestLock requestLock
    ) {
        return new EtlJobReplayService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties(),
                requestLock
        );
    }

    private static void assertError(EtlRequestError expected, Runnable invocation) {
        EtlRequestException exception = assertThrows(EtlRequestException.class, invocation::run);
        assertEquals(expected, exception.error());
    }
}
