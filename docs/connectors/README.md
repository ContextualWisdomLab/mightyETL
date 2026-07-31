# Target connectors (scaffold)

mightyETL aims to load transformed change events into analytics and BI systems.
**Today:** production load path is PostgreSQL (`etl-service` → JDBC) plus optional CDC replica apply to Postgres.

This directory documents **honest scaffolds** for additional targets. Java SPI stubs live under:

- `etl-service/src/main/java/com/xtrmetl/etl/connector/`

## Support matrix

| Target | Status | Runtime | Docs |
|:-------|:-------|:--------|:-----|
| PostgreSQL | **Supported** (primary ETL + optional CDC replica) | JDBC | README / ARCHITECTURE |
| Kafka | **Supported** as CDC event bus (not a warehouse loader) | Spring Kafka | `cdc-service` |
| Databricks | **Scaffold** — SPI + config keys only | not wired | [databricks.md](databricks.md) |
| Snowflake | **Scaffold** — SPI + config keys only | not wired | [snowflake.md](snowflake.md) |
| Qlik Sense | **Scaffold** — SPI + config keys only | not wired | [qlik-sense.md](qlik-sense.md) |

## SPI overview

```text
TargetConnector
  ├── id(), displayName()
  ├── validate(config)
  ├── open() / close()
  └── write(batch of ChangeRecords)   // not implemented for scaffold targets
```

Scaffold implementations return `ConnectorStatus.SCAFFOLD` and throw
`UnsupportedOperationException` on `write(...)` so they cannot be mistaken for production loaders.

## Config surface (etl-service)

Keys are declared in `etl-service/src/main/resources/application.yml` under `xtrmetl.connectors.*`
(all `enabled: false` by default). Enabling a flag **does not** activate a loader yet — SPI
`write()` still throws until a real client is implemented.

```yaml
xtrmetl:
  connectors:
    databricks:
      enabled: ${DATABRICKS_CONNECTOR_ENABLED:false}
    snowflake:
      enabled: ${SNOWFLAKE_CONNECTOR_ENABLED:false}
    qlik-sense:
      enabled: ${QLIK_CONNECTOR_ENABLED:false}
```

Legacy Spring prefix remains `xtrmetl.*` until dual-read (`mightyetl.*`) is added.

## What is out of scope of this scaffold

- OAuth/token management, retries, and bulk/COPY loaders for warehouses
- Qlik app reload orchestration beyond a documented API sketch
- Guaranteed exactly-once delivery to external SaaS
