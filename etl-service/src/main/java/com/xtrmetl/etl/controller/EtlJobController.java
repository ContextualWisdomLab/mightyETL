package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.job.EtlJobAcceptedResponse;
import com.xtrmetl.etl.job.EtlJobPage;
import com.xtrmetl.etl.job.EtlJobPageResponse;
import com.xtrmetl.etl.job.EtlJobService;
import com.xtrmetl.etl.job.EtlJobSnapshot;
import com.xtrmetl.etl.job.EtlJobStatusResponse;
import com.xtrmetl.etl.job.EtlJobSubmission;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.Sha256Digest;
import io.micrometer.observation.annotation.Observed;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/**
 * Exposes durable asynchronous ETL job submission, discovery, and status resources.
 *
 * <p>Submission requires authentication and an {@code Idempotency-Key}. The accepted response is
 * intentionally noncommittal under RFC 9110: it reports the durable pending state and supplies a
 * status-monitor resource through both the representation and {@code Location} header.</p>
 *
 * <p>Job discovery is owner-scoped and uses an opaque keyset cursor. A next-page link is emitted
 * under RFC 8288 only when another page exists. The service independently binds every list query to
 * the authenticated principal hash, so cursor contents never grant authority.</p>
 *
 * <p>Success and covered failure responses use {@code Cache-Control: no-store}. Malformed, absent,
 * and foreign-owned job identifiers use the same owner-safe not-found classification so the status
 * endpoint does not become a cross-principal existence oracle. Successful status responses also
 * carry a weak entity tag so an authenticated client can explicitly validate an unchanged
 * representation without authorizing shared-cache persistence.</p>
 */
@ConditionalOnBooleanProperty(
        prefix = "xtrmetl.etl.jobs",
        name = "intake-enabled",
        havingValue = true,
        matchIfMissing = false
)
@RestController
@RequestMapping("/api/etl/jobs")
public class EtlJobController {

    /** Response header indicating whether a prior durable submission was replayed. */
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private static final String DEFAULT_JOB_PAGE_LIMIT_TEXT = "50";

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
     * Lists one deterministic page of jobs in the authenticated principal namespace.
     *
     * @param cursor opaque next-page cursor, or {@code null} for the newest page
     * @param limit canonical decimal page size from 1 through 100, or {@code null} for 50
     * @param principal authenticated principal namespace
     * @return owner-scoped page with an RFC 8288 next link only when another page exists
     */
    @GetMapping
    @Observed(name = "etl.jobs.list", contextualName = "etl-job-list")
    public ResponseEntity<EtlJobPageResponse> list(
            @RequestParam(value = "cursor", required = false) @Nullable String cursor,
            @RequestParam(value = "limit", required = false) @Nullable String limit,
            @Nullable Principal principal
    ) {
        if (principal == null) {
            throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED);
        }

