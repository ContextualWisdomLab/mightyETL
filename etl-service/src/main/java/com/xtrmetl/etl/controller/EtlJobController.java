package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.job.EtlJobService;
import com.xtrmetl.etl.job.EtlJobView;
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
 * Exposes owner-scoped durable asynchronous ETL job submission and status resources.
 */
@RestController
@RequestMapping("/api/etl/jobs")
public class EtlJobController {

    private final EtlJobService jobService;

    /**
     * Creates the durable ETL job HTTP adapter.
     *
     * @param jobService validated principal-scoped job application service
     */
    public EtlJobController(EtlJobService jobService) {
        this.jobService = Objects.requireNonNull(jobService, "jobService must not be null");
    }

    /**
     * Accepts one validated durable job and returns its status monitor.
     *
     * @param requestPayload decoded JSON request text
     * @param idempotencyKey required bounded semantic submission key
     * @param principal authenticated owner namespace
     * @return 202 representation and relative status-monitor location
     */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Observed(name = "etl.jobs.submit", contextualName = "etl-job-submit")
    public ResponseEntity<EtlJobView> submit(
            @RequestBody String requestPayload,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Nullable String idempotencyKey,
            @Nullable Principal principal
    ) {
        try {
            if (principal == null) {
                throw new EtlRequestException(EtlRequestError.JOB_PRINCIPAL_REQUIRED);
            }
            EtlJobView view = jobService.submit(
                    requestPayload,
                    idempotencyKey,
                    principal.getName()
            );
            return ResponseEntity.accepted()
                    .location(URI.create(view.statusUrl()))
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(view);
        } catch (EtlRequestException | DataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EtlUnexpectedException(exception);
        }
    }

    /**
     * Returns the current owner-safe representation for one durable job.
     *
     * <p>Malformed, missing, and foreign-owned identifiers share one 404 problem classification.</p>
     *
     * @param jobRecordId opaque UUID text
     * @param principal authenticated owner namespace
     * @return current no-store job representation
     */
    @GetMapping(value = "/{jobRecordId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Observed(name = "etl.jobs.get", contextualName = "etl-job-get")
    public ResponseEntity<EtlJobView> get(
            @PathVariable String jobRecordId,
            @Nullable Principal principal
    ) {
        try {
            if (principal == null) {
                throw new EtlRequestException(EtlRequestError.JOB_PRINCIPAL_REQUIRED);
            }
            final UUID parsedJobId;
            try {
                parsedJobId = UUID.fromString(jobRecordId);
            } catch (IllegalArgumentException exception) {
                throw new EtlRequestException(EtlRequestError.JOB_NOT_FOUND, exception);
            }
            EtlJobView view = jobService.get(parsedJobId, principal.getName());
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(view);
        } catch (EtlRequestException | DataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EtlUnexpectedException(exception);
        }
    }
}
