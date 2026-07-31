# Target connectors (scaffold + config surface)

mightyETL aims to load transformed change events into analytics and BI systems.
**Today:** production load path is PostgreSQL (`etl-service` → JDBC) plus optional CDC replica apply to Postgres.

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
  ├── open(config)       // scaffold: validate only, no network
  └── write(batch)       // scaffold: always throws UnsupportedOperationException
```

Catalog: `GET /api/etl/connectors` (product=mightyETL, primaryLoadPath=postgresql).

## Config surface (etl-service)

Keys bind under `xtrmetl.connectors.*` (dual-read: prefer `mightyetl.connectors.*` for enable flags).
All `enabled: false` by default. Enabling a flag **does not** activate a loader — `write()` still throws after validation.

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

## What is out of scope of this scaffold

- Live JDBC/SDK clients, OAuth/token refresh, bulk/COPY loaders
- Guaranteed exactly-once delivery to external SaaS
- Claiming “production supported” without credentials + green integration tests
