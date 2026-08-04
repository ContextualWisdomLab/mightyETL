package com.xtrmetl.etl.job;

import com.xtrmetl.etl.controller.EtlApiProblemHandler;
import com.xtrmetl.etl.controller.EtlJobController;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers typed data-access, malformed resource identifiers, and unexpected failures at both
 * durable job controller boundaries.
 */
class EtlJobControllerFailureTest {

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
    void preservesTypedSubmissionFailures() throws Exception {
        when(etlJobService.submit(anyString(), anyString(), anyString()))
                .thenThrow(new EtlRequestException(EtlRequestError.JOB_SUBMISSION_KEY_REUSED));

        performSubmission()
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("etl_job_submission_key_reused"));
    }

    @Test
    void mapsSubmissionDatabaseFailuresWithoutLeakingMessages() throws Exception {
        DataAccessException databaseFailure = new DataAccessException("secret database detail") { };
        when(etlJobService.submit(anyString(), anyString(), anyString()))
                .thenThrow(databaseFailure);

        performSubmission()
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("etl_target_failure"))
                .andExpect(jsonPath("$.detail").value("The ETL target could not process the request."));
    }

    @Test
    void mapsSubmissionUnexpectedFailuresWithoutLeakingMessages() throws Exception {
        when(etlJobService.submit(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("secret runtime detail"));

        performSubmission()
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("etl_internal_error"))
                .andExpect(jsonPath("$.detail").value("The ETL request could not be processed."));
    }

    @Test
    void preservesTypedStatusFailures() throws Exception {
        when(etlJobService.findOwned(any(UUID.class), anyString()))
                .thenThrow(new EtlRequestException(EtlRequestError.JOB_NOT_FOUND));

        performStatus()
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("etl_job_not_found"));
    }

    @Test
    void treatsMalformedJobIdentifiersAsTheSameOwnerSafeNotFoundProblem() throws Exception {
        mockMvc.perform(get(JOBS_PATH + "/not-a-uuid").principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("etl_job_not_found"));

        verifyNoInteractions(etlJobService);
    }

    @Test
    void mapsStatusDatabaseFailuresWithoutLeakingMessages() throws Exception {
        DataAccessException databaseFailure = new DataAccessException("secret database detail") { };
        when(etlJobService.findOwned(any(UUID.class), anyString()))
                .thenThrow(databaseFailure);

        performStatus()
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("etl_target_failure"));
    }

    @Test
    void mapsStatusUnexpectedFailuresWithoutLeakingMessages() throws Exception {
        when(etlJobService.findOwned(any(UUID.class), anyString()))
                .thenThrow(new IllegalArgumentException("secret runtime detail"));

        performStatus()
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("etl_internal_error"));
    }

    private org.springframework.test.web.servlet.ResultActions performSubmission() throws Exception {
        return mockMvc.perform(post(JOBS_PATH)
                .principal(PRINCIPAL)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"id\":\"record_alpha\"}]"));
    }

    private org.springframework.test.web.servlet.ResultActions performStatus() throws Exception {
        return mockMvc.perform(get(JOBS_PATH + "/" + UUID.randomUUID()).principal(PRINCIPAL));
    }
}
