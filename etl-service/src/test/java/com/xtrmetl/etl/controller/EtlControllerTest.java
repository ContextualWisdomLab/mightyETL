package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.connector.ConnectorProperties;
import com.xtrmetl.etl.connector.TargetConnectorDispatcher;
import com.xtrmetl.etl.connector.TargetConnectorRegistry;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.EtlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Defines the successful and RFC 9457 failure contracts for the synchronous ETL API.
 */
class EtlControllerTest {

    private static final String PROCESS_PATH = "/api/etl/process";
    private static final String SYNTHETIC_SECRET = "synthetic-secret-password";

    private EtlService etlService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        etlService = mock(EtlService.class);
        TargetConnectorDispatcher dispatcher = new TargetConnectorDispatcher(
                new TargetConnectorRegistry(),
                new ConnectorProperties()
        );
        EtlController controller = new EtlController(etlService, dispatcher);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new EtlApiProblemHandler())
                .build();
    }

    @Test
    void preservesSuccessfulPlainTextResponse() throws Exception {
        String request = "[{\"id\":\"record_alpha\"}]";
        when(etlService.processData(request)).thenReturn("Processed: record_alpha");

        mockMvc.perform(post(PROCESS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Processed: record_alpha"));

        verify(etlService).processData(request);
    }

    @ParameterizedTest
    @MethodSource("requestProblemContracts")
    void mapsTypedRequestFailuresToStableProblemDetails(
            EtlRequestError error,
            int expectedStatus
    ) throws Exception {
        when(etlService.processData(anyString())).thenThrow(
                new EtlRequestException(error, new IllegalArgumentException(SYNTHETIC_SECRET))
        );

        mockMvc.perform(post(PROCESS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":\"record_alpha\"}]"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(error.type().toString()))
                .andExpect(jsonPath("$.title").value(error.title()))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").value(error.detail()))
                .andExpect(jsonPath("$.instance").value(PROCESS_PATH))
                .andExpect(jsonPath("$.errorCode").value(error.errorCode()))
                .andExpect(content().string(not(containsString(SYNTHETIC_SECRET))));
    }

    @Test
    void mapsMissingBodyToInvalidJsonProblem() throws Exception {
        mockMvc.perform(post(PROCESS_PATH).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(
                        "urn:mightyetl:problem:etl-invalid-json"
                ))
                .andExpect(jsonPath("$.errorCode").value("etl_invalid_json"))
                .andExpect(jsonPath("$.instance").value(PROCESS_PATH));
    }

    @Test
    void mapsTransientTargetFailureToRetryableServiceUnavailableProblem() throws Exception {
        when(etlService.processData(anyString())).thenThrow(
                new TransientDataAccessResourceException(SYNTHETIC_SECRET)
        );

        assertTargetProblem(
                503,
                "urn:mightyetl:problem:etl-target-unavailable",
                "ETL target unavailable",
                "The ETL target is temporarily unavailable.",
                "etl_target_unavailable"
        );
    }

    @Test
    void mapsDeterministicTargetFailureToInternalTargetProblem() throws Exception {
        when(etlService.processData(anyString())).thenThrow(
                new DataIntegrityViolationException(SYNTHETIC_SECRET)
        );

        assertTargetProblem(
                500,
                "urn:mightyetl:problem:etl-target-failure",
                "ETL target failure",
                "The ETL target could not process the request.",
                "etl_target_failure"
        );
    }

    @Test
    void mapsUnexpectedFailureToGenericInternalProblem() throws Exception {
        when(etlService.processData(anyString())).thenThrow(
                new IllegalStateException(SYNTHETIC_SECRET)
        );

        assertTargetProblem(
                500,
                "urn:mightyetl:problem:etl-internal-error",
                "ETL internal error",
                "The ETL request could not be processed.",
                "etl_internal_error"
        );
    }

    @Test
    void connectorCatalogRemainsAvailable() throws Exception {
        mockMvc.perform(get("/api/etl/connectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product").value("mightyETL"))
                .andExpect(jsonPath("$.primaryLoadPath").value("postgresql"))
                .andExpect(jsonPath("$.connectors.length()").value(3))
                .andExpect(jsonPath("$.connectors[?(@.id == 'databricks')]").exists())
                .andExpect(jsonPath("$.connectors[*].requiredConfigKeys").exists())
                .andExpect(jsonPath("$.connectors[*].integration").exists());
    }

    private void assertTargetProblem(
            int expectedStatus,
            String type,
            String title,
            String detail,
            String errorCode
    ) throws Exception {
        mockMvc.perform(post(PROCESS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"id\":\"record_alpha\"}]"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(jsonPath("$.instance").value(PROCESS_PATH))
                .andExpect(jsonPath("$.errorCode").value(errorCode))
                .andExpect(content().string(not(containsString(SYNTHETIC_SECRET))));
    }

    private static Stream<Arguments> requestProblemContracts() {
        return Stream.of(EtlRequestError.values())
                .map(error -> Arguments.of(error, error.status().value()));
    }
}
