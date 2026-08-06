package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.job.EtlJobAcceptedResponse;
import com.xtrmetl.etl.job.EtlJobReplay;
import com.xtrmetl.etl.job.EtlJobReplayService;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import io.micrometer.observation.annotation.Observed;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
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
 * Exposes immutable-lineage replay admission for owner-scoped terminal durable ETL jobs.
 *
 * <p>The source is never resurrected. A replay request supplies the complete candidate payload,
 * which the service verifies against the source digest before creating one new ordinary pending
 * job. The response is RFC 9110 noncommittal acceptance of that new resource, not evidence that its
 * ETL effects have completed.</p>
 */
@ConditionalOnBooleanProperty(
        prefix = "xtrmetl.etl.jobs",
        name = "intake-enabled",
        havingValue = true,
        matchIfMissing = false
)
@RestController
@RequestMapping("/api/etl/jobs")
public class EtlJobReplayController {

    private final EtlJobReplayService replayService;

    /**
     * Creates the replay HTTP adapter.
     *
     * @param replayService immutable owner-scoped replay admission service
     */
    public EtlJobReplayController(EtlJobReplayService replayService) {
        this.replayService = Objects.requireNonNull(
                replayService,
                "replayService must not be null"
        );
    }

    /**
     * Accepts one verified replay of a failed or cancelled owner-scoped source.
     *
     * @param sourceJobRecordIdText opaque terminal source identifier text
     * @param requestPayload exact bounded JSON array text to verify against the source digest
     * @param replayKey required replay idempotency key
     * @param principal authenticated principal namespace
     * @return accepted new-job identity, status monitor, and replay evidence
     */
    @PostMapping("/{sourceJobRecordId}/replays")
    @Observed(name = "etl.jobs.replay", contextualName = "etl-job-replay")
    public ResponseEntity<EtlJobAcceptedResponse> replay(
            @PathVariable("sourceJobRecordId") String sourceJobRecordIdText,
            @RequestBody String requestPayload,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Nullable String replayKey,
            @Nullable Principal principal
    ) {
        if (principal == null) {
            throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED);
        }
        if (replayKey == null) {
            throw new EtlRequestException(EtlRequestError.JOB_REPLAY_KEY_REQUIRED);
        }
        UUID sourceJobRecordId = parseJobRecordId(sourceJobRecordIdText);

        final EtlJobReplay replay;
        try {
            replay = replayService.replayOwned(
                    sourceJobRecordId,
                    requestPayload,
                    replayKey,
                    principal.getName()
            );
        } catch (EtlRequestException | DataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EtlUnexpectedException(exception);
        }

        String statusUrl = "/api/etl/jobs/" + replay.jobRecordId();
        EtlJobAcceptedResponse responseBody = new EtlJobAcceptedResponse(
                replay.jobRecordId(),
                replay.jobStatus(),
                statusUrl
        );
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .location(URI.create(statusUrl))
                .header(
                        EtlJobController.IDEMPOTENCY_REPLAYED_HEADER,
                        Boolean.toString(replay.replayed())
                )
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    private static UUID parseJobRecordId(String jobRecordIdText) {
        try {
            return UUID.fromString(Objects.requireNonNull(
                    jobRecordIdText,
                    "sourceJobRecordIdText must not be null"
            ));
        } catch (IllegalArgumentException exception) {
            throw new EtlRequestException(EtlRequestError.JOB_NOT_FOUND, exception);
        }
    }
}
