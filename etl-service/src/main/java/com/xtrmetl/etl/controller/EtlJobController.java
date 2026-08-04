package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.job.EtlJobAcceptedResponse;
import com.xtrmetl.etl.job.EtlJobService;
import com.xtrmetl.etl.job.EtlJobSnapshot;
import com.xtrmetl.etl.job.EtlJobStatusResponse;
import com.xtrmetl.etl.job.EtlJobSubmission;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import io.micrometer.observation.annotation.Observed;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/**
 * Exposes durable asynchronous ETL job submission and owner-scoped status resources.
 *
 * <p>Submission requires authentication and an {@code Idempotency-Key}. The accepted response is
 * intentionally noncommittal under RFC 9110: it reports the durable pending state and supplies a
 * status-monitor resource through both the representation and {@code Location} header. This intake
 * slice does not claim that worker execution has started.</p>
 *
 * <p>Successful job representations use {@code Cache-Control: no-store}. They are authenticated,
 * principal-scoped operational resources and must not be retained by shared or private caches.</p>
 */
@RestController
@RequestMapping("/api/etl/jobs")
public class EtlJobController {

    /** Response header indicating whether a prior durable submission was replayed. */
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private final EtlJobService etlJobService;

    /**
     * Creates the durable job HTTP adapter.
     *
     * @param etlJobService durable principal-scoped job service
     */
    public EtlJobController(EtlJobService etlJobService) {
        this.etlJobService = Objects.requireNonNull(
                etlJobService,
                "etlJobService must not be null"
        );
    }

    /**
     * Accepts one validated bounded JSON batch as a durable asynchronous job.
     *
     * @param requestPayload exact UTF-8 JSON array text
     * @param idempotencyKey required client-generated submission key
     * @param principal authenticated principal namespace
     * @return {@code 202 Accepted} representation and status-monitor location
     */
    @PostMapping
    @Observed(name = "etl.jobs.submit", contextualName = "etl-job-submission")
    public ResponseEntity<EtlJobAcceptedResponse> submit(
            @RequestBody String requestPayload,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Nullable String idempotencyKey,
            @Nullable Principal principal
    ) {
        if (principal == null) {
            throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED);
        }
        if (idempotencyKey == null) {
            throw new EtlRequestException(EtlRequestError.INVALID_IDEMPOTENCY_KEY);
        }

        final EtlJobSubmission submission;
        try {
            submission = etlJobService.submit(
                    requestPayload,
                    idempotencyKey,
                    principal.getName()
            );
        } catch (EtlRequestException | DataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EtlUnexpectedException(exception);
        }

        String statusUrl = statusUrl(submission.jobRecordId());
        EtlJobAcceptedResponse responseBody = new EtlJobAcceptedResponse(
                submission.jobRecordId(),
                submission.jobStatus(),
                statusUrl
        );
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .location(URI.create(statusUrl))
                .header(
                        IDEMPOTENCY_REPLAYED_HEADER,
                        Boolean.toString(submission.replayed())
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    /**
     * Returns one status resource only within the authenticated principal namespace.
     *
     * @param jobRecordId opaque durable job identifier
     * @param principal authenticated principal namespace
     * @return operator-safe status representation
     */
    @GetMapping("/{jobRecordId}")
    @Observed(name = "etl.jobs.status", contextualName = "etl-job-status")
    public ResponseEntity<EtlJobStatusResponse> status(
            @PathVariable("jobRecordId") UUID jobRecordId,
            @Nullable Principal principal
    ) {
        if (principal == null) {
            throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED);
        }

        final EtlJobSnapshot snapshot;
        try {
            snapshot = etlJobService.findOwned(jobRecordId, principal.getName());
        } catch (EtlRequestException | DataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EtlUnexpectedException(exception);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EtlJobStatusResponse.from(snapshot));
    }

    private static String statusUrl(UUID jobRecordId) {
        return "/api/etl/jobs/" + Objects.requireNonNull(
                jobRecordId,
                "jobRecordId must not be null"
        );
    }
}
