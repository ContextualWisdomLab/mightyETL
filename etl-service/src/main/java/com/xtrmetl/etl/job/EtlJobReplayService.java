package com.xtrmetl.etl.job;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.service.EtlBatchProperties;
import com.xtrmetl.etl.service.EtlRequestLock;
import com.xtrmetl.etl.service.PostgresEtlRequestLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Admits owner-scoped durable-job replay requests on the repaired stack.
 *
 * <p>This initial implementation establishes only the validated collaborator boundary required by
 * the first replay-service contract. Replay admission behavior is added in subsequent test-first
 * increments so an incomplete branch cannot silently claim the full replay contract.</p>
 */
@Service
public class EtlJobReplayService {

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
}
