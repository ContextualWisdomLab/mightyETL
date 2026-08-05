# Durable-job claim index rollout

## Purpose

The durable worker queries `etl_job_records` for the oldest eligible `PENDING` row or expired
`RUNNING` row. The descriptive `etl_job_claim_eligibility_index` supports that queue-like access
path without changing the lease-fencing state machine.

The table can already receive job submissions when this index is introduced. PostgreSQL's ordinary
`CREATE INDEX` permits reads but blocks `INSERT`, `UPDATE`, and `DELETE` until the build completes.
For a production ETL control plane that write outage is not acceptable. Migration
`V4__add_etl_job_claim_eligibility_index.sql` therefore uses `CREATE INDEX CONCURRENTLY`.

## Flyway execution boundary

PostgreSQL rejects `CREATE INDEX CONCURRENTLY` inside a transaction block. The companion script
configuration file
`V4__add_etl_job_claim_eligibility_index.sql.conf` contains:

```properties
executeInTransaction=false
```

Flyway's PostgreSQL transactional advisory lock is also disabled with:

```properties
spring.flyway.postgresql.transactional-lock=false
```

This causes Flyway to use the PostgreSQL integration's non-transactional lock mode required for
concurrent index DDL. The transactional V3 migration remains responsible only for lease columns,
legacy-data repair, and lifecycle constraints. Isolating the index in V4 prevents an index-build
failure from partially committing those schema invariants.

## Deployment procedure

1. Keep durable-job intake and worker execution disabled while validating the migration package.
2. Confirm no other concurrent index build or schema migration is active on `etl_job_records`.
3. Apply V3 and verify the lease columns and lifecycle constraints.
4. Apply V4 and monitor `pg_stat_progress_create_index`, database I/O, and transaction latency.
5. Verify `pg_index.indisvalid` is true for `etl_job_claim_eligibility_index`.
6. Run the claim-selection plan against production-equivalent data and confirm the expected index is
   available without forcing it through planner settings.
7. Enable one worker canary only after schema history, catalog state, and application health agree.

`CREATE INDEX CONCURRENTLY` performs more work and can take longer than a regular build. It preserves
normal writes, but it still adds CPU, memory, and I/O load and allows only one concurrent index build
per table.

## Failed migration and invalid-index recovery

A failed concurrent build can leave an **invalid index** in the PostgreSQL catalog. Do not mark the
Flyway migration successful merely because an index name exists.

Recovery is fail-closed:

1. Keep worker execution disabled and inspect the failed Flyway record plus `pg_index.indisvalid`.
2. Preserve database and migration logs under incident-response controls.
3. Correct the underlying resource, permission, duplicate-build, or transaction-mode cause.
4. Remove an unusable index without blocking normal table access:

   ```sql
   DROP INDEX CONCURRENTLY etl_job_claim_eligibility_index;
   ```

5. Use Flyway repair only after catalog inspection and operator approval, then rerun the unchanged,
   checksum-verified migration.
6. Reconfirm index validity and query-plan evidence before enabling workers.

Do not edit an applied versioned migration or create a same-name replacement with different SQL.

## Rollback boundary

Application rollback does not require removing the index; an unused valid index is compatible with
older binaries, although it adds write-maintenance overhead. Remove it only after every deployed
worker version no longer relies on it and a controlled change has verified the performance impact:

```sql
DROP INDEX CONCURRENTLY etl_job_claim_eligibility_index;
```

`DROP INDEX CONCURRENTLY` must also run outside a transaction block. Lease columns and constraints
require a later forward compensating migration after all compatible binaries have been removed; they
must not be rolled back by editing V3.

## Standards and primary documentation

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: Building indexes
concurrently*. https://www.postgresql.org/docs/18/sql-createindex.html#SQL-CREATEINDEX-CONCURRENTLY

Redgate Software. (2026). *Flyway script configuration*.
https://documentation.red-gate.com/flyway/reference/script-configuration

Redgate Software. (2026). *Flyway PostgreSQL transactional lock setting*.
https://documentation.red-gate.com/fd/flyway-postgresql-transactional-lock-setting-277579114.html
