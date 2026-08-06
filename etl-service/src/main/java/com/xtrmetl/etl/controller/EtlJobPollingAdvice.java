package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.job.EtlJobStatusResponse;
import com.xtrmetl.etl.job.EtlJobWorkerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Objects;

/**
 * Adds a bounded RFC 9110 polling advisory to active durable-job status responses.
 *
 * <p>The advice is scoped to {@link EtlJobController}. When the durable worker is enabled, it
 * derives a positive whole-second {@code Retry-After} value from the validated fixed delay and
 * rounds any fractional second upward. Pending and running jobs advertise that cadence; succeeded,
 * failed, and cancelled jobs remove the header because their state is terminal. A disabled worker
 * also removes the header so maintenance-mode intake does not imply that execution is progressing.</p>
 *
 * <p>The header is only client guidance. PostgreSQL lease fencing, principal-scoped authorization,
 * lifecycle validation, rate limiting, and client-side backoff remain independent correctness and
 * operational boundaries.</p>
 */
@ConditionalOnBooleanProperty(
        prefix = "xtrmetl.etl.jobs",
        name = "intake-enabled",
        havingValue = true,
        matchIfMissing = false
)
@ControllerAdvice(assignableTypes = EtlJobController.class)
public class EtlJobPollingAdvice implements ResponseBodyAdvice<Object> {

    private static final long MILLISECONDS_PER_SECOND = 1_000L;

    private final boolean workerEnabled;
    private final String retryAfterSeconds;

    /**
     * Creates polling advice aligned to the configured durable-worker state and cadence.
     *
     * <p>{@link EtlJobWorkerProperties} validates the fixed delay as one millisecond through one
     * day. Converting with upward rounding therefore always yields a positive RFC 9110
     * {@code delay-seconds} value from one through 86,400 when the worker is enabled. The computed
     * value remains dormant while the worker is disabled.</p>
     *
     * @param workerProperties validated durable-worker scheduling configuration
     * @throws NullPointerException when the configuration is {@code null}
     */
    public EtlJobPollingAdvice(EtlJobWorkerProperties workerProperties) {
        EtlJobWorkerProperties requiredProperties = Objects.requireNonNull(
                workerProperties,
                "workerProperties must not be null"
        );
        this.workerEnabled = requiredProperties.isEnabled();
        long fixedDelayMilliseconds = requiredProperties.getFixedDelayMilliseconds();
        long roundedSeconds = (fixedDelayMilliseconds + MILLISECONDS_PER_SECOND - 1L)
                / MILLISECONDS_PER_SECOND;
        this.retryAfterSeconds = Long.toString(roundedSeconds);
    }

    /**
     * Participates in response handling for the scoped durable-job controller.
     *
     * <p>The controller advice annotation already limits invocation to {@link EtlJobController}.
     * The body-type decision remains in {@link #beforeBodyWrite(Object, MethodParameter, MediaType,
     * Class, ServerHttpRequest, ServerHttpResponse)} so problem, submission, list, and status
     * responses follow one deterministic path.</p>
     *
     * @param returnType controller method return metadata
     * @param converterType selected HTTP message converter type
     * @return always {@code true} within the controller-scoped advice chain
     */
    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return true;
    }

    /**
     * Adds or removes {@code Retry-After} according to worker availability and job lifecycle state.
     *
     * <p>Only {@link EtlJobStatusResponse} bodies are modified. Active states set the configured
     * rounded delay only when a worker is enabled. Disabled-worker and terminal responses
     * explicitly remove the field, protecting maintenance-mode intake, embedded adapters, and
     * future response builders from retaining a misleading polling suggestion. All other bodies and
     * headers pass through unchanged.</p>
     *
     * @param body response body selected by the controller or exception handler
     * @param returnType controller method return metadata
     * @param selectedContentType selected response media type
     * @param selectedConverterType selected HTTP message converter type
     * @param request current server request
     * @param response mutable server response
     * @return the original response body without replacement
     */
    @Override
    @Nullable
    public Object beforeBodyWrite(
            @Nullable Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body instanceof EtlJobStatusResponse statusResponse) {
            switch (statusResponse.jobStatus()) {
                case PENDING, RUNNING -> {
                    if (workerEnabled) {
                        response.getHeaders().set(
                                HttpHeaders.RETRY_AFTER,
                                retryAfterSeconds
                        );
                    } else {
                        response.getHeaders().remove(HttpHeaders.RETRY_AFTER);
                    }
                }
                case SUCCEEDED, FAILED, CANCELLED -> response.getHeaders().remove(
                        HttpHeaders.RETRY_AFTER
                );
            }
        }
        return body;
    }
}
