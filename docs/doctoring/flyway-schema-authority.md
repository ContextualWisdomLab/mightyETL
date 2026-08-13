# Flyway production schema authority doctoring

**Capability status:** `active_pr` via #324; not `implemented_on_develop` until protected integration.  
**Protected baseline assessed:** `develop@b50ba3156bbcbfdc12f3125dec0d34201c07bd8f`  
**Application baseline:** Spring Boot 3.5.16  
**Primary-source revalidation:** 2026-08-13

## Decision

The deployable ETL service has one production schema-mutation authority: reviewed, versioned Flyway migrations. Hibernate remains available, but `spring.jpa.hibernate.ddl-auto` is `none` and must not create, update, or drop production schema objects alongside Flyway.

The maintained Spring Boot database-initialization guidance recommends one schema-generation mechanism and says higher-level migration tools such as Flyway should be used alone for schema initialization. The protected baseline violated that boundary by combining Flyway with `ddl-auto: update`; #324 changes only the Hibernate mutation setting while retaining the Flyway migration contract.

## Migration integrity

Flyway's schema history table records applied migrations, including checksums and execution state. Flyway Validate compares resolved migrations with that schema history, so checksum or migration-identity drift is review evidence rather than a reason to rewrite applied history. mightyETL therefore requires new owned schema changes to use new versioned migrations and requires PostgreSQL-realistic tests whenever locking, concurrent indexes, triggers, constraints, transactions, or catalog semantics are material.

`validate-migration-naming: true`, `clean-disabled: true`, and `baseline-on-migrate: ${FLYWAY_BASELINE_ON_MIGRATE:false}` remain fail-closed controls. Baseline enablement requires an explicit lineage decision for the pre-existing schema. Flyway clean is not a rollback mechanism for customer data.

Redgate's current Repair documentation states that repair can realign checksums, descriptions, and types and mark missing migrations as deleted. Therefore repair must not be used to conceal unexplained drift. Any use requires RCA, proof of intended migration contents, recovery planning, and auditable operator authorization.

## Bootstrap, test, and MSA boundaries

Docker/PostgreSQL clean-install bootstrap and explicit compatibility SQL are lifecycle-specific artifacts, not a second runtime ETL schema authority. Once the ETL service owns an object through Flyway, later service evolution must remain on that ordered Flyway path. Test-owned disposable schemas may use test setup, but they do not authorize production Hibernate mutation.

The rule is service-owned whether mightyETL runs standalone or inside its modular MSA. Composition may coordinate deployment order, but no gateway, discovery server, central repository, or orchestrator becomes the ETL database migration authority. Shared PostgreSQL deployments must retain explicit schema/object ownership.

## Rollback and forward recovery

Production recovery must preserve database and schema-history evidence, establish whether a migration committed or partially executed, and then use an approved verified restore or a new compensating versioned migration for forward recovery. Editing an already-applied migration or re-enabling Hibernate `update` is not rollback. Irreversible data transformations, destructive DDL, external side effects, and nontransactional PostgreSQL operations require explicit compatibility and recovery decisions.

Migration diagnostics must not expose business payloads, credentials, raw principals, sensitive SQL literals, or unrestricted provider/database exception text. Purpose-bound privileged evidence requires explicit access control, retention, and audit policy.

Canonical PRD/TRD/Architecture/ADR/ERD/Test Strategy/Operability/Traceability reconciliation remains owned by #149/#159 while that lane is active. Those authorities must treat #183/#324 as `active_pr` until protected integration.

## Machine-checkable contract

`FlywaySchemaAuthorityTest` binds this doctoring to deployable configuration. It fails if production re-enables Hibernate `update`, `create`, or `create-drop`, if Flyway safety defaults disappear, or if this source-backed schema-history, checksum, `baseline-on-migrate`, and forward recovery evidence disappears.

## References (APA 7)

Spring Boot. (2026). *Database initialization*. https://docs.spring.io/spring-boot/how-to/data-initialization.html

Redgate. (2026, July 20). *Validate*. https://documentation.red-gate.com/flyway/reference/commands/validate

Redgate. (2025, January 16). *Flyway schema history table*. https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table

Redgate. (2026, July 20). *Repair*. https://documentation.red-gate.com/flyway/reference/commands/repair

These primary sources were revalidated on 2026-08-13. The maintained Spring Boot guidance still recommends a single schema-generation mechanism, and current Flyway documentation continues to define checksum-based validation and explicit schema-history repair semantics.
