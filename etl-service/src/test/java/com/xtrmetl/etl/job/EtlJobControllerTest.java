package com.xtrmetl.etl.job;

import com.xtrmetl.etl.controller.EtlApiProblemHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Defines the authenticated HTTP contract for durable asynchronous ETL jobs.
 */
class EtlJobControllerTest {

    private static final String JOBS_PATH = "/api/etl/jobs";
    private static final String IDEMPOTENCY_KEY = "\"550e8400-e29b-41d4-a716-446655440000\"";
    private static final Principal PRINCIPAL = () -> "tenant_alpha";

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
    void acceptsANewDurableJobAndReturnsItsStatusMonitor() throws Exception {
        UUID jobRecordId = UUID.fromString("cf4f083f-8c90-4f34-a8b6-b53761de44ef");
        String request = "[{\"id\":\"record_alpha\"}]";
        when(etlJobService.submit(request, IDEMPOTENCY_KEY, "tenant_alpha"))
                .thenReturn(new EtlJobSubmission(jobRecordId, EtlJobStatus.PENDING, false));

        mockMvc.perform(post(JOBS_PATH)
                        .principal(PRINCIPAL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(
                        "Location",
                        "/api/etl/jobs/cf4f083f-8c90-4f34-a8b6-b53761de44ef"
                ))
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.jobRecordId").value(jobRecordId.toString()))
                .andExpect(jsonPath("$.jobStatus").value("PENDING"))
                .andExpect(jsonPath("$.statusUrl").value(
                        "/api/etl/jobs/cf4f083f-8c90-4f34-a8b6-b53761de44ef"
                ));

        verify(etlJobService).submit(request, IDEMPOTENCY_KEY, "tenant_alpha");
    }

    @Test
    void replaysTheSameSubmissionWithTheSameJobIdentity() throws Exception {
        UUID jobRecordId = UUID.fromString("cf4f083f-8c90-4f34-a8b6-b53761de44ef");
        String request = "[{\"id\":\"record_alpha\"}]";
        when(etlJobService.submit(request, IDEMPOTENCY_KEY, "tenant_alpha"))
                .thenReturn(new EtlJobSubmission(jobRecordId, EtlJobStatus.PENDING, true));

        mockMvc.perform(post(JOBS_PATH)
                        .principal(PRINCIPAL)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.jobRecordId").value(jobRecordId.toString()));
    }

    @Test
    void requiresAuthenticationAndAnIdempotencyKeyBeforeServiceAccess() throws Exception {
        mockMvc.perform(post(JOBS_PATH)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("etl_idempotency_principal_required"));

        mockMvc.perform(post(JOBS_PATH)
                        .principal(PRINCIPAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("etl_invalid_idempotency_key"));

        verifyNoInteractions(etlJobService);
    }

    @Test
    void returnsAnOwnerScopedJobSnapshotWithoutPayloadOrInternalHashes() throws Exception {
        UUID jobRecordId = UUID.fromString("cf4f083f-8c90-4f34-a8b6-b53761de44ef");
        Instant createdAt = Instant.parse("2026-08-04T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-08-04T10:00:01Z");
        when(etlJobService.findOwned(jobRecordId, "tenant_alpha"))
                .thenReturn(new EtlJobSnapshot(
                        jobRecordId,
                        EtlJobStatus.PENDING,
                        0,
                        null,
                        createdAt,
                        updatedAt
                ));

        mockMvc.perform(get(JOBS_PATH + "/" + jobRecordId).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobRecordId").value(jobRecordId.toString()))
                .andExpect(jsonPath("$.jobStatus").value("PENDING"))
                .andExpect(jsonPath("$.attemptCount").value(0))
                .andExpect(jsonPath("$.failureCode").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value("2026-08-04T10:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-08-04T10:00:01Z"))
                .andExpect(jsonPath("$.requestPayload").doesNotExist())
                .andExpect(jsonPath("$.principalScopeHash").doesNotExist())
                .andExpect(jsonPath("$.submissionKeyHash").doesNotExist());

        verify(etlJobService).findOwned(jobRecordId, "tenant_alpha");
    }

    @Test
    void requiresAuthenticationForJobStatus() throws Exception {
        UUID jobRecordId = UUID.fromString("cf4f083f-8c90-4f34-a8b6-b53761de44ef");

        mockMvc.perform(get(JOBS_PATH + "/" + jobRecordId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("etl_idempotency_principal_required"));

        verifyNoInteractions(etlJobService);
    }
}
