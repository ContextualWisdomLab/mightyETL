package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xtrmetl.etl.config.EtlProcessingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Parses, validates, transforms, and loads accepted JSON record batches.
 *
 * <p>The complete request is admitted before any record is scheduled, so malformed records and
 * oversized batches cannot produce partial writes. Accepted work runs on a dedicated bounded
 * executor supplied by {@code EtlExecutorConfiguration}; the inline default exists only for
 * direct unit construction outside the Spring container.</p>
 */
@Service
public class EtlService {

    private static final String INSERT_SQL = "INSERT INTO processed_data (data) VALUES (?)";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private Executor executor = Runnable::run;
    private int maxBatchRecords = 1000;

    /**
     * Creates the service with its data-access and JSON dependencies.
     *
     * @param jdbcTemplate parameterized database access
     * @param objectMapper JSON parser
     */
    public EtlService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Applies the managed execution policy after construction.
     *
     * <p>Package visibility keeps focused service tests deterministic while Spring injects the
     * production executor and validated configuration through this method.</p>
     *
     * @param executor dedicated bounded ETL executor
     * @param properties ETL request and executor limits
     */
    @Autowired
    void configureExecution(
            @Qualifier("etlExecutor") Executor executor,
            EtlProcessingProperties properties
    ) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        EtlProcessingProperties validatedProperties = Objects.requireNonNull(
                properties,
                "properties must not be null"
        );
        validatedProperties.validate();
        this.maxBatchRecords = validatedProperties.getMaxBatchRecords();
    }

    /**
     * Processes a JSON array while retaining input-order response lines.
     *
     * @param data JSON array payload
     * @return one {@code Processed: <id>} line per accepted record, in input order
     * @throws RuntimeException when parsing, preflight validation, transformation, or loading fails
     */
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String processData(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            List<JsonNode> records = preflight(root);
            List<CompletableFuture<String>> futures = new ArrayList<>(records.size());

            for (JsonNode record : records) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> processRecord(record),
                        executor
                ));
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            List<String> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            return String.join("\n", results);
        } catch (Exception exception) {
            throw new RuntimeException(
                    "Error processing data: " + exception.getMessage(),
                    exception
            );
        }
    }

    private List<JsonNode> preflight(JsonNode root) {
        if (root == null || root.isNull() || !root.isArray()) {
            throw new IllegalArgumentException("Input must be a JSON array");
        }
        if (root.size() > maxBatchRecords) {
            throw new IllegalArgumentException(
                    "Batch contains " + root.size()
                            + " records; maximum is " + maxBatchRecords
            );
        }

        List<JsonNode> records = new ArrayList<>(root.size());
        int index = 0;
        for (JsonNode record : root) {
            if (record == null || !record.isObject()) {
                throw new IllegalArgumentException(
                        "Record at index " + index + " must be a JSON object"
                );
            }
            JsonNode id = record.get("id");
            boolean supportedId = id != null
                    && !id.isNull()
                    && (id.isTextual() || id.isIntegralNumber());
            if (!supportedId || (id.isTextual() && id.asText().isBlank())) {
                throw new IllegalArgumentException(
                        "Record at index " + index
                                + " requires a non-blank string or integral id"
                );
            }
            records.add(record);
            index++;
        }
        return List.copyOf(records);
    }

    private String processRecord(JsonNode record) {
        String extractedData = extract(record);
        String transformedData = transform(extractedData);
        load(transformedData);
        return "Processed: " + record.get("id").asText();
    }

    private String extract(JsonNode record) {
        StringBuilder extracted = new StringBuilder();
        record.properties().forEach(field ->
                extracted.append(field.getKey())
                        .append(":")
                        .append(field.getValue().asText())
                        .append(",")
        );
        return extracted.toString();
    }

    private String transform(String data) {
        String[] fields = data.split(",");
        StringBuilder transformed = new StringBuilder();
        for (String field : fields) {
            String[] keyValue = field.split(":");
            if (keyValue.length == 2) {
                String key = keyValue[0].toUpperCase();
                String value = keyValue[1].trim();
                switch (key) {
                    case "NAME":
                        value = value.toUpperCase();
                        break;
                    case "EMAIL":
                        value = value.toLowerCase();
                        break;
                    case "AMOUNT":
                        try {
                            double amount = Double.parseDouble(value);
                            value = String.format("%.2f", amount);
                        } catch (NumberFormatException exception) {
                            value = "0.00";
                        }
                        break;
                    default:
                        break;
                }
                transformed.append(key).append(":").append(value).append(",");
            }
        }
        return transformed.toString();
    }

    private void load(String data) {
        jdbcTemplate.update(INSERT_SQL, data);
    }
}
