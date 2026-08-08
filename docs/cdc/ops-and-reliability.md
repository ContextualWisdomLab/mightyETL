# CDC service — operations and reliability

Companion notes for `cdc-service`. Complements `ARCHITECTURE.md` and `README.md`.

## What runs in production path

1. **Capture:** Embedded Debezium `PostgresConnector` reads logical replication, publishes JSON to Kafka.
2. **Optional replica:** `CdcReplicaConsumer` applies Kafka events to a second Postgres.
   - Data path: tables listed in `xtrmetl.replica.tables` with `(id, data)` shape (default `processed_data`).
   - DDL path: off by default (`xtrmetl.replica.ddl-enabled`).

## Critical config

| Variable / property | Purpose | Default risk |
|:--------------------|:--------|:-------------|
| `CDC_AUTOSTART` / `xtrmetl.cdc.autostart` (or `mightyetl.cdc.autostart`) | Start engine on boot | `true` — ensure PG WAL slot capacity |
| `KAFKA_BOOTSTRAP_SERVERS` | Event bus | Required for useful CDC |
| `CDC_KAFKA_DELIVERY_TIMEOUT_MS` | Kafka producer delivery success/failure upper bound | `60000` ms — lower values fail faster under broker pressure |
| `CDC_KAFKA_MAX_BLOCK_MS` | Bound producer metadata/buffer blocking | `30000` ms — lower values can surface transient pressure sooner |
| `REPLICA_ENABLED` | Turn on Kafka→PG apply | `false` |
| `REPLICA_TOPIC_PATTERN` | Topics to consume | `xtrmetl-cdc\..*` |
| `REPLICA_TABLES` / `xtrmetl.replica.tables` | Tables for JDBC apply (`id`,`data` shape) | `processed_data` |
| `REPLICA_DDL_ENABLED` | Apply DDL from events | **Keep false** unless controlled |
| `CDC_CANONICAL_MAP_ENABLED` | Validate Debezium→canonical map (no payload change) | `false` |
| `CDC_SCHEMA_INCLUDE_LIST` / table include | Limit capture scope | Unbounded capture costs slots/disk |

Env helpers and validation live in `EnvUtils` / `ValidationUtils`.

## Source-to-Kafka acknowledgement boundary

The live embedded-engine path uses Debezium's batch `ChangeConsumer` / `RecordCommitter` contract. For every destination-bearing change event, mightyETL submits the raw Debezium JSON to `KafkaTemplate`, waits for the returned `CompletableFuture` to complete successfully, and only then calls `RecordCommitter.markProcessed(...)`. `markBatchFinished()` is called only after the complete batch has reached its permitted processed state.

Kafka publication uses a maximum of **two application attempts**. The first terminal send failure increments `kafkaPublishFailure` and is retried once. A second terminal failure raises `KafkaException`; the failed record is not marked processed and the batch is not marked finished. Synchronous producer-send failures use the same bounded policy. Interruption while waiting for acknowledgement propagates without advancing the current Debezium record.

The producer configuration fixes `acks=all`, enables producer idempotence, and limits `max.in.flight.requests.per.connection` to `5`. `CDC_KAFKA_DELIVERY_TIMEOUT_MS` and `CDC_KAFKA_MAX_BLOCK_MS` bound delivery and producer blocking respectively. These settings strengthen and bound the Kafka side of the contract; the explicit future wait is what binds Kafka completion to the Debezium source-progress decision.

`GET /api/cdc/status` exposes process-lifetime diagnostic counters:

- `kafkaPublishSuccess`: acknowledged sends on the live batch path;
- `kafkaPublishFailure`: failed application send attempts, including an attempt later recovered by the one bounded retry.

These counters contain no payloads, record keys, credentials, or exception text.

This boundary is **at-least-once/replay-tolerant rather than an end-to-end exactly-once claim**. A crash can occur after Kafka acknowledgement but before Debezium's file-backed source offset is durably flushed, so a source event may be replayed after restart. Downstream consumers must retain stable event identity or idempotent processing where duplicate effects matter. See `docs/doctoring/cdc-kafka-acknowledged-delivery.md` for the failure model, TDD evidence, rollback boundary, and primary references.

## Reliability features (real)

- Source publish: Kafka acknowledgement is observed before Debezium record progress; repeated terminal failure leaves the source record uncommitted.
- Replica consumer: Spring Kafka `DefaultErrorHandler` + **DLT** (`topic.DLT`) after retries.
- `AckMode.RECORD` after successful apply.
- Actuator: `health`, `info`, liveness/readiness probes enabled.
- **CDC engine health:** component `cdcEngine` on `/actuator/health` (running / idle / down + slot details).
- Status API: replication slot lag bytes via `ReplicationSlotProbe` plus `kafkaPublishSuccess` / `kafkaPublishFailure` source-publish counters.
- SPI: `postgres-debezium` start/stop delegates to `CdcService`.
- Zipkin tracing sampling configurable via Spring Boot actuator.

