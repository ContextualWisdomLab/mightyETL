package com.xtrmetl.etl.job;

import com.xtrmetl.etl.controller.EtlApiProblemHandler;
import com.xtrmetl.etl.controller.EtlJobReplayController;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Defines success, replay, authentication, identifier, and failure behavior for replay admission.
 */
class EtlJobReplayControllerTest {

    private static final UUID SOURCE_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final UUID NEW_JOB_ID = UUID.fromString(
            "86e4d474-dabf-4d6a-9de4-4e8230589363"
    );
    private static final String PAYLOAD = "[{\"id\":\"record_alpha\"}]";
    private static final String REPLAY_KEY = "\"1e05bdca-447c-4ad3-882c-e33963ce517c\"";
    private static final Principal PRINCIPAL = () -> "tenant_alpha";

    private EtlJobReplayService replayService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        replayService = mock(EtlJobReplayService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EtlJobReplayController(replayService))
                .setControllerAdvice(new EtlApiProblemHandler())
                .build();
    }

    @Test
    void acceptsANewReplayAndReturnsItsStatusMonitor() throws Exception {
        when(replayService.replayOwned(SOURCE_ID, PAYLOAD, REPLAY_KEY, "tenant_alpha"))
                .thenReturn(new EtlJobReplay(NEW_JOB_ID, EtlJobStatus.PENDING, false));

        mockMvc.perform(request(SOURCE_ID).principal(PRINCIPAL))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Location", "/api/etl/jobs/" + NEW_JOB_ID))
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.jobRecordId").value(NEW_JOB_ID.toString()))
                .andExpect(jsonPath("$.jobStatus").value("PENDING"))
                .andExpect(jsonPath("$.statusUrl").value("/api/etl/jobs/" + NEW_JOB_ID));

        verify(replayService).replayOwned(
                SOURCE_ID,
                PAYLOAD,
                REPLAY_KEY,
                "tenant_alpha"
        );
    }

    @Test
    void returnsTheCurrentStatusWhenAReplayRequestIsRepeatedLater() throws Exception {
        when(replayService.replayOwned(SOURCE_ID, PAYLOAD, REPLAY_KEY, "tenant_alpha"))
                .thenReturn(new EtlJobReplay(NEW_JOB_ID, EtlJobStatus.SUCCEEDED, true));

        mockMvc.perform(request(SOURCE_ID).principal(PRINCIPAL))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.jobStatus").value("SUCCEEDED"));
    }

    @Test
    void rejectsAuthenticationKeyAndMalformedIdentifierBeforeServiceAccess() throws Exception {
        mockMvc.perform(request(SOURCE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(
                        "etl_idempotency_principal_required"
                ));
        mockMvc.perform(post("/api/etl/jobs/" + SOURCE_ID + "/replays")
                        .principal(PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("etl_job_replay_key_required"));
        mockMvc.perform(post("/api/etl/jobs/not-a-uuid/replays")
                        .principal(PRINCIPAL)
                        .header("Idempotency-Key", REPLAY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("etl_job_not_found"));

        verifyNoInteractions(replayService);
    }

    @Test
    void preservesTypedFailuresAndSanitizesDatabaseAndUnexpectedFailures() throws Exception {
        when(replayService.replayOwned(any(UUID.class), anyString(), anyString(), anyString()))
                .thenThrow(new EtlRequestException(EtlRequestError.JOB_REPLAY_PAYLOAD_MISMATCH))
                .thenThrow(new DataAccessException("secret database detail") { })
                .thenThrow(new IllegalStateException("secret runtime detail"));

        mockMvc.perform(request(SOURCE_ID).principal(PRINCIPAL))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("etl_job_replay_payload_mismatch"));
        mockMvc.perform(request(SOURCE_ID).principal(PRINCIPAL))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("etl_target_failure"))
                .andExpect(jsonPath("$.detail").value(
                        "The ETL target could not process the request."
                ));
        mockMvc.perform(request(SOURCE_ID).principal(PRINCIPAL))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("etl_internal_error"))
                .andExpect(jsonPath("$.detail").value(
                        "The ETL request could not be processed."
                ));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            request(UUID sourceId) {
        return post("/api/etl/jobs/" + sourceId + "/replays")
                .header("Idempotency-Key", REPLAY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYLOAD);
    }
}
