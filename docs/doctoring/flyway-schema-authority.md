# Flyway production schema authority doctoring

**Capability status:** `active_pr` via #324; not `implemented_on_develop` until protected integration.  
**Protected baseline assessed:** `develop@b50ba3156bbcbfdc12f3125dec0d34201c07bd8f`  
**Application baseline:** Spring Boot 3.5.16  
**Primary-source revalidation:** 2026-08-13

## Decision

The deployable ETL service has one production schema-mutation authority: reviewed, versioned Flyway migrations. Hibernate remains available as a framework dependency, but `spring.jpa.hibernate.ddl-auto` is `none` in deployable configuration and must not create, update, or drop production schema objects alongside Flyway.

This decision is intentionally narrower than removing JPA from the project. The current ETL persistence path does not establish an authoritative ORM entity model against which Hibernate `validate` would add useful schema protection. Choosing `validate` now could manufacture a false dependency on mappings that do not define the production schema. `none` therefore makes the authority boundary explicit without changing the application data model.

The maintained Spring Boot database-initialization guidance recommends one schema-generation mechanism and says higher-level migration tools such as Flyway should be used alone for schema initialization. The protected baseline violated that boundary by combining Flyway with `ddl-auto: update`; #324 changes only the Hibernate mutation setting while retaining the Flyway migration contract.

## Migration authority and evidence

Flyway's schema history table is the durable record of migration execution state. Versioned migrations have ordered identities and checksums. Flyway Validate compares resolved migrations with applied schema history, including checksum identity, so modifying an already-applied versioned migration is not an acceptable way to make a later environment appear current.

mightyETL therefore treats checked-in migration files as reviewed release artifacts:

- a new owned schema change requires a new versioned migration rather than startup-time ORM DDL;
- `validate-migration-naming: true` remains part of the repository naming control;
- Flyway validation or checksum failures are migration-drift evidence to investigate, not a prompt to edit history or suppress validation;
- migration ordering and descriptive multi-word `snake_case` database-object names remain reviewable source contracts;
- exact PostgreSQL rehearsal is required when a migration relies on PostgreSQL-specific transactional, locking, concurrent-index, constraint, trigger, or catalog behavior.

The schema history table is an authority record, not an operator convenience table to rewrite casually. Current Redgate Repair documentation states that repair can realign checksums, descriptions, and types and mark missing migrations as deleted. Repair must therefore not be used to conceal unexplained drift. Any repair requires explicit RCA, proof of the intended migration contents, backup/recovery planning, and auditable operator authorization.

## Baseline and clean policy

`baseline-on-migrate` remains fail-closed by default through `${FLYWAY_BASELINE_ON_MIGRATE:false}`. Baseline mode can be useful when bringing a pre-existing unmanaged schema under migration management, but enabling it broadly would let unknown schema history bypass normal initialization expectations. A deployment that needs baselining must first prove the existing schema lineage and choose the baseline version deliberately.

`clean-disabled: true` remains a production safety invariant. Flyway clean is not a rollback mechanism for customer data. Automated destructive reset belongs only in isolated ephemeral test environments with no route to production data.

## Bootstrap SQL versus runtime service migrations

The repository also contains Docker/PostgreSQL bootstrap SQL and compatibility artifacts. Those files are lifecycle-specific artifacts, not a second runtime ETL schema-mutation authority:

- clean-install bootstrap SQL establishes local container state before the service lifecycle;
- explicit compatibility artifacts are opt-in migration aids, not automatic service-startup mutations;
- once the ETL service owns a schema object through Flyway, subsequent service evolution must use the ordered Flyway path;
- bootstrap and compatibility scripts must not silently duplicate or override Flyway-owned migrations.

Issue #150 and its replacement/integration work separately own the legacy bootstrap-table retirement boundary. Durable-job migration work remains separate active work until protected integration. Evidence from one migration branch does not transfer to another after a head or base change.

## Test-only schema setup

Unit and integration tests may create isolated in-memory or disposable database state when the test explicitly owns that environment. Such test setup is not evidence that production may enable Hibernate DDL mutation. Database-realism tests must still use PostgreSQL when production semantics such as advisory locks, `FOR UPDATE SKIP LOCKED`, concurrent indexes, triggers, catalog predicates, or migration transaction behavior are material.

## Upgrade, rollback, and forward recovery

Production migration rollback is not represented as "turn Hibernate update back on" or "edit the already-applied SQL until it works." The preferred recovery model is:

1. stop or contain the affected rollout before additional incompatible writes occur;
2. preserve database, schema-history, deployment, and backup provenance evidence;
3. determine whether the migration committed, partially executed outside a transaction, or failed before mutation;
4. restore from a verified backup only when that is the approved recovery path and the data-loss boundary is understood; otherwise
5. use a new reviewed compensating/versioned migration for forward recovery;
6. validate schema history and migration checksums before resuming normal deployment.

A downgrade that crosses an irreversible data transformation, destructive DDL, external connector side effect, or nontransactional PostgreSQL operation requires an explicit compatibility decision. Application-version rollback alone is not proof of database rollback safety.

## Standalone and modular MSA implications

The schema-authority rule belongs to the ETL service whether it runs standalone or as part of the modular mightyETL MSA. Composition may coordinate rollout order, but no gateway, discovery server, central repository, or orchestrator becomes the ETL database migration authority. The ETL service's reviewed Flyway migrations remain authoritative for its owned persistence.

When multiple independently deployable services share a PostgreSQL server, ownership must remain explicit at schema and object level. Another service must not mutate mightyETL-owned objects through its ORM or startup scripts without a separately reviewed ownership and compatibility decision.

## Security, integrity, and acquisition evidence

A single versioned schema authority improves change-management and processing-integrity evidence because the code review, migration artifact, schema-history record, checksum validation, deployment evidence, and recovery procedure can be traced to one controlled path. It does not by itself establish SOC 2, CSAP, or another certification.

Migration logs and failure diagnostics must not expose business payloads, credentials, raw principals, sensitive SQL literals, or unrestricted provider/database exception text. Purpose-bound privileged diagnostics may retain deeper evidence only with explicit access control, retention, and audit requirements.

Canonical PRD/TRD/Architecture/ADR/ERD/Test Strategy/Operability/Traceability reconciliation remains owned by #149/#159 while that writer lane is active. Those authorities must represent #183/#324 as `active_pr` until protected integration and distinguish Flyway-managed service migrations, bootstrap compatibility SQL, test-only schema setup, and rollback/forward-recovery evidence.

## Machine-checkable contract

`FlywaySchemaAuthorityTest` binds this doctoring to deployable configuration. It fails if production re-enables Hibernate `update`, `create`, or `create-drop`, if Flyway safety defaults disappear, or if this source-backed schema-history, checksum, `baseline-on-migrate`, and forward recovery evidence disappears.

## References (APA 7)

Spring Boot. (2026). *Database initialization*. https://docs.spring.io/spring-boot/how-to/data-initialization.html

Redgate. (2026, July 20). *Validate*. https://documentation.red-gate.com/flyway/reference/commands/validate

Redgate. (2025, January 16). *Flyway schema history table*. https://documentation.red-gate.com/flyway/flyway-concepts/migrations/flyway-schema-history-table

Redgate. (2026, July 20). *Migrate*. https://documentation.red-gate.com/fd/migrate-277578887.html

Redgate. (2026, July 20). *Repair*. https://documentation.red-gate.com/flyway/reference/commands/repair

These primary sources were revalidated on 2026-08-13. The maintained Spring Boot guidance still recommends a single schema-generation mechanism, and current Flyway documentation continues to define checksum-based validation and explicit schema-history repair semantics.
