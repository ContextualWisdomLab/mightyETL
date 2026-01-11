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
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "xtrmetl.replica", name = "enabled", havingValue = "true")
public class SchemaChangeReplicaApplier {

    private static final Logger log = LoggerFactory.getLogger(SchemaChangeReplicaApplier.class);

    private static final int DDL_LOG_MAX_LENGTH = 500;
    private static final Set<String> IDEMPOTENT_DDL_SQL_STATES = Set.of(
            "42P06", // duplicate_schema
            "42P07", // duplicate_table
            "42701", // duplicate_column
            "42710"  // duplicate_object
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final boolean ddlEnabled;
    private final DdlValidationMode ddlValidationMode;
    private final Set<String> ddlAllowedPrefixes;
    private final Set<String> ddlBlockedPrefixes;

    /**
     * 레플리카에 대한 DDL 적용을 담당하는 SchemaChangeReplicaApplier 인스턴스를 초기화한다.
     *
     * @param jdbcTemplate  레플리카 데이터베이스에 DDL을 실행하는 JdbcTemplate (빈 이름 "replicaJdbcTemplate" 사용)
     * @param objectMapper  Debezium 이벤트의 JSON 페이로드에서 DDL을 추출하기 위해 사용하는 ObjectMapper
     * @param ddlEnabled    레플리카에 DDL 적용을 활성화할지 여부; `true`이면 수신된 스키마 변경 DDL을 실행함
     * @param ddlValidationMode DDL 적용 전 검증 모드 (none|whitelist|blocklist); legacy `blacklist` is accepted as an alias
     * @param ddlAllowedPrefixes whitelist 모드에서 허용할 DDL 접두어(Comma-separated)
     * @param ddlBlockedPrefixes blocklist 모드에서 차단할 DDL 접두어(Comma-separated)
     */
    public SchemaChangeReplicaApplier(
            @Qualifier("replicaJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${xtrmetl.replica.ddl-enabled:false}") boolean ddlEnabled,
            @Value("${xtrmetl.replica.ddl-validation-mode:none}") String ddlValidationMode,
            @Value("${xtrmetl.replica.ddl-allowed-prefixes:CREATE TABLE,ALTER TABLE,CREATE INDEX}") String ddlAllowedPrefixes,
            @Value("${xtrmetl.replica.ddl-blocked-prefixes:DROP TABLE,DROP SCHEMA,DROP DATABASE,TRUNCATE}") String ddlBlockedPrefixes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ddlEnabled = ddlEnabled;
        this.ddlValidationMode = DdlValidationMode.from(ddlValidationMode);
        this.ddlAllowedPrefixes = parseDdlPrefixes(ddlAllowedPrefixes);
        this.ddlBlockedPrefixes = parseDdlPrefixes(ddlBlockedPrefixes);

        if (ddlEnabled && this.ddlValidationMode == DdlValidationMode.WHITELIST && this.ddlAllowedPrefixes.isEmpty()) {
            throw new IllegalArgumentException(
                    "xtrmetl.replica.ddl-validation-mode=whitelist requires non-empty xtrmetl.replica.ddl-allowed-prefixes"
            );
        }
    }

    /**
     * 레플리카에 Debezium 스키마 변경 이벤트로부터 추출한 DDL을 적용한다.
     *
     * <p>구성이 DDL 적용을 비활성화했거나 토픽이 ".schema-changes"로 끝나지 않거나
     * valueJson이 비어있거나 페이로드에서 DDL을 추출하지 못하면 아무 작업도 수행하지 않는다.
     *
     * @param topic 이벤트 소스 토픽 이름 (".schema-changes"로 끝나는 경우에만 처리)
     * @param keyJson 이벤트 키의 JSON 표현 (이 메서드에서는 사용되지 않을 수 있음)
     * @param valueJson 이벤트 값의 JSON 표현 (이 값에서 DDL을 추출하여 적용함)
     * @throws org.springframework.dao.DataAccessException 레플리카에 DDL 적용 중 JDBC 실행 오류가 발생한 경우
     */
    public void apply(String topic, String keyJson, String valueJson) {
        if (!ddlEnabled) {
            return;
        }
        if (topic == null || !topic.endsWith(ReplicaTopics.SCHEMA_CHANGES_SUFFIX)) {
            return;
        }
        if (valueJson == null || valueJson.isBlank()) {
            return;
        }

        String ddl = extractDdl(valueJson);
        if (ddl == null || ddl.isBlank()) {
            return;
        }
        ddl = requireSingleStatement(topic, ddl);
        ddl = makeIdempotent(ddl);
        validateDdl(topic, ddl);

        try {
            jdbcTemplate.execute(ddl);
            if (log.isInfoEnabled()) {
                log.info("Applied schema change DDL on replica (topic={}, ddl={})", topic, truncateForLog(ddl));
            }
        } catch (DataAccessException e) {
            if (isIdempotentDuplicate(e)) {
                if (log.isInfoEnabled()) {
                    log.info(
                            "Schema change DDL already applied; skipping duplicate (topic={}, ddl={})",
                            topic,
                            truncateForLog(ddl)
                    );
                }
                return;
            }
            if (log.isErrorEnabled()) {
                log.error(
                        "Failed to apply schema change DDL on replica (topic={}, ddl={})",
                        topic,
                        truncateForLog(ddl),
                        e
                );
            } else {
                log.error("Failed to apply schema change DDL on replica (topic={})", topic, e);
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
            if (log.isWarnEnabled()) {
                log.warn("Blocked multi-statement DDL (topic={}, ddl={})", topic, truncateForLog(ddl));
            }
            throw new IllegalArgumentException("Multiple SQL statements are not allowed");
        }

        return trimmed;
    }

    /**
     * Debezium 스키마 변경 이벤트 JSON에서 DDL 문자열을 추출한다.
     *
     * @param valueJson Debezium 스키마 변경 이벤트 전체 JSON 문자열(루트 또는 `payload` 객체를 허용)
     * @return `ddl` 필드의 문자열 값, 해당 필드가 없거나 파싱 실패 시 `null`
     */
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

    /**
     * 레플리카 적용 시 카프카 재전달/재시도로 인한 중복 실행을 고려하여 DDL을 가능한 한 idempotent하게 변환한다.
     *
     * <p>주의: 변환에는 PostgreSQL 9.6+ 문법(예: {@code ALTER TABLE ... ADD COLUMN IF NOT EXISTS})이 포함되므로,
     * 레플리카 DB는 PostgreSQL 9.6 이상이어야 한다.
     */
    private String makeIdempotent(String ddl) {
        if (ddl == null) {
            return null;
        }
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

        String upper = rewritten.toUpperCase(Locale.ROOT);
        if (upper.startsWith("ALTER TABLE")) {
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
            if (cursor instanceof SQLException) {
                SQLException sqlException = (SQLException) cursor;
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

        if (ddlValidationMode == DdlValidationMode.BLOCKLIST) {
            boolean blocked = ddlBlockedPrefixes.stream().anyMatch(normalized::startsWith);
            if (blocked) {
                if (log.isWarnEnabled()) {
                    log.warn(
                            "Blocked DDL by validation policy (mode={}, topic={}, ddl={})",
                            ddlValidationMode,
                            topic,
                            truncateForLog(ddl)
                    );
                }
                throw new IllegalArgumentException("DDL blocked by validation policy");
            }
        }

        if (ddlValidationMode == DdlValidationMode.WHITELIST) {
            boolean allowed = ddlAllowedPrefixes.stream().anyMatch(normalized::startsWith);
            if (!allowed) {
                if (log.isWarnEnabled()) {
                    log.warn(
                            "Blocked DDL by validation policy (mode={}, topic={}, ddl={})",
                            ddlValidationMode,
                            topic,
                            truncateForLog(ddl)
                    );
                }
                throw new IllegalArgumentException("DDL blocked by validation policy");
            }
        }
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
                return NONE;
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
            throw new IllegalArgumentException("Unsupported xtrmetl.replica.ddl-validation-mode: " + value);
        }
    }
}
