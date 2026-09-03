package com.xtrmetl.cdc.spi;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves configured CDC source type ids to {@link CdcSourceConnector} instances.
 *
 * <p>Today only {@code postgres-debezium} is registered. Additional types can be
 * added to the registry without changing {@code CdcService} immediately.</p>
 */
@Component
public class CdcSourceFactory {

    private final CdcSourceRegistry sourceRegistry;

    public CdcSourceFactory(CdcSourceRegistry sourceRegistry) {
        this.sourceRegistry = sourceRegistry;
    }

    public Optional<CdcSourceConnector> resolve(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return Optional.empty();
        }
        String normalizedSourceType = sourceType.trim().toLowerCase(Locale.ROOT);
        return sourceRegistry.find(normalizedSourceType);
    }

    /**
     * Validates and describes a multi-source configuration list without starting engines.
     * Live capture remains single-source in {@code CdcService}.
     *
     * @param sourceSpecs configured source entries; {@code null} is treated as an empty list
     * @return one descriptive row for each configured source
     * @throws IllegalArgumentException when two entries declare the same source id
     */
    public List<Map<String, Object>> describeConfigured(List<SourceSpec> sourceSpecs) {
        List<Map<String, Object>> sourceDescriptions = new ArrayList<>();
        if (sourceSpecs == null) {
            return sourceDescriptions;
        }
        Set<String> sourceIds = new HashSet<>();
        for (SourceSpec sourceSpec : sourceSpecs) {
            if (!sourceIds.add(sourceSpec.sourceId())) {
                throw new IllegalArgumentException("duplicate source id: " + sourceSpec.sourceId());
            }
            Map<String, Object> sourceDescription = new java.util.LinkedHashMap<>();
            // Compatibility boundary: the existing HTTP response still publishes legacy id/type keys.
            sourceDescription.put("id", sourceSpec.sourceId());
            sourceDescription.put("type", sourceSpec.sourceType());
            sourceDescription.put("enabled", sourceSpec.enabled());
            Optional<CdcSourceConnector> sourceConnector = resolve(sourceSpec.sourceType());
            sourceDescription.put("registered", sourceConnector.isPresent());
            sourceDescription.put(
                    "scaffoldOnly",
                    sourceConnector.map(connector -> connector.capabilities().scaffoldOnly()).orElse(true)
            );
            if (sourceConnector.isEmpty()) {
                sourceDescription.put("error", "unknown_source_type");
            }
            sourceDescriptions.add(sourceDescription);
        }
        return sourceDescriptions;
    }

    /**
     * Immutable source config entry (YAML list item).
     */
    public record SourceSpec(String sourceId, String sourceType, boolean enabled) {
        public SourceSpec {
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException("source id required");
            }
            if (sourceType == null || sourceType.isBlank()) {
                throw new IllegalArgumentException("source type required");
            }
        }
    }
}
