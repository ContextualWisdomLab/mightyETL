package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Validates, transforms, and loads one bounded ETL request batch.
 *
 * <p>The complete payload is parsed and transformed before the first JDBC call. Accepted records
 * are then written synchronously inside one Spring transaction, so a runtime failure rolls the
 * batch back rather than leaving committed prefix records. The service intentionally avoids the
 * JVM common pool and one-task-per-record fan-out.</p>
 */
@Service
public class EtlService {

    private static final String INSERT_SQL = "INSERT INTO processed_data (data) VALUES (?)";
    private static final int MAX_AMOUNT_PRECISION = 38;
    private static final int MAX_AMOUNT_ABSOLUTE_SCALE = 18;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EtlBatchProperties batchProperties;

    /**
     * Creates the ETL service.
     *
     * @param jdbcTemplate parameterized database access
     * @param objectMapper JSON parser
     * @param batchProperties request safety limits; {@code null} uses safe defaults for legacy
     *                        direct construction and Mockito-based tests
     */
    public EtlService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Nullable EtlBatchProperties batchProperties
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.batchProperties = batchProperties == null
                ? new EtlBatchProperties()
                : batchProperties;
    }

    /**
     * Processes one JSON-array request as a prevalidated transaction-scoped batch.
     *
     * <p>Only transient Spring data-access failures are retried. Invalid client input and
     * deterministic constraint violations fail immediately instead of repeating the same work.</p>
     *
     * @param data UTF-8 JSON array payload
     * @return one {@code Processed: <id>} line per record, in input order
     * @throws RuntimeException when parsing, admission, transformation, or loading fails
     */
    @Retryable(
            retryFor = TransientDataAccessException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    @Transactional
    public String processData(String data) {
        try {
            String payload = Objects.requireNonNull(data, "Input payload must not be null");
            enforcePayloadLimit(payload);

            JsonNode root = objectMapper.readTree(payload);
            List<ProcessedRecord> preparedRecords = prepareBatch(root);

            for (ProcessedRecord record : preparedRecords) {
                jdbcTemplate.update(INSERT_SQL, record.data());
            }

            return preparedRecords.stream()
                    .map(record -> "Processed: " + record.id())
                    .collect(Collectors.joining("\n"));
        } catch (DataAccessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException(
                    "Error processing data: " + exception.getMessage(),
                    exception
            );
        }
    }

    private void enforcePayloadLimit(String payload) {
        int payloadBytes = payload.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > batchProperties.getMaxPayloadBytes()) {
            throw new IllegalArgumentException(
                    "Input payload contains " + payloadBytes
                            + " UTF-8 bytes; maximum is "
                            + batchProperties.getMaxPayloadBytes()
            );
        }
    }

    private List<ProcessedRecord> prepareBatch(@Nullable JsonNode root) {
        if (root == null || root.isNull() || !root.isArray()) {
            throw new IllegalArgumentException("Input must be a JSON array");
        }
        if (root.size() > batchProperties.getMaxBatchRecords()) {
            throw new IllegalArgumentException(
                    "Input contains " + root.size()
                            + " records; maximum is "
                            + batchProperties.getMaxBatchRecords()
            );
        }

        List<ProcessedRecord> preparedRecords = new ArrayList<>(root.size());
        int index = 0;
        for (JsonNode record : root) {
            preparedRecords.add(prepareRecord(record, index));
            index++;
        }
        return List.copyOf(preparedRecords);
    }

    private ProcessedRecord prepareRecord(@Nullable JsonNode record, int index) {
        if (record == null || !record.isObject()) {
            throw new IllegalArgumentException(
                    "Record at index " + index + " must be a JSON object"
            );
        }

        JsonNode idNode = record.get("id");
        if (idNode == null || !idNode.isTextual() || idNode.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Record at index " + index + " requires a non-blank string id"
            );
        }

        String id = idNode.asText();
        String transformedData = transformRecord(record);
        return new ProcessedRecord(id, transformedData);
    }

    private String transformRecord(JsonNode record) {
        StringBuilder transformed = new StringBuilder();
        for (Map.Entry<String, JsonNode> field : record.properties()) {
            String key = field.getKey().toUpperCase(Locale.ROOT);
            String value = transformValue(key, field.getValue());
            transformed.append(key).append(":").append(value).append(",");
        }
        return transformed.toString();
    }

    private String transformValue(String key, @Nullable JsonNode valueNode) {
        String value = valueNode == null || valueNode.isNull()
                ? "null"
                : valueNode.asText();
        return switch (key) {
            case "NAME" -> value.toUpperCase(Locale.ROOT);
            case "EMAIL" -> value.toLowerCase(Locale.ROOT);
            case "AMOUNT" -> formatAmount(value);
            default -> value;
        };
    }

    private String formatAmount(String value) {
        try {
            BigDecimal amount = new BigDecimal(value.trim());
            int scale = amount.scale();
            if (amount.precision() > MAX_AMOUNT_PRECISION
                    || scale < -MAX_AMOUNT_ABSOLUTE_SCALE
                    || scale > MAX_AMOUNT_ABSOLUTE_SCALE) {
                return "0.00";
            }
            return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException exception) {
            return "0.00";
        }
    }

    private record ProcessedRecord(String id, String data) {
        private ProcessedRecord {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(data, "data must not be null");
        }
    }
}
