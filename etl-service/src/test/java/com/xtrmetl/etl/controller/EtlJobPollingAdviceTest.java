package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.job.EtlJobStatus;
import com.xtrmetl.etl.job.EtlJobStatusResponse;
import com.xtrmetl.etl.job.EtlJobWorkerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Defines the bounded RFC 9110 polling-advisory contract for durable job status responses.
 */
class EtlJobPollingAdviceTest {

    private static final UUID JOB_RECORD_ID = UUID.fromString(
            "cf4f083f-8c90-4f34-a8b6-b53761de44ef"
    );
    private static final Instant CREATED_AT = Instant.parse("2026-08-05T01:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-05T01:00:05Z");

    @Test
    void activeJobsAdvertiseTheRoundedWorkerPollingCadence() {
        EtlJobWorkerProperties workerProperties = enabledWorkerProperties();
        workerProperties.setFixedDelayMilliseconds(1_001L);
        EtlJobPollingAdvice advice = new EtlJobPollingAdvice(workerProperties);

        assertRetryAfter(advice, EtlJobStatus.PENDING, "2");
        assertRetryAfter(advice, EtlJobStatus.RUNNING, "2");
    }

    @Test
    void subSecondWorkerCadenceAdvertisesAtLeastOneSecond() {
        EtlJobWorkerProperties workerProperties = enabledWorkerProperties();
        workerProperties.setFixedDelayMilliseconds(1L);
        EtlJobPollingAdvice advice = new EtlJobPollingAdvice(workerProperties);

        assertRetryAfter(advice, EtlJobStatus.PENDING, "1");
    }

    @Test
    void disabledWorkerDoesNotAdvertiseAProcessingCadence() {
        EtlJobPollingAdvice advice = new EtlJobPollingAdvice(new EtlJobWorkerProperties());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "999");
        EtlJobStatusResponse responseBody = response(EtlJobStatus.PENDING);

        Object returnedBody = apply(advice, responseBody, headers);

        assertSame(responseBody, returnedBody);
        assertTrue(headers.getOrEmpty(HttpHeaders.RETRY_AFTER).isEmpty());
    }

    @Test
    void terminalJobsRemoveAnyRetryAfterSuggestion() {
        EtlJobPollingAdvice advice = new EtlJobPollingAdvice(enabledWorkerProperties());

        assertTerminalHeaderRemoved(advice, EtlJobStatus.SUCCEEDED);
        assertTerminalHeaderRemoved(advice, EtlJobStatus.FAILED);
    }

    @Test
    void unrelatedBodiesRemainUnchanged() {
        EtlJobPollingAdvice advice = new EtlJobPollingAdvice(enabledWorkerProperties());
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "7");
        String body = "unrelated-body";

        Object returnedBody = apply(advice, body, headers);

        assertSame(body, returnedBody);
        assertEquals("7", headers.getFirst(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void participatesInTheControllerResponseAdviceChain() {
        EtlJobPollingAdvice advice = new EtlJobPollingAdvice(enabledWorkerProperties());

        assertTrue(advice.supports(
                mock(MethodParameter.class),
                MappingJackson2HttpMessageConverter.class
        ));
    }

    @Test
    void rejectsMissingWorkerConfiguration() {
        assertThrows(NullPointerException.class, () -> new EtlJobPollingAdvice(null));
    }

    private static EtlJobWorkerProperties enabledWorkerProperties() {
        EtlJobWorkerProperties workerProperties = new EtlJobWorkerProperties();
        workerProperties.setEnabled(true);
        return workerProperties;
    }

    private static void assertRetryAfter(
            EtlJobPollingAdvice advice,
            EtlJobStatus jobStatus,
            String expectedSeconds
    ) {
        HttpHeaders headers = new HttpHeaders();
        EtlJobStatusResponse responseBody = response(jobStatus);

        Object returnedBody = apply(advice, responseBody, headers);

        assertSame(responseBody, returnedBody);
        assertEquals(expectedSeconds, headers.getFirst(HttpHeaders.RETRY_AFTER));
    }

    private static void assertTerminalHeaderRemoved(
            EtlJobPollingAdvice advice,
            EtlJobStatus jobStatus
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "999");
        EtlJobStatusResponse responseBody = response(jobStatus);

        Object returnedBody = apply(advice, responseBody, headers);

        assertSame(responseBody, returnedBody);
        assertTrue(headers.getOrEmpty(HttpHeaders.RETRY_AFTER).isEmpty());
    }

    private static Object apply(
            EtlJobPollingAdvice advice,
            Object body,
            HttpHeaders headers
    ) {
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        when(response.getHeaders()).thenReturn(headers);
        return advice.beforeBodyWrite(
                body,
                mock(MethodParameter.class),
                MediaType.APPLICATION_JSON,
                converterType(),
                mock(ServerHttpRequest.class),
                response
        );
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends HttpMessageConverter<?>> converterType() {
        return (Class<? extends HttpMessageConverter<?>>)
                (Class<?>) MappingJackson2HttpMessageConverter.class;
    }

    private static EtlJobStatusResponse response(EtlJobStatus jobStatus) {
        return new EtlJobStatusResponse(
                JOB_RECORD_ID,
                jobStatus,
                jobStatus == EtlJobStatus.PENDING ? 0 : 1,
                jobStatus == EtlJobStatus.FAILED ? "etl_target_failure" : null,
                CREATED_AT,
                UPDATED_AT
        );
    }
}
