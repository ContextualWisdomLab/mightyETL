package com.xtrmetl.cdc.service;

import com.xtrmetl.cdc.spi.DebeziumChangeRecordMapper;
import com.xtrmetl.cdc.spi.PostgresDebeziumCdcSource;
import com.xtrmetl.cdc.util.EnvUtils;
import com.xtrmetl.cdc.util.ValidationUtils;
import io.debezium.config.Configuration;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CdcService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CdcService.class);

    private ExecutorService executor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean autoStart;
    private final boolean canonicalMapEnabled;
    @Nullable
    private final DebeziumChangeRecordMapper changeRecordMapper;
    private final AtomicLong canonicalMapSuccess = new AtomicLong();
    private final AtomicLong canonicalMapFailure = new AtomicLong();

    private DebeziumEngine<ChangeEvent<String, String>> debeziumEngine;
    private Future<?> engineTask;

    public CdcService(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${xtrmetl.cdc.autostart:true}") boolean autoStart,
            @Value("${xtrmetl.cdc.canonical-map-enabled:false}") boolean canonicalMapEnabled,
            @Nullable DebeziumChangeRecordMapper changeRecordMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.autoStart = autoStart;
        this.canonicalMapEnabled = canonicalMapEnabled;
        this.changeRecordMapper = changeRecordMapper;
    }

    /**
     * Test helper / backward-compatible constructor.
     */
    public CdcService(KafkaTemplate<String, String> kafkaTemplate, boolean autoStart) {
        this(kafkaTemplate, autoStart, false, null);
    }

    /**
     * Auto-starts the Debezium CDC engine when enabled.
     *
     * Debezium CDC 엔진을 초기화하고 전용 단일 스레드 실행기에서 실행을 시작한다.
     *
     * 초기화된 엔진이 없으면 새 Debezium 엔진을 생성한 후 단일 스레드 실행기에 제출하여 비동기로 실행을 시작한다.
     */
    public void maybeAutoStart() {
        if (autoStart) {
            start();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        maybeAutoStart();
    }

    /**
     * Starts the Debezium CDC engine.
     *
     * Debezium CDC 엔진 실행을 시작한다.
     *
     * <p>This method is idempotent; if the engine is already running, it returns immediately.</p>
     *
     * <p>이 메서드는 idempotent 하며, 엔진이 이미 실행 중이면 아무 작업도 수행하지 않고 즉시 반환한다.</p>
     *
     * <p>This method is {@code synchronized} and safe to call concurrently. The engine runs asynchronously on a
     * dedicated single-thread executor.</p>
     *
     * <p>동시에 여러 스레드에서 호출되더라도 {@code synchronized}로 보호되어 단일 실행만 보장한다.
     * 엔진은 내부 전용 단일 스레드 실행기에서 비동기로 실행된다.</p>
     *
     * <p>After {@link #stop()}, calling {@code start()} again will initialize a new engine instance and restart.</p>
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
     * Stops the Debezium CDC engine and clears internal references.
     *
     * Debezium CDC 엔진을 종료하고 내부 참조를 해제한다.
     *
     * Calls {@code close()} when the engine instance exists. Any {@link IOException} is wrapped and rethrown.
     * If the engine is {@code null}, this method does nothing.
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
     * Whether the Debezium engine task is currently running.
     */
    public synchronized boolean isRunning() {
        return engineTask != null && !engineTask.isDone();
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    /**
     * Operator-facing status (no secrets). Safe for health dashboards and support.
     */
    public synchronized Map<String, Object> getStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("product", "mightyETL");
        status.put("running", isRunning());
        status.put("autoStart", autoStart);
        status.put("sourceType", "postgres-debezium");
        status.put("sourceId", "postgres-debezium");
        status.put("connectorName", EnvUtils.getEnv("CDC_CONNECTOR_NAME", "xtrmetl-postgres-connector"));
        status.put("topicPrefix", EnvUtils.getEnv("CDC_TOPIC_PREFIX", "xtrmetl-cdc"));
        status.put("schemaIncludeList", EnvUtils.getEnv("CDC_SCHEMA_INCLUDE_LIST", "public"));
        status.put("tableIncludeList", EnvUtils.getEnv("CDC_TABLE_INCLUDE_LIST", "public.processed_data"));
        status.put("slotName", EnvUtils.getEnv("CDC_SLOT_NAME", "xtrmetl_slot"));
        status.put("publicationName", EnvUtils.getEnv("CDC_PUBLICATION_NAME", "xtrmetl_publication"));
        status.put("includeSchemaChanges", EnvUtils.getEnv("CDC_INCLUDE_SCHEMA_CHANGES", "true"));
        status.put("anyToAny", false);
        status.put("canonicalMapEnabled", canonicalMapEnabled);
        status.put("canonicalMapSuccess", canonicalMapSuccess.get());
        status.put("canonicalMapFailure", canonicalMapFailure.get());
        status.put("configPrefixes", "mightyetl.* (preferred) or xtrmetl.* (legacy); dual-read via EnvironmentPostProcessor");
        status.put("notes", "Capture is PostgreSQL→Kafka only; see docs/cdc/any-to-any-cdc.md");
        return status;
    }

    /**
     * Cleans up the Debezium engine and executor during application shutdown.
     *
     * 애플리케이션 종료 시 Debezium 엔진 및 내부 실행기를 정리한다.
     *
     * <p>Stops the engine via {@link #stop()} and then shuts down the dedicated executor. Waits up to 5 seconds for
     * termination and falls back to {@code shutdownNow()}. If interrupted, restores the interrupt flag and attempts a
     * forceful shutdown.</p>
     *
     * <p>{@link #stop()}을 호출해 엔진을 종료하고, 이후 전용 단일 스레드 실행기를 종료한다.
     * 실행기 종료는 최대 5초까지 대기하며, 제한 시간 내 종료되지 않으면 {@code shutdownNow()}로 강제 종료한다.
     * 대기 중 인터럽트가 발생하면 인터럽트 플래그를 복원한 뒤 강제 종료를 시도한다.</p>
     */
    public synchronized void shutdown() {
        try {
            stop();
        } catch (IOException e) {
            log.warn("Error while stopping CDC engine during shutdown", e);
        } finally {
            ExecutorService executorToShutdown = executor;
            executor = null;
            if (executorToShutdown == null) {
                return;
            }
            executorToShutdown.shutdown();
            try {
                if (!executorToShutdown.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorToShutdown.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executorToShutdown.shutdownNow();
            }
        }
    }

    private void initializeDebeziumEngine() {
        this.debeziumEngine = DebeziumEngine.create(Json.class)
                .using(getCdcConfiguration().asProperties())
                .notifying(this::handleChangeEvent)
                .build();
    }

    @Override
    public void destroy() {
        shutdown();
    }

    /**
     * Publishes Debezium key/value JSON to the destination Kafka topic.
     *
     * Debezium에서 받은 CDC 변경 이벤트의 JSON key/value를 해당 topic으로 전송한다.
     *
     * Uses a key-less send overload when the key is absent, following Spring Kafka conventions.
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

        maybeMapCanonical(topic, key, value);

        // Live path: raw Debezium JSON (not canonical) for consumer compatibility.
        if (key != null) {
            kafkaTemplate.send(topic, key, value);
        } else {
            kafkaTemplate.send(topic, value);
        }
    }

    /**
     * Optional validation path for any-to-any scaffolding. Fail-open: never blocks Kafka publish.
     */
    private void maybeMapCanonical(String topic, String key, String value) {
        if (!canonicalMapEnabled || changeRecordMapper == null) {
            return;
        }
        try {
            boolean ok = changeRecordMapper
                    .map(PostgresDebeziumCdcSource.ID, topic, key, value)
                    .isPresent();
            if (ok) {
                canonicalMapSuccess.incrementAndGet();
            } else {
                canonicalMapFailure.incrementAndGet();
                log.debug("Canonical map produced empty result for topic={}", topic);
            }
        } catch (RuntimeException e) {
            canonicalMapFailure.incrementAndGet();
            log.debug("Canonical map failed for topic={}: {}", topic, e.toString());
        }
    }

    /**
     * Debezium PostgreSQL 커넥터용 CDC 설정을 환경 변수와 기본값으로 구성하여 생성한다.
     *
     * 생성된 구성은 오프셋 및 스키마 히스토리 파일 경로를 포함하며, 해당 파일의 부모 디렉터리가 존재하도록 보장한다.
     *
     * @return Debezium Engine에서 사용할 Configuration 인스턴스
     */
    private Configuration getCdcConfiguration() {
        Path defaultCdcDataDir = defaultCdcDataDir();
        String offsetFile = EnvUtils.getEnv("CDC_OFFSET_FILE", defaultCdcDataDir.resolve("offsets.dat").toString());
        String schemaHistoryFile = EnvUtils.getEnv("CDC_SCHEMA_HISTORY_FILE", defaultCdcDataDir.resolve("schemahistory.dat").toString());
        ensureParentDirExists(offsetFile);
        ensureParentDirExists(schemaHistoryFile);

        return Configuration.create()
                .with("name", EnvUtils.getEnv("CDC_CONNECTOR_NAME", "xtrmetl-postgres-connector"))
                .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
                .with("database.hostname", EnvUtils.requireEnv("PGHOST"))
                .with("database.port", ValidationUtils.requireValidPort(EnvUtils.getEnv("PGPORT", "5432"), "PGPORT"))
                .with("database.user", EnvUtils.requireEnv("PGUSER"))
                .with("database.password", EnvUtils.requireEnv("PGPASSWORD"))
                .with("database.dbname", EnvUtils.requireEnv("PGDATABASE"))
                .with("topic.prefix", EnvUtils.getEnv("CDC_TOPIC_PREFIX", "xtrmetl-cdc"))
                .with("include.schema.changes", EnvUtils.getEnv("CDC_INCLUDE_SCHEMA_CHANGES", "true"))
                .with("schema.include.list", EnvUtils.getEnv("CDC_SCHEMA_INCLUDE_LIST", "public"))
                .with("table.include.list", EnvUtils.getEnv("CDC_TABLE_INCLUDE_LIST", "public.processed_data"))
                .with("plugin.name", EnvUtils.getEnv("CDC_PLUGIN_NAME", "pgoutput"))
                .with("slot.name", EnvUtils.getEnv("CDC_SLOT_NAME", "xtrmetl_slot"))
                .with("publication.autocreate.mode", EnvUtils.getEnv("CDC_PUBLICATION_AUTOCREATE_MODE", "filtered"))
                .with("publication.name", EnvUtils.getEnv("CDC_PUBLICATION_NAME", "xtrmetl_publication"))
                .with("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
                .with("offset.storage.file.filename", offsetFile)
                .with("offset.flush.interval.ms", EnvUtils.getEnv("CDC_OFFSET_FLUSH_INTERVAL_MS", "1000"))
                .with("schema.history.internal", "io.debezium.storage.file.history.FileSchemaHistory")
                .with("schema.history.internal.file.filename", schemaHistoryFile)
                .build();
    }

    private static Path defaultCdcDataDir() {
        String userHome = System.getProperty("user.home");
        Path baseDir = userHome == null || userHome.isBlank() ? Path.of(".") : Path.of(userHome);
        return baseDir.resolve(".xtrmetl").resolve("cdc");
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
