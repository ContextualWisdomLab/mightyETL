package com.xtrmetl.etl.job;

import com.xtrmetl.etl.controller.EtlApiProblemHandler;
import com.xtrmetl.etl.controller.EtlJobController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Defines the authenticated HTTP contract for owner-scoped durable job pagination.
 */
class EtlJobPaginationControllerTest {

    private static final String JOBS_PATH = "/api/etl/jobs";
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
    void listsOwnedJobsAndAdvertisesOnlyTheExistingNextPage() throws Exception {
        EtlJobSnapshot snapshot = snapshot();
        when(etlJobService.listOwned("tenant_alpha", "current_cursor", "2"))
                .thenReturn(new EtlJobPage(List.of(snapshot), "next_cursor"));

        mockMvc.perform(get(JOBS_PATH)
                        .principal(PRINCIPAL)
                        .queryParam("cursor", "current_cursor")
                        .queryParam("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string(
                        "Link",
                        "</api/etl/jobs?limit=2&cursor=next_cursor>; rel=\"next\""
                ))
                .andExpect(jsonPath("$.jobs.length()").value(1))
                .andExpect(jsonPath("$.jobs[0].jobRecordId").value(
                        snapshot.jobRecordId().toString()
                ))
                .andExpect(jsonPath("$.jobs[0].jobStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.jobs[0].attemptCount").value(2))
                .andExpect(jsonPath("$.jobs[0].failureCode").doesNotExist())
                .andExpect(jsonPath("$.jobs[0].createdAt").value("2026-08-05T01:00:00Z"))
                .andExpect(jsonPath("$.jobs[0].updatedAt").value("2026-08-05T01:00:05Z"))
                .andExpect(jsonPath("$.jobs[0].requestPayload").doesNotExist())
                .andExpect(jsonPath("$.jobs[0].principalScopeHash").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").value("next_cursor"));

        verify(etlJobService).listOwned("tenant_alpha", "current_cursor", "2");
    }

    @Test
    void omitsTheNextLinkAndCursorForTheTerminalPage() throws Exception {
        when(etlJobService.listOwned("tenant_alpha", null, null))
                .thenReturn(new EtlJobPage(List.of(), null));

        mockMvc.perform(get(JOBS_PATH).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Link"))
                .andExpect(jsonPath("$.jobs.length()").value(0))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());

        verify(etlJobService).listOwned("tenant_alpha", null, null);
    }

    @Test
    void requiresAuthenticationBeforeListingJobs() throws Exception {
        mockMvc.perform(get(JOBS_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value(
                        "etl_idempotency_principal_required"
                ));

        verifyNoInteractions(etlJobService);
    }

    private static EtlJobSnapshot snapshot() {
        return new EtlJobSnapshot(
                UUID.fromString("cf4f083f-8c90-4f34-a8b6-b53761de44ef"),
                EtlJobStatus.SUCCEEDED,
                2,
                null,
                Instant.parse("2026-08-05T01:00:00Z"),
                Instant.parse("2026-08-05T01:00:05Z")
        );
    }
}
