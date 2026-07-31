# Snowflake connector (scaffold)

## Intent

Load CDC/ETL change batches into Snowflake tables for warehouse analytics.

## Status

| Item | State |
|:-----|:------|
| Product claim | **Not supported in runtime** |
| Docs + SPI stub | Yes — `SnowflakeTargetConnector` |
| Snowflake JDBC / Snowpipe | No |
| MERGE / stream-based CDC apply | No |

## Planned integration shape

1. Normalize change events (op, before, after, pk).
2. Stage via internal stage or Snowpipe Streaming (future choice).
3. `MERGE` into target tables for idempotent apply.

## Config keys (proposed)

| Key | Description |
|:----|:------------|
| `account` | Account locator |
| `warehouse` / `database` / `schema` | Compute + object path |
| `user` + `private-key` or `password` | Auth (**secret**) |
| `role` | Optional role |
| `table` | Target table |
| `merge-keys` | PK columns for MERGE |

## Limitations (honest)

- Scaffold only: no JDBC driver, no network client, no tests against Snowflake.
- Network egress, key rotation, and cost controls are operator responsibilities when implemented.
