# Databricks connector (scaffold)

## Intent

Load CDC/ETL change batches into Databricks Unity Catalog tables (Delta Lake) for analytics.

## Status

| Item | State |
|:-----|:------|
| Product claim | **Not supported in runtime** |
| Docs + SPI stub | Yes — `DatabricksTargetConnector` |
| JDBC/SQL warehouse driver wiring | No |
| Autoloader / structured streaming sink | No |

## Planned integration shape

1. Consume normalized change records from Kafka topics (`xtrmetl-cdc.{schema}.{table}` today).
2. Map to Delta table columns (schema registry optional later).
3. Batch upsert via Databricks SQL Statement Execution API or JDBC (`com.databricks:databricks-jdbc`).

## Config keys (proposed)

| Key | Description |
|:----|:------------|
| `host` | Workspace hostname |
| `http-path` | SQL warehouse HTTP path |
| `token` | PAT or OAuth token (**secret**) |
| `catalog` / `schema` / `table` | Unity Catalog target |
| `write-mode` | `append` \| `merge` (merge needs keys) |

## Limitations (honest)

- No credentials handling, no driver on classpath, no integration tests.
- Do not enable in production until write path and secret management land.
