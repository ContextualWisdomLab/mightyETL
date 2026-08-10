# PostgreSQL logical backup and recovery boundary

Status: `active_pr` (#208). This document describes the bounded logical-backup capability on this branch. It is not `implemented_on_develop` until protected integration.

## Purpose and scope

The local mightyETL PostgreSQL profile uses persistent storage, but a named Docker volume is not a backup. `scripts/ops/postgres-logical-backup.sh` creates a separate PostgreSQL logical archive plus a provenance manifest so an operator can preserve database state independently of the live volume.

This first recovery increment proves **backup artifact creation and verification only**. It does not yet prove a clean restore, destructive-loss recovery, service restart, end-to-end CDC recovery, or a measured recovery objective.

## Prerequisites

Use PostgreSQL client tooling compatible with the database server and provide ordinary libpq connection settings without writing credentials into source control:

- `PGHOST`
- `PGPORT`
- `PGDATABASE`
- `PGUSER`
- `PGPASSWORD` or another deployment-approved libpq authentication mechanism
- `BACKUP_DIRECTORY`, an operator-controlled destination
- `APPLICATION_SOURCE_SHA`, the exact 40-character mightyETL Git commit represented by the running deployment

The script creates files under `umask 077`. The operator remains responsible for storage encryption, host and object-store access control, retention, replication, deletion, and custody of database credentials. Do not place `PGPASSWORD` in command histories, logs, manifests, or committed configuration.

Example for a controlled local environment:

```bash
export BACKUP_DIRECTORY="$HOME/mightyetl-backups"
export APPLICATION_SOURCE_SHA="$(git rev-parse HEAD)"
export PGHOST="127.0.0.1"
export PGPORT="5432"
export PGDATABASE="postgres"
export PGUSER="postgres"
# Supply PGPASSWORD through the deployment's approved secret mechanism.
./scripts/ops/postgres-logical-backup.sh
```

Do not substitute a guessed SHA. In a packaged or remote deployment, obtain `APPLICATION_SOURCE_SHA` from the exact deployed release/provenance record rather than whichever checkout happens to be on an operator workstation.

## Artifact and verification contract

The command creates a private temporary directory inside `BACKUP_DIRECTORY`, writes a PostgreSQL custom-format archive with `pg_dump --format=custom`, and requires `pg_restore --list` to read the completed archive before publication. PostgreSQL documents the custom archive as a format intended for `pg_restore` and suitable for selective/reordered restore operations. The structural check proves that the artifact is readable as a PostgreSQL archive; it does **not** prove that restoring it into a clean server will satisfy mightyETL recovery invariants. 

The companion `manifest.txt` records:

- manifest version;
- `application_source_sha`;
- UTC creation time;
- PostgreSQL `server_version_num`;
- latest successful `flyway_schema_history` migration version;
- `backup_sha256` for `database.dump`.

Archive and manifest are published together only after the archive check and metadata queries succeed. The final bundle is never intentionally replaced by the command. Before moving or restoring an archive, recompute SHA-256 and require equality with `backup_sha256`.

## Backup is not restore

**Backup is not restore.** A successful `pg_dump`, `pg_restore --list`, and digest check establishes a verified backup artifact, not disaster-recovery success.

Current evidence state:

- RPO: not measured
- RTO: not measured
- clean-target restore: not yet proven
- destructive-loss replacement: not yet proven
- application restart after restore: not yet proven
- restored durable-job/idempotency invariants: not yet proven

Do not advertise an RPO or RTO until a repeatable recovery rehearsal measures it from an explicitly defined failure point and workload.

## Recovery-domain boundaries

A PostgreSQL logical restore cannot by itself rewind or reconcile every mightyETL side effect. A full #188 recovery rehearsal must treat these as separate authorities:

- **Kafka:** broker topics, consumer groups, offsets, retained records, and acknowledged publications are outside a PostgreSQL dump.
- **Debezium:** connector offset and schema-history state may live outside the restored application database and must be reconciled against the chosen recovery point.
- **DLT:** dead-letter records, retention, deletion, and redrive authorization are broker/data-governance state, not PostgreSQL backup contents.
- **external target:** warehouse, BI, JDBC, or other external target writes are not rolled back by restoring PostgreSQL. Recovery must use proven idempotency, reconciliation, or compensation for each target boundary.

A database-only restore must therefore never be described as end-to-end exactly-once recovery.

## Next recovery acceptance increment

The next bounded #188 increment should create a disposable clean PostgreSQL target and prove, without touching a production database:

1. manifest and SHA-256 verification before restore;
2. restore from the custom archive using `pg_restore`;
3. PostgreSQL and Flyway schema/migration identity after restore;
4. representative durable ETL/idempotency data invariants;
5. application startup/readiness against the restored database;
6. deliberate destructive-loss and replacement procedure;
7. documented treatment of Kafka, Debezium, DLT, and external-target divergence;
8. measured elapsed recovery evidence before any RTO claim.

Rollback of this backup feature means disabling/removing the operator command and its documentation, not deleting previously created recovery evidence. Existing archives remain sensitive operational artifacts and must follow the operator's retention/destruction policy.

## References

PostgreSQL Global Development Group. (2026). *pg_dump (PostgreSQL 18 documentation)*. https://www.postgresql.org/docs/18/app-pgdump.html

PostgreSQL Global Development Group. (2026). *pg_restore (PostgreSQL 18 documentation)*. https://www.postgresql.org/docs/18/app-pgrestore.html

PostgreSQL Global Development Group. (2026). *Backup and restore (PostgreSQL 18 documentation)*. https://www.postgresql.org/docs/18/backup.html
