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

    private final CdcSourceRegistry registry;

    public CdcSourceFactory(CdcSourceRegistry registry) {
        this.registry = registry;
    }

    public Optional<CdcSourceConnector> resolve(String type) {
        if (type == null || type.isBlank()) {
            return Optional.empty();
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return registry.find(normalized);
    }

    /**
     * Validates and describes a multi-source configuration list without starting engines.
     * Live capture remains single-source in {@code CdcService}.
     *
     * @param specs configured source entries; {@code null} is treated as an empty list
     * @return one descriptive row for each configured source
     * @throws IllegalArgumentException when two entries declare the same source id
     */
    public List<Map<String, Object>> describeConfigured(List<SourceSpec> specs) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (specs == null) {
            return out;
        }
        Set<String> sourceIds = new HashSet<>();
        for (SourceSpec spec : specs) {
            if (!sourceIds.add(spec.id())) {
                throw new IllegalArgumentException("duplicate source id: " + spec.id());
            }
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", spec.id());
            row.put("type", spec.type());
            row.put("enabled", spec.enabled());
            Optional<CdcSourceConnector> connector = resolve(spec.type());
            row.put("registered", connector.isPresent());
            row.put("scaffoldOnly", connector.map(c -> c.capabilities().scaffoldOnly()).orElse(true));
            if (connector.isEmpty()) {
                row.put("error", "unknown_source_type");
            }
            out.add(row);
        }
        return out;
    }

    /**
     * Immutable source config entry (YAML list item).
     */
    public record SourceSpec(String id, String type, boolean enabled) {
        public SourceSpec {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("source id required");
            }
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("source type required");
            }
        }
    }
}
