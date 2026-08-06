package com.xtrmetl.etl.job;

import com.xtrmetl.etl.controller.EtlApiProblemHandler;
import com.xtrmetl.etl.controller.EtlJobController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that a committed cancellation invalidates every active status representation validator.
 */
class EtlJobCancellationConditionalStatusTest {

    private static final UUID JOB_RECORD_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final Principal PRINCIPAL = () -> "tenant_alpha";
    private static final Instant CREATED_AT = Instant.parse("2026-08-05T01:00:00Z");

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
    void cancelledStatusInvalidatesThePriorActiveEntityTag() throws Exception {
        when(etlJobService.findOwned(JOB_RECORD_ID, "tenant_alpha"))
                .thenReturn(
                        snapshot(EtlJobStatus.RUNNING, Instant.parse("2026-08-05T01:00:05Z")),
                        snapshot(EtlJobStatus.CANCELLED, Instant.parse("2026-08-05T01:00:06Z"))
                );

        String activeEntityTag = mockMvc.perform(statusRequest())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(HttpHeaders.ETAG);
        assertNotNull(activeEntityTag);

        mockMvc.perform(statusRequest().header(HttpHeaders.IF_NONE_MATCH, activeEntityTag))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, not(equalTo(activeEntityTag))))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.jobStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.failureCode").doesNotExist());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            statusRequest() {
        return get("/api/etl/jobs/" + JOB_RECORD_ID).principal(PRINCIPAL);
    }

    private static EtlJobSnapshot snapshot(EtlJobStatus status, Instant updatedAt) {
        return new EtlJobSnapshot(
                JOB_RECORD_ID,
                status,
                1,
                null,
                CREATED_AT,
                updatedAt
        );
    }
}
