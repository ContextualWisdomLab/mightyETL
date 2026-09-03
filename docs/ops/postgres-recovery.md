# PostgreSQL logical backup and recovery boundary

Status: `active_pr` (#208). This document describes the bounded PostgreSQL logical-backup and disposable-target restore-rehearsal capability on this branch. It is not `implemented_on_develop` until protected integration.

## Purpose and scope

The local mightyETL PostgreSQL profile uses persistent storage, but a named Docker volume is not a backup. `scripts/ops/postgres-logical-backup.sh` creates a separate PostgreSQL custom-format logical archive plus a provenance manifest. `scripts/ops/postgres-logical-restore-rehearsal.sh` then provides a distinct fail-closed rehearsal that restores a verified bundle into an explicitly provisioned empty PostgreSQL database and checks bounded application invariants.

The branch therefore proves more than archive creation, but it still does **not** prove disaster recovery. Application startup/readiness, destructive-loss replacement, representative durable-row recovery, Kafka/Debezium/DLT reconciliation, external-target compensation, PITR, and measured RPO/RTO remain outside the current evidence boundary.

## Backup prerequisites

Use PostgreSQL client tooling compatible with the database server and provide ordinary libpq connection settings without writing credentials into source control:

- `PGHOST`
- `PGPORT`
- `PGDATABASE`
- `PGUSER`
- `PGPASSWORD` or another deployment-approved libpq authentication mechanism
- `BACKUP_DIRECTORY`, an operator-controlled destination
- `APPLICATION_SOURCE_SHA`, the exact 40-character mightyETL Git commit represented by the running deployment

The backup script creates files under `umask 077`. The operator remains responsible for storage encryption, host and object-store access control, retention, replication, deletion, and custody of database credentials. Do not place `PGPASSWORD` in command histories, logs, manifests, or committed configuration.

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

## Backup artifact and provenance contract

The backup command creates a private temporary directory inside `BACKUP_DIRECTORY`, writes a PostgreSQL custom-format archive with `pg_dump --format=custom`, and requires `pg_restore --list` to read the completed archive before publication. The companion `manifest.txt` records:

- manifest version;
- `application_source_sha`;
- UTC creation time;
- PostgreSQL `server_version_num`;
- latest successful `flyway_schema_history` migration version;
- `backup_sha256` for `database.dump`.

Archive and manifest are published together only after archive verification and metadata queries succeed. The command reserves the final bundle identity before dump work so concurrent invocations cannot silently replace the same timestamp/source identity. Publication is a same-filesystem directory rename and never intentionally replaces an existing final bundle.

The manifest is part of the evidence and must not authenticate itself. Before a restore rehearsal, independently record and protect both:

1. the SHA-256 of `database.dump` as `EXPECTED_BACKUP_SHA256`; and
2. the SHA-256 of `manifest.txt` as `EXPECTED_MANIFEST_SHA256`.

Those digests must be kept outside the mutable backup bundle in the deployment's approved recovery-evidence channel. Computing a digest only after accepting an untrusted transferred bundle is not independent provenance.

## Disposable-target restore prerequisites

`postgres-logical-restore-rehearsal.sh` requires all recovery authority to be explicit:

- `BACKUP_BUNDLE`, the verified bundle directory;
- `EXPECTED_APPLICATION_SOURCE_SHA`, the expected 40-character source revision;
- `EXPECTED_BACKUP_SHA256`, the independently recorded archive digest;
- `EXPECTED_MANIFEST_SHA256`, the independently recorded manifest digest;
- `RECOVERY_PGHOST`;
- `RECOVERY_PGPORT`;
- `RECOVERY_PGDATABASE`, an explicitly provisioned empty database;
- `RECOVERY_PGUSER`;
- a deployment-approved libpq authentication mechanism such as `PGPASSWORD`, without printing or committing the credential.

The rehearsal rejects symlinked archive/manifest files. It authenticates `manifest.txt` against `EXPECTED_MANIFEST_SHA256` **before** parsing provenance fields, then requires the manifest source SHA and archive digest to match the independently supplied expected values. It recomputes the archive SHA-256 and requires `pg_restore --list` to parse the custom archive before any target write.

## Restore safety and compatibility boundary

Before restore, the command:

1. reads the recovery server's `server_version_num` and requires the same PostgreSQL major version as the backup provenance;
2. queries the target catalog and requires zero non-system relations of the governed relation kinds;
3. never uses `--clean`, `dropdb`, or `createdb`; the operator must deliberately provision the disposable empty target.

The database-writing restore uses:

```text
pg_restore --exit-on-error --single-transaction --no-owner --no-privileges
```

`--single-transaction` makes the restore all-or-nothing for PostgreSQL command failures: an error rolls back the complete restore instead of accepting earlier committed objects as a partially restored application state. `--no-owner` and `--no-privileges` avoid importing archived ownership/grants into the disposable target; they do not establish the final production authorization model.

PostgreSQL's current version 18 guidance explicitly documents `-1` / `--single-transaction` for whole-dump restore and notes that even a small error rolls back the entire restore. This branch intentionally chooses that behavior for the bounded rehearsal because accepting a partial application restore would be a false recovery success.

## Post-restore application invariants

A successful `pg_restore` is not by itself accepted. The rehearsal then requires:

- the latest successful `flyway_schema_history` version to equal the version recorded in the authenticated manifest;
- `public.processed_data` to exist;
- `public.etl_idempotency_records` to exist;
- `public.etl_job_records` to exist.

These are structural application invariants only. They do not yet prove representative row contents, durable-job ownership/lineage, idempotency replay behavior, service startup, health/readiness, or application transactions against the restored database.

## Backup is not restore

**Backup is not restore.** A successful `pg_dump`, `pg_restore --list`, and digest check establishes a verified backup artifact. The separate restore-rehearsal command adds bounded clean-target restore evidence, but neither artifact alone establishes complete disaster recovery.

Current evidence state on this active PR:

- verified logical backup artifact: proven by repository contracts;
- independently bound archive and manifest integrity inputs: proven by repository contracts;
- disposable clean-target restore: proven by the bounded restore-rehearsal contract;
- restore command-failure atomicity via `--single-transaction`: proven by the bounded restore-rehearsal contract;
- Flyway migration identity after restore: proven by the bounded restore-rehearsal contract;
- required application relation presence after restore: proven by the bounded restore-rehearsal contract;
- representative durable-job/idempotency row invariants: not yet proven;
- application startup/readiness against the restored database: not yet proven;
- destructive-loss replacement: not yet proven;
- end-to-end CDC/external-target recovery: not yet proven;
- RPO: not measured;
- RTO: not measured.

Do not advertise an RPO or RTO until a repeatable recovery rehearsal measures it from an explicitly defined failure point and workload.

## Recovery-domain boundaries

A PostgreSQL logical restore cannot by itself rewind or reconcile every mightyETL side effect. A full #188 recovery rehearsal must treat these as separate authorities:

- **Kafka:** broker topics, consumer groups, offsets, retained records, and acknowledged publications are outside a PostgreSQL dump.
- **Debezium:** connector offset and schema-history state may live outside the restored application database and must be reconciled against the chosen recovery point.
- **DLT:** dead-letter records, retention, deletion, and redrive authorization are broker/data-governance state, not PostgreSQL backup contents.
- **external target:** warehouse, BI, JDBC, or other external target writes are not rolled back by restoring PostgreSQL. Recovery must use proven idempotency, reconciliation, or compensation for each target boundary.

A database-only restore must therefore never be described as end-to-end exactly-once recovery.

## Next recovery acceptance increment

The next bounded #188 work should extend the disposable rehearsal without touching production data and prove, in order:

1. realistic PostgreSQL backup and restore execution against an ephemeral supported server rather than source-contract inspection alone;
2. representative durable ETL/idempotency data invariants and immutable lifecycle evidence after restore;
3. application startup/readiness and a bounded read/write smoke path against the restored database;
4. deliberate destructive-loss/replacement procedure with retry/incident handling that preserves the last known good backup;
5. documented and tested Kafka, Debezium, DLT, and external-target divergence/reconciliation boundaries;
6. elapsed recovery measurements before any profile-specific RTO/RPO claim.

Rollback of this repository recovery tooling means disabling/removing the operator commands and documentation, not deleting previously created recovery evidence. Existing archives remain sensitive operational artifacts and must follow the operator's retention/destruction policy.

## References

PostgreSQL Global Development Group. (2026). *pg_dump (PostgreSQL 18 documentation)*. https://www.postgresql.org/docs/18/app-pgdump.html

PostgreSQL Global Development Group. (2026). *pg_restore (PostgreSQL 18 documentation)*. https://www.postgresql.org/docs/18/app-pgrestore.html

PostgreSQL Global Development Group. (2026). *Populating a database (PostgreSQL 18 documentation)*. https://www.postgresql.org/docs/18/populate.html

PostgreSQL Global Development Group. (2026). *Backup and restore (PostgreSQL 18 documentation)*. https://www.postgresql.org/docs/18/backup.html
