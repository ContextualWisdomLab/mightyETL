package com.xtrmetl.etl.controller;

import com.xtrmetl.etl.connector.TargetConnectorDispatcher;
import com.xtrmetl.etl.service.EtlIdempotencyResult;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.EtlService;
import io.micrometer.observation.annotation.Observed;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Exposes the synchronous ETL processing and target-connector discovery endpoints.
 *
 * <p>The controller owns successful response shaping only. {@link EtlApiProblemHandler} maps
 * request, target, and unexpected failures to the stable RFC 9457 API contract.</p>
 */
@RestController
@RequestMapping("/api/etl")
public class EtlController {

    /** Response header that tells a keyed client whether a prior success was replayed. */
    public static final String IDEMPOTENCY_REPLAYED_HEADER = "Idempotency-Replayed";

    private final EtlService etlService;
    private final TargetConnectorDispatcher connectorDispatcher;

    /**
     * Creates the ETL HTTP adapter.
     *
     * @param etlService bounded transactional ETL service
     * @param connectorDispatcher target connector catalog and lifecycle dispatcher
     */
    public EtlController(EtlService etlService, TargetConnectorDispatcher connectorDispatcher) {
        this.etlService = Objects.requireNonNull(etlService, "etlService must not be null");
        this.connectorDispatcher = Objects.requireNonNull(
                connectorDispatcher,
                "connectorDispatcher must not be null"
        );
    }

    /**
     * Processes one bounded JSON-array request and returns record results in input order.
     *
     * <p>Supplying {@code Idempotency-Key} opts into durable principal-scoped replay protection.
     * The authenticated principal is required for keyed requests so one caller cannot probe or
     * replay another caller's ledger namespace. Unkeyed requests retain the original synchronous
     * behavior.</p>
     *
     * <p>The mapping does not constrain response negotiation to the successful representation.
     * This allows callers that accept only {@code application/problem+json} to receive typed
     * failure responses before a successful text body is selected. Typed request and data-access
     * failures retain their dedicated handlers; only an unexpected runtime failure raised during
     * the service invocation is wrapped as an ETL internal failure.</p>
     *
     * @param jsonInput UTF-8 JSON array request body
     * @param idempotencyKey optional bounded client-generated retry key
     * @param principal authenticated caller namespace for keyed requests
     * @return existing newline-delimited plain-text success response, plus replay metadata when keyed
     */
    @PostMapping("/process")
    @Observed(name = "etl.process", contextualName = "etl-processing")
    public ResponseEntity<String> processData(
            @RequestBody String jsonInput,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Nullable String idempotencyKey,
            @Nullable Principal principal
    ) {
        final String result;
        final boolean replayed;
        try {
            if (idempotencyKey == null) {
                result = etlService.processData(jsonInput);
                replayed = false;
            } else {
                if (principal == null) {
                    throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED);
                }
                EtlIdempotencyResult idempotencyResult = etlService.processDataIdempotently(
                        jsonInput,
                        idempotencyKey,
                        principal.getName()
                );
                result = idempotencyResult.responseBody();
                replayed = idempotencyResult.replayed();
            }
        } catch (EtlRequestException | DataAccessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new EtlUnexpectedException(exception);
        }

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN);
        if (idempotencyKey != null) {
            response.header(IDEMPOTENCY_REPLAYED_HEADER, Boolean.toString(replayed));
        }
        return response.body(result);
    }

    /**
     * Processes an unkeyed request for direct callers that use the historical Java API.
     *
     * <p>This overload is not an additional HTTP mapping. It preserves source compatibility for
     * direct controller tests and embedded integrations while delegating to the mapped method.</p>
     *
     * @param jsonInput UTF-8 JSON array request body
     * @return existing newline-delimited plain-text success response
     */
    public ResponseEntity<String> processData(String jsonInput) {
        return processData(jsonInput, null, null);
    }

    /**
     * Returns target connector capabilities and runtime state without configuration secrets.
     *
     * <p>Primary load path remains PostgreSQL through {@code /process}; external warehouse and BI
     * connectors retain their documented support status.</p>
     *
     * @return operator-safe target connector catalog
     */
    @GetMapping("/connectors")
    @Observed(name = "etl.connectors", contextualName = "etl-connectors")
    public ResponseEntity<Map<String, Object>> connectors() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product", "mightyETL");
        body.put("primaryLoadPath", "postgresql");
        body.put("connectors", connectorDispatcher.catalog());
        body.put("docs", "docs/connectors/");
        return ResponseEntity.ok(body);
    }
}
