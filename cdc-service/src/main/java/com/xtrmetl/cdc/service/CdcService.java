package com.xtrmetl.cdc.service;

import io.debezium.config.Configuration;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Service
public class CdcService {

    private static final Logger log = LoggerFactory.getLogger(CdcService.class);

    private ExecutorService executor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean autoStart;

    private DebeziumEngine<ChangeEvent<String, String>> debeziumEngine;
    private Future<?> engineTask;

    public CdcService(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${xtrmetl.cdc.autostart:true}") boolean autoStart
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.autoStart = autoStart;
    }

    /**
     * Debezium CDC 엔진을 초기화하고 전용 단일 스레드 실행기에서 실행을 시작한다.
     *
     * 초기화된 엔진이 없으면 새 Debezium 엔진을 생성한 후 단일 스레드 실행기에 제출하여 비동기로 실행을 시작한다.
     */
    @PostConstruct
    public void maybeAutoStart() {
        if (autoStart) {
            start();
        }
    }

    /**
     * Debezium CDC 엔진 실행을 시작한다.
     *
     * <p>이 메서드는 idempotent 하며, 엔진이 이미 실행 중이면 아무 작업도 수행하지 않고 즉시 반환한다.</p>
     *
     * <p>동시에 여러 스레드에서 호출되더라도 {@code synchronized}로 보호되어 단일 실행만 보장한다.
     * 엔진은 내부 전용 단일 스레드 실행기에서 비동기로 실행된다.</p>
     *
     * <p>{@link #stop()}으로 엔진을 종료한 뒤 다시 호출하면 새로운 엔진 인스턴스를 초기화한 후 재시작한다.</p>
     */
    public synchronized void start() {
        if (this.executor == null) {
            this.executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "cdc-debezium-engine");
                thread.setDaemon(true);
                return thread;
            });
        }
        if (engineTask != null && !engineTask.isDone()) {
            return;
        }
        if (this.debeziumEngine == null) {
            initializeDebeziumEngine();
        }
        this.engineTask = this.executor.submit(debeziumEngine);
    }

    /**
     * Debezium CDC 엔진을 종료하고 내부 참조를 해제한다.
     *
     * 엔진 인스턴스가 존재하면 `close()`를 호출하여 종료하며 종료 중 발생한 `IOException`은 메시지를 추가해 래핑하여 다시 던진다.
     * 엔진이 `null`이면 아무 작업도 수행하지 않는다.
     *
     * @throws IOException 엔진의 `close()` 호출 중 발생한 입출력 예외를 래핑하여 던짐
     */
    public synchronized void stop() throws IOException {
        if (this.debeziumEngine != null) {
            try {
                this.debeziumEngine.close();
            } catch (IOException e) {
                throw new IOException("Error stopping CDC", e);
            } finally {
                this.debeziumEngine = null;
                this.engineTask = null;
            }
        }
    }

    /**
     * 애플리케이션 종료 시 Debezium 엔진 및 내부 실행기를 정리한다.
     *
     * <p>{@link #stop()}을 호출해 엔진을 종료하고, 이후 전용 단일 스레드 실행기를 종료한다.
     * 실행기 종료는 최대 5초까지 대기하며, 제한 시간 내 종료되지 않으면 {@code shutdownNow()}로 강제 종료한다.
     * 대기 중 인터럽트가 발생하면 인터럽트 플래그를 복원한 뒤 강제 종료를 시도한다.</p>
     */
    @PreDestroy
    public synchronized void shutdown() {
        try {
            stop();
        } catch (IOException e) {
            log.warn("Error while stopping CDC engine during shutdown", e);
        } finally {
            if (executor == null) {
                return;
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
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
        Path defaultCdcDataDir = defaultCdcDataDir();
        String offsetFile = getEnv("CDC_OFFSET_FILE", defaultCdcDataDir.resolve("offsets.dat").toString());
        String schemaHistoryFile = getEnv("CDC_SCHEMA_HISTORY_FILE", defaultCdcDataDir.resolve("schemahistory.dat").toString());
        ensureParentDirExists(offsetFile);
        ensureParentDirExists(schemaHistoryFile);

        return Configuration.create()
                .with("name", getEnv("CDC_CONNECTOR_NAME", "xtrmetl-postgres-connector"))
                .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
                .with("database.hostname", requireEnv("PGHOST"))
                .with("database.port", requireEnv("PGPORT"))
                .with("database.user", requireEnv("PGUSER"))
                .with("database.password", requireEnv("PGPASSWORD"))
                .with("database.dbname", requireEnv("PGDATABASE"))
                .with("topic.prefix", getEnv("CDC_TOPIC_PREFIX", "xtrmetl-cdc"))
                .with("schema.include.list", getEnv("CDC_SCHEMA_INCLUDE_LIST", "public"))
                .with("table.include.list", getEnv("CDC_TABLE_INCLUDE_LIST", "public.processed_data"))
                .with("plugin.name", getEnv("CDC_PLUGIN_NAME", "pgoutput"))
                .with("slot.name", getEnv("CDC_SLOT_NAME", "xtrmetl_slot"))
                .with("publication.autocreate.mode", getEnv("CDC_PUBLICATION_AUTOCREATE_MODE", "filtered"))
                .with("publication.name", getEnv("CDC_PUBLICATION_NAME", "xtrmetl_publication"))
                .with("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
                .with("offset.storage.file.filename", offsetFile)
                .with("offset.flush.interval.ms", getEnv("CDC_OFFSET_FLUSH_INTERVAL_MS", "1000"))
                .with("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory")
                .with("schema.history.internal.file.filename", schemaHistoryFile)
                .build();
    }

    private static Path defaultCdcDataDir() {
        String userHome = System.getProperty("user.home");
        Path baseDir = userHome == null || userHome.isBlank() ? Path.of(".") : Path.of(userHome);
        return baseDir.resolve(".xtrmetl").resolve("cdc");
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    private static void ensureParentDirExists(String filePath) {
        Path parent = Path.of(filePath).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create directory for " + filePath + ": " + parent, e);
        }
    }
}
