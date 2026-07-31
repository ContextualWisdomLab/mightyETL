# Databricks connector (scaffold)

## Intent

Load CDC/ETL change batches into Databricks Unity Catalog tables (Delta Lake) for analytics.

## Status

| Item | State |
|:-----|:------|
| Product claim | **Not supported in runtime** (`ConnectorStatus.SCAFFOLD`) |
| SPI class | `DatabricksTargetConnector` |
| Config binding (`xtrmetl.connectors.databricks.*`) | Yes |
| Required-key validation | Yes (host, http-path, token, catalog, schema, table) |
| Catalog API fields | Yes (`GET /api/etl/connectors`) |
| JDBC/SQL warehouse driver on classpath | **No** |
| Live write path | **Refused** (`UnsupportedOperationException`) |

## Planned integration shape

1. Consume normalized change records from Kafka topics (`xtrmetl-cdc.{schema}.{table}` today).
2. Map to Delta table columns (schema registry optional later).
3. Batch upsert via Databricks SQL Statement Execution API or JDBC (`com.databricks:databricks-jdbc`).

## Config keys

| Key | Required | Description |
|:----|:--------:|:------------|
| `host` | yes | Workspace hostname |
| `http-path` | yes | SQL warehouse HTTP path |
| `token` | yes | PAT or OAuth token (**secret**) |
| `catalog` / `schema` / `table` | yes | Unity Catalog target |
| `write-mode` | no | `append` \| `merge` (merge needs keys) |

## Limitations (honest)

- No credentials handling beyond property binding, no driver on classpath, no network client in CI.
- Do not enable in production until write path and secret management land.
