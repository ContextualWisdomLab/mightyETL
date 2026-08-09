# Any-to-any CDC (design + scaffold)

## Honest current state

| Path | Status |
|:-----|:-------|
| PostgreSQL → Kafka (Debezium embedded) | **Supported** |
| Kafka → PostgreSQL replica apply (`processed_data` only) | **Supported** (optional) |
| MySQL / Oracle / SQL Server / Mongo sources | **Not implemented** |
| Generic source SPI | **Scaffold** (interfaces only) |
| Generic target SPI (beyond Postgres/Kafka) | **Scaffold** (see `docs/connectors/`) |

Product claim **“any-to-any CDC” is not true today**. This document defines the abstraction so support can grow without rewiring the whole service.

## Architecture target

```text
                    ┌─────────────────────┐
   Source SPI       │  CdcPipeline        │       Target SPI
  (capture)         │  normalize → route  │      (apply/publish)
                    └─────────────────────┘
         │                    │                    │
         v                    v                    v
  PostgresSource        ChangeRecord          KafkaTarget
  (Debezium today)      (canonical)           JdbcReplicaTarget
  MysqlSource (todo)                          DatabricksTarget (scaffold)
  ...                                         SnowflakeTarget (scaffold)
```

## Canonical event

Java type: `com.xtrmetl.cdc.spi.CanonicalChangeRecord`

```json
{
  "sourceId": "postgres-debezium",
  "op": "c|u|d|r",
  "schema": "public",
  "table": "orders",
  "tsEpochMs": 0,
  "before": {},
  "after": {},
  "pk": {}
}
```

**Mapper implemented:** `DebeziumChangeRecordMapper` converts Debezium key/value JSON → `CanonicalChangeRecord`.  
**Optional on hot path:** set `xtrmetl.cdc.canonical-map-enabled=true` (or `mightyetl.cdc.canonical-map-enabled`) to map each event for success/failure counters on `GET /api/cdc/status`.  
**Kafka payload unchanged:** live publish remains raw Debezium JSON (no consumer-breaking cutover).

## Source SPI (Java)

Package: `com.xtrmetl.cdc.spi`

- `CdcSourceConnector` — lifecycle + start/stop capture
- `SourceCapabilities` — declares engine (debezium/logminer/… ), databases supported
- `PostgresDebeziumCdcSource` — adapter wrapping existing `CdcService` configuration surface
- `CdcSourceRegistry` — discovery; exposed via `GET /api/cdc/sources` and status payload

## Target SPI (Java)

- `CdcTargetConnector` — batch write of canonical records
- `KafkaCdcTargetConnector` — documents live Kafka path (`scaffoldOnly=false`, write not SPI-wired)
- `JdbcReplicaCdcTargetConnector` — documents `processed_data` replica apply
- `CdcTargetRegistry` — in-process discovery

## Config (partially live)

```yaml
xtrmetl:
  cdc:
    sources:
      - id: pg-main
        type: postgres-debezium
        enabled: true
      # - id: mysql-1
      #   type: mysql-debezium   # scaffold only
      #   enabled: false
      # - id: sqlserver-1
      #   type: sqlserver-debezium # not registered — status reports unknown_source_type
      #   enabled: false
```

- **Declared** in `application.yml` / `XtrmetlProperties.Cdc.sources` and reported on `GET /api/cdc/status` as `configuredSources` via `CdcSourceFactory`.
- **Registered types:**

  | Type id | Status |
  |:--------|:-------|
  | `postgres-debezium` | **Live** — SPI `start`/`stop` delegates to `CdcService` |
  | `mysql-debezium` | Scaffold only (no connector JAR) |
  | `sqlserver-debezium` | **Not registered** — reference scaffold only; configured use reports `unknown_source_type` |

- `SqlServerDebeziumCdcSource` remains explicit design/reference code only. It is not a Spring component and is not production-discoverable until a maintained SQL Server Debezium implementation, lifecycle, offset/schema-history handling, recovery behavior, and realistic integration evidence exist.
- **Live engine:** still a single Debezium Postgres path in `CdcService` (multi-source start not implemented).
- Targets remain: Kafka (live raw publish) + optional JDBC replica (`replica.enabled`).

## Limitations accepted

- One embedded Debezium engine per `cdc-service` process (HA: one instance per table set).
- Replica DDL apply is opt-in and dangerous; see `docs/cdc/ops-and-reliability.md`.
- No schema registry; consumers must tolerate evolving Debezium envelopes.
