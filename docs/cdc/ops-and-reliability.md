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
| `REPLICA_ENABLED` | Turn on Kafka→PG apply | `false` |
| `REPLICA_TOPIC_PATTERN` | Topics to consume | `xtrmetl-cdc\..*` |
| `REPLICA_TABLES` / `xtrmetl.replica.tables` | Tables for JDBC apply (`id`,`data` shape) | `processed_data` |
| `REPLICA_DDL_ENABLED` | Apply DDL from events | **Keep false** unless controlled |
| `CDC_CANONICAL_MAP_ENABLED` | Validate Debezium→canonical map (no payload change) | `false` |
| `CDC_SCHEMA_INCLUDE_LIST` / table include | Limit capture scope | Unbounded capture costs slots/disk |

Env helpers and validation live in `EnvUtils` / `ValidationUtils`.

## Reliability features (real)

- Replica consumer: Spring Kafka `DefaultErrorHandler` + **DLT** (`topic.DLT`) after retries.
- `AckMode.RECORD` after successful apply.
- Actuator: `health`, `info`, liveness/readiness probes enabled.
- **CDC engine health:** component `cdcEngine` on `/actuator/health` (running / idle / down + slot details).
- Status API: replication slot lag bytes via `ReplicationSlotProbe`.
- SPI: `postgres-debezium` start/stop delegates to `CdcService`.
- Zipkin tracing sampling configurable via Spring Boot actuator.

## Gaps operators must accept

| Gap | Impact | Mitigation |
|:----|:-------|:-----------|
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
| `GET` | `/api/cdc/status` | Engine flags, **replication slot lag** (`restartLagBytes` / `flushLagBytes`), replica flags, configured/registered sources & targets |
| `GET` | `/api/cdc/sources` | Source SPI registry (Postgres Debezium today) |
| `GET` | `/api/cdc/targets` | Target SPI registry (`kafka`, `jdbc-replica`) |
| `POST` | `/api/cdc/start` | Start embedded Debezium engine |
| `POST` | `/api/cdc/stop` | Stop engine |

Status JSON includes `product: mightyETL`, `anyToAny: false`, `replicationSlot`, and honest notes that capture is PostgreSQL→Kafka only.

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

# Engine / config status (no passwords)
curl -sf http://localhost:8001/api/cdc/status | jq .

# Registered CDC source types
curl -sf http://localhost:8001/api/cdc/sources | jq .

# Start/stop (no auth on API in default dev config — lock down in prod)
curl -X POST http://localhost:8001/api/cdc/start
curl -X POST http://localhost:8001/api/cdc/stop
```

## Tests

Unit tests under `cdc-service/src/test/java/com/xtrmetl/cdc/**` cover controller, service lifecycle mocks, replica appliers, Kafka error handler config, and **ops-doc alignment** (`ops/CdcOpsDocsAlignmentTest` — asserts this file names the same paths/health component the shipped `CdcController` / `CdcEngineHealthIndicator` expose). No Testcontainers integration suite in-repo yet.

## Sale-ready honesty

Primary path is **PostgreSQL → Kafka** only. Warehouse BI connectors and multi-source any-to-any CDC remain scaffolds (see README support matrix and `docs/connectors/`). Operators should treat unlisted capabilities as **not production-supported**.
