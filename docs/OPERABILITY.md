# Operability, SLO, Recovery, and Runbook Index

**Protected baseline:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`

This document defines system-level operating expectations. Targets below are acceptance objectives unless explicitly backed by measured production-like evidence; they are not invented attainment claims.

## 1. Operating modes

### Standalone ETL

Required dependencies: ETL Service + target PostgreSQL for synchronous/ledger/job persistence paths actually enabled. Gateway/Eureka/CDC/Kafka are optional unless the deployment deliberately composes them.

### Standalone CDC

Required dependencies: CDC Service + configured source PostgreSQL + Kafka for the live publication path. Gateway/Eureka/ETL are optional unless composed.

### Composed MSA

Gateway, ETL, CDC, Eureka, optional Config Server, tracing, PostgreSQL, and Kafka are operated as separately observable components with independent health/recovery signals.

## 2. Core SLI inventory

| Capability | SLI | Status |
| --- | --- | --- |
| synchronous ETL | request success/error, latency, committed rows, rollback count | `implemented_on_develop` |
| idempotency | replay/conflict/in-progress outcomes, target duplicate count | `implemented_on_develop` |
| durable intake | submit/status latency, PENDING age/count | `implemented_on_develop` when enabled |
| durable worker | claim/idle/succeeded/retried/failed/stale, lease age | `active_pr` #143 |
| CDC | running state, capture errors, replication slot lag, canonical-map counters | `implemented_on_develop` |
| CDC Kafka delivery | ack latency/timeouts/retries | `active_pr` #139 |
| gateway identity | auth success/failure by bounded reason | `active_pr` #142; protected gap |
| autonomous maintenance | run result, candidate publication, exact-head authorization | `active_pr` #121 |

Metric labels must remain finite and must not contain raw job/principal/key/payload/lease/SQL/secret data.

## 3. SLO objectives

These are product objectives to be measured before release claims:

- successful bounded synchronous ETL commits exactly the accepted row count: target **100% correctness**, not probabilistic availability;
- same committed idempotent request creates **0 duplicate target effects** within the transactional scope;
- operator status must never intentionally state `stopped`/`succeeded` earlier than the underlying contract proves;
- required PR/release evidence must correspond to the exact accepted source revision: **100% provenance binding**;
- critical/high accepted security findings at release: **0 unresolved actionable findings**;
- owned production statement/branch coverage at protected merge: **100%** configured target.

Latency/availability SLO values require environment-specific baseline measurements and must not be invented in documentation. Add numeric service latency/availability targets only with load profile, capacity model, and alert/error-budget ownership.

## 4. Health and readiness

- Process liveness must not be confused with dependency readiness.
- ETL readiness for target-write traffic should prove required target/database dependencies for the enabled path.
- CDC readiness should distinguish service process health, source configuration, engine task state, and downstream publication readiness.
- `known_gap`: current CDC stopped-state observability can precede graceful Debezium Future completion; issue #141 owns repair.
- Gateway readiness must fail closed when the selected identity mode lacks its required trust material once #142 integrates.

## 5. Start/stop/restart

### ETL

Synchronous requests rely on database transactions. A process termination before commit must not be reported as a committed success. Durable idempotency/job state allows later replay/status recovery according to the stored contract.

### CDC

`start()` uses a dedicated single-thread executor and supports restart after `stop()` on protected develop. Application shutdown closes the engine, shuts down the executor, and waits up to its bounded termination period before forceful shutdown.

Ordinary `stop()` currently does not wait on the captured task Future. Operators must treat this as a known reliability gap until issue #141 integrates.

## 6. Backup and disaster recovery

### PostgreSQL

Backups must include, according to deployed scope:

- target business data;
- `etl_idempotency_records`;
- `etl_job_records` and all integrated later migrations;
- schema/Flyway history.

Recovery verification must test ledger/job referential/lifecycle constraints and avoid replaying a response ledger against target data restored to a different logical point without an explicit reconciliation plan.

### CDC offsets/schema history

The embedded engine uses configured offset and schema-history storage. Treat these files as continuity state. Backup/restore must be paired with source WAL/slot retention assumptions; restoring stale offsets can replay events and must be consumer-safe.

### Kafka

Topic durability/retention/replication are deployment-owned. mightyETL must not claim broker durability beyond the configured cluster and acknowledged producer semantics.

## 7. Rollback principles

- Never modify an already-applied Flyway migration in place; add a reviewed migration/recovery action.
- Stop serving an API that emits/depends on a new lifecycle state before rolling back to binaries that cannot deserialize/handle it.
- A database rollback across cancellation/replay lineage must preserve or deliberately archive evidence first.
- External warehouse/file/API/message effects require connector-native compensation/idempotency; database rollback alone cannot reverse them.
- Scheduler/workflow rollback must preserve branch protection and independent review; do not solve an automation incident by widening tokens.

## 8. Incident classes

### ETL atomicity incident

Freeze affected writes, preserve exact request/revision/transaction evidence, compare accepted rows with committed rows, identify whether a connector escaped the local transaction, and restore from an evidence-backed point rather than replay blindly.

### Idempotency divergence

Do not log raw keys while diagnosing. Compare authorized internal hashes/digests, principal namespace, transaction history, target effects, and application SHA. Reconcile target and ledger together.

### Durable job lifecycle incident

Pause intake/worker as appropriate, preserve job row and migration state, inspect lease/lifecycle constraints, and do not manually coerce terminal state without understanding transactional target effects.

### CDC lag/delivery incident

Inspect source slot/WAL lag, engine task state, Kafka producer/broker state, and downstream idempotency before resetting offsets. Do not delete offset state as a generic retry mechanism.

### Authentication incident

Protected develop's placeholder token filter is not an acceptable production identity boundary. For #142 deployments preserve issuer/JWK/audience/algorithm configuration evidence and deny-mode behavior without logging bearer values.

### Autonomous-agent incident

Disable candidate publication/authorization at the narrow deterministic writer layer while preserving read-only evidence collection. Revoke/rotate only credentials proven affected. Preserve exact branch/ref/workflow SHA and candidate bundle provenance.

## 9. Existing feature runbooks

Canonical system operation links to feature-specific evidence instead of duplicating it:

- `docs/etl/bounded-atomic-batches.md`;
- `docs/etl/idempotent-retries.md`;
- `docs/etl/durable-job-intake.md`;
- `docs/cdc/ops-and-reliability.md`;
- `docs/boot-support-strategy.md` where applicable;
- active PR runbooks become protected references only after merge.

## 10. Capacity and backpressure

- enforce configured ETL payload/record hard ceilings;
- avoid per-record thread creation/common-pool fan-out;
- preserve bounded retries;
- connector dispatchers serialize/constrain work according to connector safety;
- Kafka/CDC backpressure must be measured at the acknowledged publication boundary once #139 integrates;
- durable job worker concurrency is database lease controlled once #143 integrates.

## 11. Release operations

Release readiness requires integrated exact-source tests/security/coverage, migrations/rollback rehearsal, SBOM/provenance, standalone and composed smoke evidence, current canonical docs, independent review, and artifact verification. Do not release merely because an individual PR is green.

## 12. References

Debezium. (2026). *Debezium Engine 3.4*. Debezium Documentation. https://debezium.io/documentation/reference/3.4/development/engine.html

OpenTelemetry Authors. (2025). *Semantic conventions*. https://opentelemetry.io/docs/concepts/semantic-conventions/

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: Backup and restore*. https://www.postgresql.org/docs/18/backup.html
