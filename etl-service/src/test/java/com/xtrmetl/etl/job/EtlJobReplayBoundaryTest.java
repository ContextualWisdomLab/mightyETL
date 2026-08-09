package com.xtrmetl.etl.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Captures immutable replay-result, construction, and pre-persistence validation contracts.
 */
class EtlJobReplayBoundaryTest {

    private static final UUID SOURCE_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String REPLAY_KEY = "1e05bdca-447c-4ad3-882c-e33963ce517c";
    private static final String PERSISTENCE_NOT_AVAILABLE =
            "Durable ETL job replay persistence is not yet available";

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

    @Test
    void validatesIdentityKeyPrincipalAndPayloadBeforeDatabaseWork() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlJobReplayService service = new EtlJobReplayService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties(),
                hash -> true
        );

        assertThrows(
                NullPointerException.class,
                () -> service.replayOwned(null, PAYLOAD, REPLAY_KEY, "tenant_alpha")
        );
        assertErrorCode(
                "etl_job_replay_key_required",
                () -> service.replayOwned(SOURCE_ID, PAYLOAD, null, "tenant_alpha")
        );
        assertErrorCode(
                "etl_job_replay_key_required",
                () -> service.replayOwned(SOURCE_ID, PAYLOAD, "unsafe key", "tenant_alpha")
        );
        assertErrorCode(
                "etl_idempotency_principal_required",
                () -> service.replayOwned(SOURCE_ID, PAYLOAD, REPLAY_KEY, null)
        );
        assertErrorCode(
                "etl_idempotency_principal_required",
                () -> service.replayOwned(SOURCE_ID, PAYLOAD, REPLAY_KEY, " ".repeat(513))
        );
        assertErrorCode(
                "etl_idempotency_principal_required",
                () -> service.replayOwned(SOURCE_ID, PAYLOAD, REPLAY_KEY, "a".repeat(513))
        );
        assertErrorCode(
                "etl_invalid_json",
                () -> service.replayOwned(SOURCE_ID, null, REPLAY_KEY, "tenant_alpha")
        );
        assertErrorCode(
                "etl_invalid_json",
                () -> service.replayOwned(SOURCE_ID, "", REPLAY_KEY, "tenant_alpha")
        );
        assertErrorCode(
                "etl_invalid_json",
                () -> service.replayOwned(SOURCE_ID, " ", REPLAY_KEY, "tenant_alpha")
        );
        assertErrorCode(
                "etl_invalid_json",
                () -> service.replayOwned(SOURCE_ID, "null", REPLAY_KEY, "tenant_alpha")
        );
        assertErrorCode(
                "etl_invalid_json",
                () -> service.replayOwned(SOURCE_ID, "not-json", REPLAY_KEY, "tenant_alpha")
        );
        assertErrorCode(
                "etl_invalid_json",
                () -> service.replayOwned(SOURCE_ID, "{}", REPLAY_KEY, "tenant_alpha")
        );
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void acceptsRawAndStructuredReplayKeysThenFailsClosedBeforePersistence() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        EtlJobReplayService service = new EtlJobReplayService(
                jdbcTemplate,
                new ObjectMapper(),
                new EtlBatchProperties(),
                hash -> true
        );

        IllegalStateException rawKeyFailure = assertThrows(
                IllegalStateException.class,
                () -> service.replayOwned(SOURCE_ID, PAYLOAD, REPLAY_KEY, "tenant_alpha")
        );
        assertEquals(PERSISTENCE_NOT_AVAILABLE, rawKeyFailure.getMessage());

        IllegalStateException structuredKeyFailure = assertThrows(
                IllegalStateException.class,
                () -> service.replayOwned(
                        SOURCE_ID,
                        PAYLOAD,
                        "\"" + REPLAY_KEY + "\"",
                        "tenant_alpha"
                )
        );
        assertEquals(PERSISTENCE_NOT_AVAILABLE, structuredKeyFailure.getMessage());
        verifyNoInteractions(jdbcTemplate);
    }

    private static void assertErrorCode(String expectedCode, Runnable invocation) {
        EtlRequestException exception = assertThrows(EtlRequestException.class, invocation::run);
        assertEquals(expectedCode, exception.getMessage());
    }
}
