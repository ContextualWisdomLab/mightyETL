package com.xtrmetl.etl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class EtlService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public String processData(String data) {
        try {
            JsonNode jsonNode = objectMapper.readTree(data);
            if (jsonNode == null || jsonNode.isNull() || !jsonNode.isArray()) {
                throw new IllegalArgumentException("Input must be a JSON array");
            }
            List<CompletableFuture<String>> futures = new ArrayList<>();

            // Parallel processing of each record
            for (JsonNode record : jsonNode) {
                futures.add(CompletableFuture.supplyAsync(() -> processRecord(record)));
            }

            // Wait for all futures to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Collect results
            List<String> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            return String.join("\n", results);
        } catch (Exception e) {
            throw new RuntimeException("Error processing data: " + e.getMessage(), e);
        }
    }

    private String processRecord(JsonNode record) {
        String extractedData = extract(record);
        String transformedData = transform(extractedData);
        load(transformedData);
        return "Processed: " + record.get("id").asText();
    }

    private String extract(JsonNode record) {
        // Advanced extraction logic
        StringBuilder extracted = new StringBuilder();
        record.properties().forEach(field ->
                extracted.append(field.getKey()).append(":").append(field.getValue().asText()).append(",")
        );
        return extracted.toString();
    }

    private String transform(String data) {
        // Advanced transformation logic
        String[] fields = data.split(",");
        StringBuilder transformed = new StringBuilder();
        for (String field : fields) {
            String[] keyValue = field.split(":");
            if (keyValue.length == 2) {
                String key = keyValue[0].toUpperCase();
                String value = keyValue[1].trim();
                // Apply some transformations based on the field
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
                        } catch (NumberFormatException e) {
                            value = "0.00";
                        }
                        break;
                }
                transformed.append(key).append(":").append(value).append(",");
            }
        }
        return transformed.toString();
    }

    private void load(String data) {
        // Simulated database loading
        String sql = "INSERT INTO processed_data (data) VALUES (?)";
        jdbcTemplate.update(sql, data);
    }
}
