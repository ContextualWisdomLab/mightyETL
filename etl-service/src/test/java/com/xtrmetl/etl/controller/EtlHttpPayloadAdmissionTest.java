package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.connector.TargetConnectorDispatcher;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlService;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
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
 * Proves synchronous ETL request bytes are bounded before MVC invokes the controller service.
 */
@WebMvcTest(EtlController.class)
@EnableConfigurationProperties(EtlBatchProperties.class)
@Import(EtlHttpPayloadAdmissionTest.UnknownLengthRequestConfig.class)
class EtlHttpPayloadAdmissionTest {

    private static final String PROCESS_PATH = "/api/etl/process";
    private static final String OVERSIZED_MARKER = "oversized-private-marker";
    private static final String UNKNOWN_LENGTH_HEADER = "X-Test-Unknown-Content-Length";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EtlService etlService;

    @MockBean
    private TargetConnectorDispatcher connectorDispatcher;

    @Test
    @WithMockUser
    void rejectsKnownOversizedBodyBeforeControllerInvocation() throws Exception {
        String request = oversizedJsonRequest();
        when(etlService.processData(anyString())).thenReturn("unexpected controller invocation");

        mockMvc.perform(post(PROCESS_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.errorCode").value("etl_payload_too_large"))
                .andExpect(jsonPath("$.instance").value(PROCESS_PATH))
                .andExpect(content().string(not(containsString(OVERSIZED_MARKER))));

        verifyNoInteractions(etlService);
    }

    @Test
    @WithMockUser
    void rejectsUnknownLengthOversizedBodyBeforeControllerInvocation() throws Exception {
        String request = oversizedJsonRequest();
        when(etlService.processData(anyString())).thenReturn("unexpected controller invocation");

        mockMvc.perform(post(PROCESS_PATH)
                        .with(csrf())
                        .header(UNKNOWN_LENGTH_HEADER, "true")
                        .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.errorCode").value("etl_payload_too_large"));

        verifyNoInteractions(etlService);
    }

    @Test
    @WithMockUser
    void acceptsKnownLengthBodyAtExactByteLimit() throws Exception {
        String request = sizedJsonRequest(EtlBatchProperties.DEFAULT_MAX_PAYLOAD_BYTES);
        when(etlService.processData(request)).thenReturn("processed");

        mockMvc.perform(post(PROCESS_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(content().string("processed"));

        verify(etlService).processData(request);
    }

    @Test
    @WithMockUser
    void acceptsUnknownLengthBodyImmediatelyBelowByteLimit() throws Exception {
        String request = sizedJsonRequest(EtlBatchProperties.DEFAULT_MAX_PAYLOAD_BYTES - 1);
        when(etlService.processData(request)).thenReturn("processed");

        mockMvc.perform(post(PROCESS_PATH)
                        .with(csrf())
                        .header(UNKNOWN_LENGTH_HEADER, "true")
                        .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(content().string("processed"));

        verify(etlService).processData(request);
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
