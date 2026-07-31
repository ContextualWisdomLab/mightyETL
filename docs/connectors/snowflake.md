# Snowflake connector (scaffold)

## Intent

Load CDC/ETL change batches into Snowflake tables for warehouse analytics.

## Status

| Item | State |
|:-----|:------|
| Product claim | **Not supported in runtime** (`ConnectorStatus.SCAFFOLD`) |
| SPI class | `SnowflakeTargetConnector` |
| Config binding (`xtrmetl.connectors.snowflake.*`) | Yes |
| Required-key validation | Yes (account, warehouse, database, schema, user, table) |
| Catalog API fields | Yes (`GET /api/etl/connectors`) |
| Snowflake JDBC / Snowpipe | **No** |
| Live write path | **Refused** (`UnsupportedOperationException`) |

## Planned integration shape

1. Normalize change events (op, before, after, pk).
2. Stage via internal stage or Snowpipe Streaming (future choice).
3. `MERGE` into target tables for idempotent apply.

## Config keys

| Key | Required | Description |
|:----|:--------:|:------------|
| `account` | yes | Account locator |
| `warehouse` / `database` / `schema` | yes | Compute + object path |
| `user` | yes | Auth user |
| `password` or `private-key` | optional* | Auth (**secret**); required for a future live client |
| `role` | no | Optional role |
| `table` | yes | Target table |
| `merge-keys` | no | PK columns for MERGE |

\* Auth secrets are optional in the scaffold so unit tests can validate the structural keys without embedding credentials; a future SUPPORTED implementation will require one of them.

## Limitations (honest)

- Scaffold only: no JDBC driver, no network client, no tests against Snowflake cloud.
- Network egress, key rotation, and cost controls are operator responsibilities when implemented.
