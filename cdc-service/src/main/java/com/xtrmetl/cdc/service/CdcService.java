package com.xtrmetl.cdc.service;

import io.debezium.config.Configuration;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
public class CdcService {

    private final Executor executor = Executors.newSingleThreadExecutor();
    private DebeziumEngine<ChangeEvent<String, String>> debeziumEngine;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Debezium CDC 엔진을 초기화하고 전용 단일 스레드 실행기에서 실행을 시작한다.
     *
     * 초기화된 엔진이 없으면 새 Debezium 엔진을 생성한 후 단일 스레드 실행기에 제출하여 비동기로 실행을 시작한다.
     *
     */
    @PostConstruct
    public void start() {
        if (this.debeziumEngine == null) {
            initializeDebeziumEngine();
        }
        if (this.debeziumEngine == null) {
            throw new IllegalStateException("Debezium engine not initialized");
        }
        this.executor.execute(debeziumEngine);
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
        this.debeziumEngine = DebeziumEngine.create(Json.class)
                .using(getCdcConfiguration().asProperties())
                .notifying(this::handleChangeEvent)
                .build();
    }

    /**
     * Debezium에서 받은 CDC 변경 이벤트의 JSON key/value를 해당 topic으로 전송한다.
     *
     * key가 없으면 Spring Kafka 계약에 맞게 key-less send 오버로드를 사용한다.
     *
     * @param changeEvent Debezium의 변경 이벤트로부터 key/value와 destination을 포함하는 이벤트 객체
     */
    protected void handleChangeEvent(ChangeEvent<String, String> changeEvent) {
        String topic = changeEvent.destination();
        if (topic == null) {
            return;
        }

        String key = changeEvent.key();
        String value = changeEvent.value();

        if (key != null) {
            kafkaTemplate.send(topic, key, value);
        } else {
            kafkaTemplate.send(topic, value);
        }
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
