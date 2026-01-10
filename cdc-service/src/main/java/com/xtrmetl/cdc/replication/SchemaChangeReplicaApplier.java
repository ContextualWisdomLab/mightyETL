package com.xtrmetl.cdc.replication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class SchemaChangeReplicaApplier {

    private static final Logger log = LoggerFactory.getLogger(SchemaChangeReplicaApplier.class);

    private static final String SCHEMA_CHANGES_SUFFIX = ".schema-changes";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final boolean ddlEnabled;

    public SchemaChangeReplicaApplier(
            @Qualifier("replicaJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${xtrmetl.replica.ddl-enabled:true}") boolean ddlEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ddlEnabled = ddlEnabled;
    }

    public void apply(String topic, String keyJson, String valueJson) {
        if (!ddlEnabled) {
            return;
        }
        if (topic == null || !topic.endsWith(SCHEMA_CHANGES_SUFFIX)) {
            return;
        }
        if (valueJson == null || valueJson.isBlank()) {
            return;
        }

        String ddl = extractDdl(valueJson);
        if (ddl == null || ddl.isBlank()) {
            return;
        }

        try {
            jdbcTemplate.execute(ddl);
            log.info("Applied schema change DDL on replica (topic={})", topic);
        } catch (DataAccessException e) {
            log.error("Failed to apply schema change DDL on replica (topic={})", topic, e);
            throw e;
        }
    }

    private String extractDdl(String valueJson) {
        try {
            JsonNode root = objectMapper.readTree(valueJson);
            JsonNode payload = root.has("payload") ? root.get("payload") : root;
            if (payload == null || payload.isNull()) {
                return null;
            }
            return payload.path("ddl").asText(null);
        } catch (IOException e) {
            log.warn("Failed to parse Debezium schema change JSON; skipping DDL apply", e);
            return null;
        }
    }
}
