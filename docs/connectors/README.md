# Target connectors (scaffold + managed lifecycle)

mightyETL aims to load transformed change events into analytics and BI systems.
**Today:** the production load path is PostgreSQL (`etl-service` → JDBC) plus optional CDC replica apply to Postgres.

Java SPI lives under:

- `etl-service/src/main/java/com/xtrmetl/etl/connector/`

## Support matrix

| Target | Status | Runtime | Docs |
|:-------|:-------|:--------|:-----|
| PostgreSQL | **Supported** (primary ETL + optional CDC replica) | JDBC | README / ARCHITECTURE |
| Kafka | **Supported** as CDC event bus (not a warehouse loader) | Spring Kafka | `cdc-service` |
| Databricks | **Scaffold** — SPI, YAML binding, required-key validation, catalog API; **write refused** | no driver | [databricks.md](databricks.md) |
| Snowflake | **Scaffold** — SPI, YAML binding, required-key validation, catalog API; **write refused** | no driver | [snowflake.md](snowflake.md) |
| Qlik Sense | **Scaffold** — SPI, YAML binding, required-key validation, catalog API; **write refused** | no REST client | [qlik-sense.md](qlik-sense.md) |

Do **not** market Databricks / Snowflake / Qlik as production-supported until `status=SUPPORTED` and CI exercises a real write path.

## SPI overview

```text
TargetConnector
  ├── id(), displayName(), status()
  ├── requiredConfigKeys() / optionalConfigKeys()
  ├── writeRefusalReason() / describeIntegration()
  ├── validate(config)   // fails on missing required keys
  ├── open(config)       // establishes resources for SUPPORTED connectors
  ├── write(batch)       // scaffold/unsupported implementations must throw
  └── close()            // releases resources during orderly shutdown
```

`TargetConnectorDispatcher` owns the runtime lifecycle contract:

1. Disabled connectors fail before configuration or external-resource access.
2. Scaffold and unsupported connectors validate configuration, then fail closed without `open()` or `write()`.
3. Supported connectors open lazily before the first write and reuse that opened instance across batches.
4. Failed opens are not cached, so a later dispatch can retry.
5. Shutdown waits for in-flight writes, closes each opened connector once, and rejects later dispatches.

The lifecycle is ready for live implementations, but the three external connectors listed above remain scaffolds.

Catalog: `GET /api/etl/connectors` (`product=mightyETL`, `primaryLoadPath=postgresql`). Each connector row includes:

- `status`, `enabled`, and `writable` capability state
- `opened`, the current runtime resource state
- required/optional configuration key names without secret values
- integration metadata and a write-refusal reason

## Config surface (etl-service)

Keys bind under `xtrmetl.connectors.*` (dual-read: prefer `mightyetl.connectors.*` for enable flags).
All connectors are `enabled: false` by default. Enabling a scaffold flag **does not** activate a loader — validation runs, then dispatch is refused without opening an external resource.

```yaml
xtrmetl:
  connectors:
    databricks:
      enabled: false
      host: ...
      http-path: ...
      token: ...          # secret
      catalog: ...
      schema: ...
      table: ...
      write-mode: append
    snowflake:
      enabled: false
      account: ...
      warehouse: ...
      database: ...
      schema: ...
      user: ...
      password: ...       # secret (or private-key)
      table: ...
    qlik-sense:
      enabled: false
      tenant-url: ...
      api-key: ...        # secret
      app-id: ...
      mode: reload-only
```

## What remains out of scope

- Live JDBC/SDK clients, OAuth/token refresh, bulk/COPY loaders
- Guaranteed exactly-once delivery to external SaaS
- Claiming “production supported” without credentials and green integration tests
