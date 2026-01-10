package com.xtrmetl.cdc.service;

import io.debezium.config.Configuration;
import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
public class CdcService {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private DebeziumEngine<RecordChangeEvent<SourceRecord>> debeziumEngine;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Debezium CDC 엔진을 초기화하고 전용 단일 스레드 실행기에서 실행을 시작한다.
     *
     * 초기화된 엔진이 없으면 새 Debezium 엔진을 생성한 후 단일 스레드 실행기에 제출하여 비동기로 실행을 시작한다.
     *
     * @throws NullPointerException 엔진이 초기화되지 않아 실행할 수 없는 경우
     */
    @PostConstruct
    public void start() {
        if (this.debeziumEngine == null) {
            initializeDebeziumEngine();
        }
        this.executor.execute(Objects.requireNonNull(debeziumEngine, "Debezium engine"));
    }

    /**
     * Debezium CDC 엔진을 종료하고 내부 참조를 해제한다.
     *
     * 엔진 인스턴스가 존재하면 `close()`를 호출하여 종료하며 종료 중 발생한 `IOException`은 메시지를 추가해 래핑하여 다시 던진다.
     * 엔진이 `null`이면 아무 작업도 수행하지 않는다.
     *
     * @throws IOException 엔진의 `close()` 호출 중 발생한 입출력 예외를 래핑하여 던짐
     */
    @PreDestroy
    public void stop() throws IOException {
        if (this.debeziumEngine != null) {
            try {
                this.debeziumEngine.close();
            } catch (IOException e) {
                throw new IOException("Error stopping CDC: " + e.getMessage(), e);
            } finally {
                this.debeziumEngine = null;
            }
        }
    }

    private void initializeDebeziumEngine() {
        this.debeziumEngine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
                .using(getCdcConfiguration().asProperties())
                .notifying(this::handleChangeEvent)
                .build();
    }

    /**
     * Debezium에서 받은 CDC 변경 이벤트의 SourceRecord를 추출해 해당 topic으로 키·값을 Kafka에 전송한다.
     *
     * @param sourceRecordRecordChangeEvent Debezium의 변경 이벤트로부터 SourceRecord를 포함하는 이벤트 객체
     * @throws NullPointerException topic, key 또는 value가 null인 경우 발생한다
     */
    protected void handleChangeEvent(RecordChangeEvent<SourceRecord> sourceRecordRecordChangeEvent) {
        SourceRecord sourceRecord = sourceRecordRecordChangeEvent.record();
        String topic = Objects.requireNonNull(sourceRecord.topic(), "CDC source record topic");
        String key = Objects.requireNonNull(sourceRecord.key(), "CDC source record key").toString();
        String value = Objects.requireNonNull(sourceRecord.value(), "CDC source record value").toString();

        kafkaTemplate.send(topic, key, value);
    }

    private Configuration getCdcConfiguration() {
        return Configuration.create()
                .with("name", "xtrmetl-postgres-connector")
                .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
                .with("database.hostname", System.getenv("PGHOST"))
                .with("database.port", System.getenv("PGPORT"))
                .with("database.user", System.getenv("PGUSER"))
                .with("database.password", System.getenv("PGPASSWORD"))
                .with("database.dbname", System.getenv("PGDATABASE"))
                .with("database.server.name", "xtrmetl-server")
                .with("topic.prefix", "xtrmetl-cdc")
                .with("schema.include.list", "public")
                .with("table.include.list", "public.your_table_name") // Replace with your actual table name
                .with("plugin.name", "pgoutput")
                .build();
    }
}