## Gaps operators must accept

| Gap | Impact | Mitigation |
|:----|:-------|:-----------|
| Source→Kafka is not one distributed transaction | Crash after Kafka ack but before durable Debezium offset flush can replay a record | Keep downstream consumers idempotent/replay-tolerant; monitor publish failures; see acknowledged-delivery doctoring |
| Slot lag is point-in-time probe | No historical lag graph | Scrape `/api/cdc/status` or use PG exporters |
| Replica apply limited to `(id,data)` tables | Arbitrary schemas not replicated | Configure `replica.tables` only for matching shapes; warehouse connectors later |
| One CDC process ownership | Duplicate processes fight for slots | Run single active instance per slot/publication |
| DDL vs DML ordering | Schema apply races | Keep DDL off; coordinate migrations |
| Defaults still branded `xtrmetl-*` | Confusing under mightyETL name | Override via env; see rebrand matrix |
| Multi-source YAML is declarative only | Extra sources not started | Factory validates types; engine still single PG path |
| Canonical Kafka payload not default | Consumers expect Debezium JSON | Keep raw publish until cutover |

## Control / status API

| Method | Path | Purpose |
|:-------|:-----|:--------|
| `GET` | `/api/cdc/status` | Engine flags, Kafka publish counters, **replication slot lag** (`restartLagBytes` / `flushLagBytes`), replica flags, configured/registered sources & targets |
| `GET` | `/api/cdc/sources` | Source SPI registry (Postgres Debezium today) |
| `GET` | `/api/cdc/targets` | Target SPI registry (`kafka`, `jdbc-replica`) |
| `POST` | `/api/cdc/start` | Start embedded Debezium engine |
| `POST` | `/api/cdc/stop` | Stop engine |

Status JSON includes `product: mightyETL`, `anyToAny: false`, `kafkaPublishSuccess`, `kafkaPublishFailure`, `replicationSlot`, and honest notes that capture is PostgreSQL→Kafka only.

### replicationSlot fields

| Field | Meaning |
|:------|:--------|
| `found` / `active` | Slot exists and is currently used |
| `restartLsn` / `confirmedFlushLsn` | Slot LSN positions (text) |
| `restartLagBytes` / `flushLagBytes` | `pg_wal_lsn_diff(pg_current_wal_lsn(), …)` |
| `available=false` | Probe failed (DB down / permissions); status API still 200 |

## Cheap health checklist

```bash
# Slot present and active (on primary)
psql -c "select slot_name, active, restart_lsn from pg_replication_slots;"

# CDC process + engine health component
curl -sf http://localhost:8001/actuator/health | jq .
curl -sf http://localhost:8001/actuator/health/cdcEngine | jq . 2>/dev/null || true

# Engine / config status (no passwords); inspect Kafka publish counters as well
curl -sf http://localhost:8001/api/cdc/status | jq .

# Registered CDC source types
curl -sf http://localhost:8001/api/cdc/sources | jq .

# Start/stop (no auth on API in default dev config — lock down in prod)
curl -X POST http://localhost:8001/api/cdc/start
curl -X POST http://localhost:8001/api/cdc/stop
```

If `kafkaPublishFailure` rises, inspect broker reachability, topic authorization, producer/broker availability, and the configured delivery/block timeout bounds before restarting the engine. Do not treat a repeated retry as proof that the cause has been removed.

## Tests

Unit tests under `cdc-service/src/test/java/com/xtrmetl/cdc/**` cover controller, service lifecycle mocks, acknowledged Kafka publication before source progress, bounded terminal/synchronous publish failures, interruption, producer durability configuration, replica appliers, Kafka error handler config, and **ops-doc alignment** (`ops/CdcOpsDocsAlignmentTest` — asserts this file names the same paths/health component the shipped `CdcController` / `CdcEngineHealthIndicator` expose). No Testcontainers integration suite in-repo yet.

## Sale-ready honesty

Primary path is **PostgreSQL → Kafka** only. Its embedded source publisher now waits for Kafka acknowledgement before marking a Debezium record processed, but this does not make PostgreSQL, the file-backed Debezium offset store, Kafka, and downstream consumers one exactly-once transaction. Warehouse BI connectors and multi-source any-to-any CDC remain scaffolds (see README support matrix and `docs/connectors/`). Operators should treat unlisted capabilities as **not production-supported**.
