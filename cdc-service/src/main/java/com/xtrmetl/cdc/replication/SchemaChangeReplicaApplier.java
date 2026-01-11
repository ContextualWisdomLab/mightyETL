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

    /**
     * 레플리카에 대한 DDL 적용을 담당하는 SchemaChangeReplicaApplier 인스턴스를 초기화한다.
     *
     * @param jdbcTemplate  레플리카 데이터베이스에 DDL을 실행하는 JdbcTemplate (빈 이름 "replicaJdbcTemplate" 사용)
     * @param objectMapper  Debezium 이벤트의 JSON 페이로드에서 DDL을 추출하기 위해 사용하는 ObjectMapper
     * @param ddlEnabled    레플리카에 DDL 적용을 활성화할지 여부; `true`이면 수신된 스키마 변경 DDL을 실행함
     */
    public SchemaChangeReplicaApplier(
            @Qualifier("replicaJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${xtrmetl.replica.ddl-enabled:true}") boolean ddlEnabled
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ddlEnabled = ddlEnabled;
    }

    /**
     * 리플리카에 Debezium 스키마 변경 이벤트로부터 추출한 DDL을 적용한다.
     *
     * <p>구성이 DDL 적용을 비활성화했거나 토픽이 ".schema-changes"로 끝나지 않거나
     * valueJson이 비어있거나 페이로드에서 DDL을 추출하지 못하면 아무 작업도 수행하지 않는다.
     *
     * @param topic 이벤트 소스 토픽 이름 (".schema-changes"로 끝나는 경우에만 처리)
     * @param keyJson 이벤트 키의 JSON 표현 (이 메서드에서는 사용되지 않을 수 있음)
     * @param valueJson 이벤트 값의 JSON 표현 (이 값에서 DDL을 추출하여 적용함)
     * @throws org.springframework.dao.DataAccessException 리플리카에 DDL 적용 중 JDBC 실행 오류가 발생한 경우
     */
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
}