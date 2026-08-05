package com.xtrmetl.etl.job;

import com.xtrmetl.etl.controller.EtlApiProblemHandler;
import com.xtrmetl.etl.controller.EtlJobController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Defines conditional-request behavior for owner-scoped durable-job status polling.
 */
class EtlJobConditionalStatusTest {

    private static final String JOBS_PATH = "/api/etl/jobs";
    private static final UUID JOB_RECORD_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final Principal PRINCIPAL = () -> "tenant_alpha";
    private static final Instant CREATED_AT = Instant.parse("2026-08-05T01:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-05T01:00:05Z");

    private EtlJobService etlJobService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        etlJobService = mock(EtlJobService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EtlJobController(etlJobService))
                .setControllerAdvice(new EtlApiProblemHandler())
                .build();
    }

    @Test
    void unchangedStatusUsesAWeakEntityTagForAnEmptyNotModifiedResponse() throws Exception {
        when(etlJobService.findOwned(JOB_RECORD_ID, "tenant_alpha"))
                .thenReturn(snapshot(EtlJobStatus.PENDING, 0, null, UPDATED_AT));

        MvcResult initialResult = mockMvc.perform(statusRequest())
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.ETAG, startsWith("W/\"")))
                .andReturn();
        String entityTag = initialResult.getResponse().getHeader(HttpHeaders.ETAG);
        assertNotNull(entityTag);
        assertTrue(entityTag.endsWith("\""));

        mockMvc.perform(statusRequest().header(HttpHeaders.IF_NONE_MATCH, entityTag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.ETAG, entityTag))
                .andExpect(content().string(""));

        verify(etlJobService, times(2)).findOwned(JOB_RECORD_ID, "tenant_alpha");
    }

    @Test
    void changedStatusInvalidatesThePriorEntityTag() throws Exception {
        when(etlJobService.findOwned(JOB_RECORD_ID, "tenant_alpha"))
                .thenReturn(
                        snapshot(EtlJobStatus.PENDING, 0, null, UPDATED_AT),
                        snapshot(EtlJobStatus.RUNNING, 1, null, UPDATED_AT.plusSeconds(5))
                );

        String priorEntityTag = mockMvc.perform(statusRequest())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
        assertNotNull(priorEntityTag);

        mockMvc.perform(statusRequest().header(HttpHeaders.IF_NONE_MATCH, priorEntityTag))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, not(equalTo(priorEntityTag))))
                .andExpect(jsonPath("$.jobStatus").value("RUNNING"))
                .andExpect(jsonPath("$.attemptCount").value(1));
    }

    @Test
    void changedFailureCodeInvalidatesTheTagEvenAtTheSameTimestamp() throws Exception {
        when(etlJobService.findOwned(JOB_RECORD_ID, "tenant_alpha"))
                .thenReturn(
                        snapshot(EtlJobStatus.FAILED, 3, "etl_source_failure", UPDATED_AT),
                        snapshot(EtlJobStatus.FAILED, 3, "etl_target_failure", UPDATED_AT)
                );

        String priorEntityTag = mockMvc.perform(statusRequest())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
        assertNotNull(priorEntityTag);

        mockMvc.perform(statusRequest().header(HttpHeaders.IF_NONE_MATCH, priorEntityTag))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, not(equalTo(priorEntityTag))))
                .andExpect(jsonPath("$.failureCode").value("etl_target_failure"));
    }

    @Test
    void wildcardIfNoneMatchRecognizesTheExistingOwnerScopedRepresentation() throws Exception {
        when(etlJobService.findOwned(JOB_RECORD_ID, "tenant_alpha"))
                .thenReturn(snapshot(EtlJobStatus.SUCCEEDED, 1, null, UPDATED_AT));

        mockMvc.perform(statusRequest().header(HttpHeaders.IF_NONE_MATCH, "*"))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, startsWith("W/\"")))
                .andExpect(content().string(""));
    }

    @Test
    void submissionResponsesDoNotReceiveAStatusEntityTag() throws Exception {
        String requestPayload = "[{\"id\":\"record_alpha\"}]";
        String idempotencyKey = "\"550e8400-e29b-41d4-a716-446655440000\"";
        when(etlJobService.submit(requestPayload, idempotencyKey, "tenant_alpha"))
                .thenReturn(new EtlJobSubmission(
                        JOB_RECORD_ID,
                        EtlJobStatus.PENDING,
                        false
                ));

        mockMvc.perform(post(JOBS_PATH)
                        .principal(PRINCIPAL)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload))
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            statusRequest() {
        return get(JOBS_PATH + "/" + JOB_RECORD_ID).principal(PRINCIPAL);
    }

    private static EtlJobSnapshot snapshot(
            EtlJobStatus jobStatus,
            int attemptCount,
            String failureCode,
            Instant updatedAt
    ) {
        return new EtlJobSnapshot(
                JOB_RECORD_ID,
                jobStatus,
                attemptCount,
                failureCode,
                CREATED_AT,
                updatedAt
        );
    }
}
