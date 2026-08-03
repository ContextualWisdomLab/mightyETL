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
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies Debezium schema-change events to the configured replica database.
 *
 * <p>DDL execution is disabled by default. When enabled, the default policy is a positive
 * allow-list, only a single comment-free statement is accepted, and prefix matches must end
 * at a SQL token boundary.
 */
@Service
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class SchemaChangeReplicaApplier {

    private static final Logger log = LoggerFactory.getLogger(SchemaChangeReplicaApplier.class);
    private static final int DDL_LOG_MAX_LENGTH = 500;
    private static final Set<String> IDEMPOTENT_DDL_SQL_STATES = Set.of(
            "42P06",
            "42P07",
            "42701",
            "42710"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final boolean ddlEnabled;
    private final DdlValidationMode ddlValidationMode;
    private final Set<String> ddlAllowedPrefixes;
    private final Set<String> ddlBlockedPrefixes;

    public SchemaChangeReplicaApplier(
            @Qualifier("replicaJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${xtrmetl.replica.ddl-enabled:false}") boolean ddlEnabled,
            @Value("${xtrmetl.replica.ddl-validation-mode:whitelist}") String ddlValidationMode,
            @Value("${xtrmetl.replica.ddl-allowed-prefixes:CREATE TABLE,ALTER TABLE,CREATE INDEX,CREATE UNIQUE INDEX}") String ddlAllowedPrefixes,
            @Value("${xtrmetl.replica.ddl-blocked-prefixes:DROP TABLE,DROP SCHEMA,DROP DATABASE,TRUNCATE}") String ddlBlockedPrefixes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ddlEnabled = ddlEnabled;
        this.ddlValidationMode = DdlValidationMode.from(ddlValidationMode);
        this.ddlAllowedPrefixes = parseDdlPrefixes(ddlAllowedPrefixes);
        this.ddlBlockedPrefixes = parseDdlPrefixes(ddlBlockedPrefixes);

        if (ddlEnabled && this.ddlValidationMode == DdlValidationMode.WHITELIST
                && this.ddlAllowedPrefixes.isEmpty()) {
            throw new IllegalArgumentException(
                    "xtrmetl.replica.ddl-validation-mode=whitelist requires non-empty "
                            + "xtrmetl.replica.ddl-allowed-prefixes"
            );
        }
    }

    public void apply(@Nullable String topic, @Nullable String keyJson, @Nullable String valueJson) {
        if (!ddlEnabled
                || topic == null
                || !topic.endsWith(ReplicaTopics.SCHEMA_CHANGES_SUFFIX)
                || valueJson == null
                || valueJson.isBlank()) {
            return;
        }

        String ddl = extractDdl(valueJson);
        if (ddl == null || ddl.isBlank()) {
            return;
        }

        ddl = requireSingleStatement(topic, ddl);
        ddl = requireCommentFree(topic, ddl);
        ddl = makeIdempotent(ddl);
        validateDdl(topic, ddl);

        try {
            // DDL identifiers cannot be JDBC bind parameters. The statement has passed the
            // single-statement, comment-free, and configured policy gates above.
            jdbcTemplate.execute(ddl); // nosemgrep: java.spring.security.audit.spring-sqli.spring-sqli
            if (log.isInfoEnabled()) {
                log.info("Applied schema change DDL on replica (topic={}, ddl={})",
                        topic, truncateForLog(ddl));
            }
        } catch (DataAccessException e) {
            if (isIdempotentDuplicate(e)) {
                if (log.isInfoEnabled()) {
                    log.info("Schema change DDL already applied; skipping duplicate (topic={}, ddl={})",
                            topic, truncateForLog(ddl));
                }
                return;
            }
            if (log.isErrorEnabled()) {
                log.error("Failed to apply schema change DDL on replica (topic={}, ddl={})",
                        topic, truncateForLog(ddl), e);
            }
            throw e;
        }
    }

    private String requireSingleStatement(String topic, String ddl) {
        String trimmed = ddl.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        if (trimmed.contains(";")) {
            logBlocked(topic, ddl, "Blocked multi-statement DDL");
            throw new IllegalArgumentException("Multiple SQL statements are not allowed");
        }
        return trimmed;
    }

    private String requireCommentFree(String topic, String ddl) {
        if (ddl.contains("--") || ddl.contains("/*") || ddl.contains("*/") || ddl.indexOf('\0') >= 0) {
            logBlocked(topic, ddl, "Blocked DDL containing SQL comments or NUL");
            throw new IllegalArgumentException("SQL comments and NUL characters are not allowed in replicated DDL");
        }
        return ddl;
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

    private String makeIdempotent(String ddl) {
        String rewritten = ddl.trim();

        rewritten = rewritten.replaceFirst(
                "(?i)^CREATE\\s+TABLE\\s+(?!IF\\s+NOT\\s+EXISTS\\b)",
                "CREATE TABLE IF NOT EXISTS "
        );
        rewritten = rewritten.replaceFirst(
                "(?i)^CREATE\\s+SCHEMA\\s+(?!IF\\s+NOT\\s+EXISTS\\b)",
                "CREATE SCHEMA IF NOT EXISTS "
        );
        rewritten = rewritten.replaceFirst(
                "(?i)^CREATE\\s+UNIQUE\\s+INDEX\\s+CONCURRENTLY\\s+(?!IF\\s+NOT\\s+EXISTS\\b)",
                "CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS "
        );
        rewritten = rewritten.replaceFirst(
                "(?i)^CREATE\\s+INDEX\\s+CONCURRENTLY\\s+(?!IF\\s+NOT\\s+EXISTS\\b)",
                "CREATE INDEX CONCURRENTLY IF NOT EXISTS "
        );
        rewritten = rewritten.replaceFirst(
                "(?i)^CREATE\\s+UNIQUE\\s+INDEX\\s+(?!CONCURRENTLY\\b)(?!IF\\s+NOT\\s+EXISTS\\b)",
                "CREATE UNIQUE INDEX IF NOT EXISTS "
        );
        rewritten = rewritten.replaceFirst(
                "(?i)^CREATE\\s+INDEX\\s+(?!CONCURRENTLY\\b)(?!IF\\s+NOT\\s+EXISTS\\b)",
                "CREATE INDEX IF NOT EXISTS "
        );
        rewritten = rewritten.replaceFirst(
                "(?i)^DROP\\s+INDEX\\s+CONCURRENTLY\\s+(?!IF\\s+EXISTS\\b)",
                "DROP INDEX CONCURRENTLY IF EXISTS "
        );
        rewritten = rewritten.replaceFirst(
                "(?i)^DROP\\s+INDEX\\s+(?!CONCURRENTLY\\b)(?!IF\\s+EXISTS\\b)",
                "DROP INDEX IF EXISTS "
        );
        rewritten = rewritten.replaceFirst(
                "(?i)^DROP\\s+TABLE\\s+(?!IF\\s+EXISTS\\b)",
                "DROP TABLE IF EXISTS "
        );
        rewritten = rewritten.replaceFirst(
                "(?i)^DROP\\s+SCHEMA\\s+(?!IF\\s+EXISTS\\b)",
                "DROP SCHEMA IF EXISTS "
        );

        if (rewritten.toUpperCase(Locale.ROOT).startsWith("ALTER TABLE")) {
            rewritten = rewritten.replaceAll(
                    "(?i)\\bADD\\s+COLUMN\\s+(?!IF\\s+NOT\\s+EXISTS(?![A-Za-z0-9_]))",
                    "ADD COLUMN IF NOT EXISTS "
            );
            rewritten = rewritten.replaceAll(
                    "(?i)\\bDROP\\s+COLUMN\\s+(?!IF\\s+EXISTS(?![A-Za-z0-9_]))",
                    "DROP COLUMN IF EXISTS "
            );
        }
        return rewritten;
    }

    private boolean isIdempotentDuplicate(DataAccessException exception) {
        Throwable cursor = exception;
        while (cursor != null) {
            if (cursor instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && IDEMPOTENT_DDL_SQL_STATES.contains(sqlState)) {
                    return true;
                }
                String message = sqlException.getMessage();
                if (message != null && message.toLowerCase(Locale.ROOT).contains("already exists")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private void validateDdl(String topic, String ddl) {
        if (ddlValidationMode == DdlValidationMode.NONE) {
            return;
        }

        String normalized = normalizeForValidation(ddl);
        if (ddlValidationMode == DdlValidationMode.BLOCKLIST
                && ddlBlockedPrefixes.stream().anyMatch(prefix -> matchesPrefix(normalized, prefix))) {
            logBlocked(topic, ddl, "Blocked DDL by validation policy");
            throw new IllegalArgumentException("DDL blocked by validation policy");
        }

        if (ddlValidationMode == DdlValidationMode.WHITELIST
                && ddlAllowedPrefixes.stream().noneMatch(prefix -> matchesPrefix(normalized, prefix))) {
            logBlocked(topic, ddl, "Blocked DDL by validation policy");
            throw new IllegalArgumentException("DDL blocked by validation policy");
        }
    }

    private void logBlocked(String topic, String ddl, String message) {
        if (log.isWarnEnabled()) {
            log.warn("{} (mode={}, topic={}, ddl={})",
                    message, ddlValidationMode, topic, truncateForLog(ddl));
        }
    }

    private static boolean matchesPrefix(String normalizedDdl, String normalizedPrefix) {
        return normalizedDdl.equals(normalizedPrefix)
                || normalizedDdl.startsWith(normalizedPrefix + " ");
    }

    private static Set<String> parseDdlPrefixes(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(SchemaChangeReplicaApplier::normalizeForValidation)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeForValidation(String ddl) {
        return ddl.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String truncateForLog(String ddl) {
        if (ddl == null) {
            return null;
        }
        String normalized = ddl.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= DDL_LOG_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, DDL_LOG_MAX_LENGTH) + "...";
    }

    private enum DdlValidationMode {
        NONE,
        WHITELIST,
        BLOCKLIST;

        static DdlValidationMode from(String value) {
            if (value == null || value.isBlank()) {
                return WHITELIST;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if ("BLACKLIST".equals(normalized)) {
                return BLOCKLIST;
            }
            for (DdlValidationMode mode : values()) {
                if (mode.name().equals(normalized)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException(
                    "Unsupported xtrmetl.replica.ddl-validation-mode: " + value
            );
        }
    }
}
