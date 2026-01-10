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

    @PostConstruct
    public void start() {
        if (this.debeziumEngine == null) {
            initializeDebeziumEngine();
        }
        this.executor.execute(Objects.requireNonNull(debeziumEngine, "Debezium engine"));
    }

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
