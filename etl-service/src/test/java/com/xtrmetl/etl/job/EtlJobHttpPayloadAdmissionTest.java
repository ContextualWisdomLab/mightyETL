package com.xtrmetl.etl.job;

import com.xtrmetl.etl.controller.EtlJobController;
import com.xtrmetl.etl.service.EtlBatchProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves durable job-intake request bytes are bounded before MVC invokes the job service.
 */
@WebMvcTest(EtlJobController.class)
@EnableConfigurationProperties(EtlBatchProperties.class)
@TestPropertySource(properties = "xtrmetl.etl.jobs.intake-enabled=true")
@Import(EtlJobHttpPayloadAdmissionTest.UnknownLengthRequestConfig.class)
class EtlJobHttpPayloadAdmissionTest {

    private static final String JOBS_PATH = "/api/etl/jobs";
    private static final String IDEMPOTENCY_KEY = "\"550e8400-e29b-41d4-a716-446655440000\"";
    private static final String OVERSIZED_MARKER = "oversized-private-marker";
    private static final String UNKNOWN_LENGTH_HEADER = "X-Test-Unknown-Content-Length";
    private static final UUID JOB_RECORD_ID = UUID.fromString("cf4f083f-8c90-4f34-a8b6-b53761de44ef");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EtlJobService etlJobService;

    @Test
    @WithMockUser
    void rejectsKnownOversizedBodyBeforeControllerInvocation() throws Exception {
        String request = oversizedJsonRequest();
        when(etlJobService.submit(request, IDEMPOTENCY_KEY, "user"))
                .thenReturn(new EtlJobSubmission(JOB_RECORD_ID, EtlJobStatus.PENDING, false));

        mockMvc.perform(post(JOBS_PATH)
                        .with(csrf())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.errorCode").value("etl_payload_too_large"))
                .andExpect(jsonPath("$.instance").value(JOBS_PATH))
                .andExpect(content().string(not(containsString(OVERSIZED_MARKER))));

        verifyNoInteractions(etlJobService);
    }

    @Test
    @WithMockUser
    void rejectsUnknownLengthOversizedBodyBeforeControllerInvocation() throws Exception {
        String request = oversizedJsonRequest();
        when(etlJobService.submit(request, IDEMPOTENCY_KEY, "user"))
                .thenReturn(new EtlJobSubmission(JOB_RECORD_ID, EtlJobStatus.PENDING, false));

        mockMvc.perform(post(JOBS_PATH)
                        .with(csrf())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .header(UNKNOWN_LENGTH_HEADER, "true")
                        .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("etl_payload_too_large"));

        verifyNoInteractions(etlJobService);
    }

    @Test
    @WithMockUser
    void acceptsKnownLengthBodyAtExactByteLimit() throws Exception {
        String request = sizedJsonRequest(EtlBatchProperties.DEFAULT_MAX_PAYLOAD_BYTES);
        when(etlJobService.submit(request, IDEMPOTENCY_KEY, "user"))
                .thenReturn(new EtlJobSubmission(JOB_RECORD_ID, EtlJobStatus.PENDING, false));

        mockMvc.perform(post(JOBS_PATH)
                        .with(csrf())
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobRecordId").value(JOB_RECORD_ID.toString()));

        verify(etlJobService).submit(request, IDEMPOTENCY_KEY, "user");
    }

    private static String oversizedJsonRequest() {
        return "[{\"id\":\"" + OVERSIZED_MARKER + ""
                + "x".repeat(EtlBatchProperties.DEFAULT_MAX_PAYLOAD_BYTES)
                + "\"}]";
    }

    private static String sizedJsonRequest(int totalBytes) {
        String prefix = "[{\"id\":\"";
        String suffix = "\"}]";
        int fillerLength = totalBytes - prefix.length() - suffix.length();
        return prefix + "x".repeat(fillerLength) + suffix;
    }

    /**
     * Test-only transport shim that models chunked input whose byte length is not known up front.
     */
    @TestConfiguration
    static class UnknownLengthRequestConfig {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        Filter unknownLengthRequestFilter() {
            return (request, response, chain) -> {
                if (request instanceof HttpServletRequest httpRequest
                        && httpRequest.getHeader(UNKNOWN_LENGTH_HEADER) != null) {
                    chain.doFilter(new HttpServletRequestWrapper(httpRequest) {
                        @Override
                        public int getContentLength() {
                            return -1;
                        }

                        @Override
                        public long getContentLengthLong() {
                            return -1L;
                        }
                    }, response);
                    return;
                }
                chain.doFilter(request, response);
            };
        }
    }
}
