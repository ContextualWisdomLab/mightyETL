package com.xtrmetl.etl.job;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestError;
import com.xtrmetl.etl.service.EtlRequestException;
import com.xtrmetl.etl.service.EtlRequestLock;
import com.xtrmetl.etl.service.PostgresEtlRequestLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admits owner-scoped durable-job replay requests on the repaired stack.
 *
 * <p>The service validates replay identity, authenticated principal scope, and JSON shape before
 * persistence work. Database admission and replay transitions are intentionally added in later
 * test-first increments so an incomplete branch cannot silently claim the full replay contract.</p>
 */
@Service
public class EtlJobReplayService {

    private static final int MAX_PRINCIPAL_SCOPE_CODE_POINTS = 512;
    private static final String IDEMPOTENCY_KEY_VALUE_EXPRESSION = "[A-Za-z0-9._:-]{16,128}";
    private static final Pattern IDEMPOTENCY_KEY_VALUE_PROFILE = Pattern.compile(
            IDEMPOTENCY_KEY_VALUE_EXPRESSION
    );
    private static final Pattern IDEMPOTENCY_KEY_STRUCTURED_FIELD_PROFILE = Pattern.compile(
            "\"(" + IDEMPOTENCY_KEY_VALUE_EXPRESSION + ")\""
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EtlBatchProperties batchProperties;
    private final EtlRequestLock requestLock;

    /**
     * Creates replay admission with the PostgreSQL transaction-lock implementation.
     *
     * @param jdbcTemplate parameterized durable job persistence
     * @param objectMapper JSON parser configuration to copy
     * @param batchProperties bounded request limits
     */
    public EtlJobReplayService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EtlBatchProperties batchProperties
    ) {
        this(
                jdbcTemplate,
                objectMapper,
                batchProperties,
                new PostgresEtlRequestLock(Objects.requireNonNull(
                        jdbcTemplate,
                        "jdbcTemplate must not be null"
                ))
        );
    }

    /**
     * Creates replay admission with an explicit transaction-lifetime request lock.
     *
     * @param jdbcTemplate parameterized durable job persistence
     * @param objectMapper JSON parser configuration to copy
     * @param batchProperties bounded request limits
     * @param requestLock transaction-lifetime replay-key lock
     */
    @Autowired
    public EtlJobReplayService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EtlBatchProperties batchProperties,
            EtlRequestLock requestLock
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        ObjectMapper sourceMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper must not be null"
        );
        this.objectMapper = sourceMapper.copy();
        this.objectMapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.batchProperties = Objects.requireNonNull(
                batchProperties,
                "batchProperties must not be null"
        );
        this.requestLock = Objects.requireNonNull(requestLock, "requestLock must not be null");
    }

    /**
     * Validates one owner-scoped replay request before any persistence work.
     *
     * <p>The database admission and replay transition are introduced in later TDD increments.
     * Until then, an otherwise valid request fails closed rather than acquiring a lock or touching
     * durable state.</p>
     *
     * @param sourceJobRecordId terminal durable job whose intent is being replayed
     * @param requestBody bounded JSON-array payload resupplied by the authenticated owner
     * @param rawReplayKey principal-scoped idempotency key for this replay request
     * @param principalName authenticated principal namespace
     * @return replay-created durable job result after persistence support is introduced
     * @throws NullPointerException when the source job identifier is absent
     * @throws EtlRequestException when key, principal, or JSON validation fails
     * @throws IllegalStateException while the persistence increment is intentionally unavailable
     */
    public EtlJobReplay replayOwned(
            UUID sourceJobRecordId,
            @Nullable String requestBody,
            @Nullable String rawReplayKey,
            @Nullable String principalName
    ) {
        Objects.requireNonNull(sourceJobRecordId, "sourceJobRecordId must not be null");
        validateReplayKey(rawReplayKey);
        validatePrincipalScope(principalName);
        validateReplayPayload(requestBody);
        throw new IllegalStateException("Durable ETL job replay persistence is not yet available");
    }

    private static String validateReplayKey(@Nullable String rawReplayKey) {
        if (rawReplayKey == null) {
            throw new EtlRequestException(EtlRequestError.JOB_REPLAY_KEY_REQUIRED);
        }
        var structuredFieldMatcher = IDEMPOTENCY_KEY_STRUCTURED_FIELD_PROFILE.matcher(rawReplayKey);
        if (structuredFieldMatcher.matches()) {
            return structuredFieldMatcher.group(1);
        }
        if (IDEMPOTENCY_KEY_VALUE_PROFILE.matcher(rawReplayKey).matches()) {
            return rawReplayKey;
        }
        throw new EtlRequestException(EtlRequestError.JOB_REPLAY_KEY_REQUIRED);
    }

    private static String validatePrincipalScope(@Nullable String principalName) {
        if (principalName == null
                || principalName.isBlank()
                || principalName.codePointCount(0, principalName.length())
                > MAX_PRINCIPAL_SCOPE_CODE_POINTS) {
            throw new EtlRequestException(EtlRequestError.IDEMPOTENCY_PRINCIPAL_REQUIRED);
        }
        return principalName;
    }

    private JsonNode validateReplayPayload(@Nullable String requestBody) {
        if (requestBody == null || requestBody.isEmpty()) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON);
        }
        try {
            JsonNode root = objectMapper.readTree(requestBody);
            if (root == null || !root.isArray()) {
                throw new EtlRequestException(EtlRequestError.INVALID_JSON);
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new EtlRequestException(EtlRequestError.INVALID_JSON, exception);
        }
    }
}
