# ADR-0009: Schema Migration and Recovery Authority

**Status:** Accepted with known gaps  
**Date:** 2026-08-10

## Context

Protected `develop` currently combines checked-in Flyway migrations with JPA schema mutation configuration. That creates two possible production schema authorities and makes upgrade, rollback, support, and acquisition evidence ambiguous. Backup artifacts are also useful but do not by themselves prove destructive-loss recovery, application readiness, durable invariants, or reconciliation of Kafka, Debezium, dead-letter, and external target side effects.

PR #184 is the active path that disables production JPA auto-DDL. PR #208 is the active path that binds PostgreSQL logical backup and restore rehearsal to exact source, PostgreSQL, Flyway, and digest provenance. Neither active PR is shipped truth.

## Decision

1. **Flyway is the sole production schema-mutation authority.** JPA may validate or avoid schema generation, but it must not create, update, or silently repair production schema.
2. Checked-in migration history is immutable after protected release. A defect is corrected by a new forward migration and explicit recovery guidance, not by rewriting an already released migration.
3. Every owned schema change requires clean-install, supported upgrade, failure, recovery, and compatibility evidence. Rollback means a tested operational recovery path; it does not imply that every DDL operation has a mechanically safe down migration.
4. A PostgreSQL backup bundle must bind at least the exact application source revision, PostgreSQL version, Flyway migration level, archive digest, creation time, and tool version. Publication must be restrictive, collision-safe, and atomic.
5. Restore tooling validates manifest and archive before target writes, refuses unsafe target state, avoids uncontrolled owner/privilege restoration, and re-verifies the expected migration identity after restore.
6. A backup or successful `pg_restore` is not disaster-recovery attainment. Recovery acceptance additionally requires destructive-loss replacement rehearsal, application startup/readiness, representative durable ETL/idempotency invariants, and explicit reconciliation or isolation of Kafka, Debezium, dead-letter, and external connector effects.
7. RPO and RTO remain `not measured` until measured on a documented production-like profile. Targets and observed attainment are separate evidence.
8. Database, broker, object-storage, and external warehouse recovery domains remain separate unless an accepted design and executable evidence prove a shared atomic or compensating boundary.

## Consequences

- Production schema ownership becomes auditable and reproducible.
- Application startup cannot silently mutate an operator-owned database.
- Forward migrations and recovery procedures require more deliberate design than `ddl-auto=update`.
- Backup bundles become provenance-bearing recovery inputs rather than informal files.
- Whole-system recovery remains a product/operability program and cannot be claimed from one database script.

## Alternatives rejected

- **JPA and Flyway both mutate production schema:** ambiguous ordering and drift authority.
- **rewrite an old migration:** destroys released-history reproducibility.
- **always provide reverse DDL:** unsafe or impossible for lossy transformations.
- **call a volume or dump file a backup-and-recovery solution:** conflates artifact existence with tested restoration.
- **invent RPO/RTO from configuration:** replaces measurement with assertion.

## Failure and recovery

A failed migration leaves the database in the state defined by PostgreSQL/Flyway transaction semantics and the migration runbook. Operators stop dependent writes, preserve evidence, diagnose the exact migration boundary, restore from a verified artifact or apply an approved forward repair, and rerun invariant checks. External effects are not declared rolled back unless their owning system proves it.

## Security and data-governance impact

Backup archives and manifests can contain customer data, schema details, pseudonymous identifiers, and operational metadata. They require purpose-bound access, encryption in transit and at rest, bounded retention, auditable export, secure deletion, and tenant/deployment scope consistent with ADR-0014 once accepted.

## Migration and compatibility

PR #184 and PR #208 remain `active_pr`. Existing installations must inventory current JPA/Flyway drift before enforcing the new authority. Compatibility aliases or repair migrations require explicit evidence; automatic destructive normalization is prohibited.

## Acceptance evidence

- configuration contract proving production JPA schema mutation is disabled;
- clean-install and supported upgrade migration tests;
- exact migration/index/constraint assertions;
- backup manifest, archive digest, collision, permission, and failure tests;
- clean-target restore rehearsal and post-restore migration/invariant verification;
- destructive-loss application readiness rehearsal;
- external-side-effect recovery classification;
- exact-source CI, complete security evidence, non-vacuous coverage, review, and protected-develop operational proof.

## Supersession

Supersede this ADR only if mightyETL adopts another single, versioned schema authority with equivalent migration, recovery, provenance, and compatibility evidence. A framework default or operator convention is not sufficient.