        final EtlJobPage page;
        try {
            page = etlJobService.listOwned(principal.getName(), cursor, limit);
        } catch (EtlRequestException | DataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EtlUnexpectedException(exception);
        }

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .cacheControl(CacheControl.noStore());
        if (page.nextCursor() != null) {
            String effectiveLimit = limit == null ? DEFAULT_JOB_PAGE_LIMIT_TEXT : limit;
            String nextTarget = UriComponentsBuilder.fromPath("/api/etl/jobs")
                    .queryParam("limit", effectiveLimit)
                    .queryParam("cursor", page.nextCursor())
                    .build()
                    .toUriString();
            responseBuilder.header(
                    HttpHeaders.LINK,
                    "<" + nextTarget + ">; rel=\"next\""
            );
        }
        return responseBuilder.body(EtlJobPageResponse.from(page));
    }

    /**
     * Returns one status resource only within the authenticated principal namespace.
     *
     * <p>The response includes a weak {@code ETag} derived from every operator-visible status
     * field. Spring MVC applies RFC 9110 weak comparison to ordinary {@code If-None-Match} entity
     * tags. The controller handles the RFC wildcard after the existing owner-safe lookup because
     * Spring Framework 6.2.19 does not treat {@code If-None-Match: *} as a match for safe methods.
     * Either matching form returns {@code 304 Not Modified} with no representation body.
     * Authentication and owner-safe lookup still occur before validation, and
     * {@code Cache-Control: no-store} remains in force.</p>
     *
     * @param jobRecordIdText opaque durable job identifier text
     * @param ifNoneMatch optional conditional request field
     * @param principal authenticated principal namespace
     * @return operator-safe status representation or an empty not-modified response
     */
    @GetMapping("/{jobRecordId}")
    @Observed(name = "etl.jobs.status", contextualName = "etl-job-status")
    public ResponseEntity<EtlJobStatusResponse> status(
            @PathVariable("jobRecordId") String jobRecordIdText,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
            @Nullable String ifNoneMatch,
            @Nullable Principal principal
    ) {
        if (principal == null) {
            throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED);
        }

        UUID jobRecordId = parseJobRecordId(jobRecordIdText);
        final EtlJobSnapshot snapshot;
        try {
            snapshot = etlJobService.findOwned(jobRecordId, principal.getName());
        } catch (EtlRequestException | DataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EtlUnexpectedException(exception);
        }
        EtlJobStatusResponse responseBody = EtlJobStatusResponse.from(snapshot);
        String entityTag = statusEntityTag(responseBody);
        if ("*".equals(Objects.toString(ifNoneMatch, "").trim())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CacheControl.noStore())
                    .eTag(entityTag)
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .eTag(entityTag)
                .body(responseBody);
    }

    /**
     * Builds an opaque weak validator from the complete status representation.
     *
     * <p>Each non-null field is marked and length-prefixed before SHA-256 hashing, while a
     * dedicated marker represents {@code null}. This prevents adjacent-value ambiguity and keeps
     * an omitted nullable JSON field distinct from an explicitly empty string. The digest prevents
     * the response header from exposing even the operator-safe values themselves. Payloads,
     * principals, idempotency keys, lease identifiers, SQL text, and exception text are not part of
     * the response model and therefore cannot enter this tag.</p>
     *
     * @param responseBody complete owner-authorized status representation
     * @return syntactically valid weak HTTP entity tag
     */
    private static String statusEntityTag(EtlJobStatusResponse responseBody) {
        EtlJobStatusResponse requiredResponse = Objects.requireNonNull(
                responseBody,
                "responseBody must not be null"
        );
        String canonicalRepresentation = canonicalField(requiredResponse.jobRecordId())
                + canonicalField(requiredResponse.jobStatus())
                + canonicalField(requiredResponse.attemptCount())
                + canonicalField(requiredResponse.failureCode())
                + canonicalField(requiredResponse.createdAt())
                + canonicalField(requiredResponse.updatedAt());
        return "W/\"" + Sha256Digest.digest(canonicalRepresentation) + "\"";
    }

    /**
     * Encodes one possibly-null value without delimiter or null/empty ambiguity.
     *
     * @param value representation field value
     * @return a null marker or a value marker followed by length-prefixed canonical text
     */
    private static String canonicalField(@Nullable Object value) {
        if (value == null) {
            return "N;";
        }
        String canonicalValue = value.toString();
        return "V" + canonicalValue.length() + ":" + canonicalValue;
    }

    private static UUID parseJobRecordId(String jobRecordIdText) {
        try {
            return UUID.fromString(Objects.requireNonNull(
                    jobRecordIdText,
                    "jobRecordIdText must not be null"
            ));
        } catch (IllegalArgumentException exception) {
            throw new EtlRequestException(EtlRequestError.JOB_NOT_FOUND, exception);
        }
    }

    private static String statusUrl(UUID jobRecordId) {
        return "/api/etl/jobs/" + Objects.requireNonNull(
                jobRecordId,
                "jobRecordId must not be null"
        );
    }
}
