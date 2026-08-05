# Durable ETL Job Lease Worker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute accepted asynchronous ETL jobs safely across replicas with PostgreSQL claim locking, exact lease fencing, bounded retries, atomic success, terminal payload clearing, and operator-safe evidence.

**Architecture:** A transaction-scoped repository owns claim and state transitions; a separate transactional execution service couples existing ETL target writes with an exact-live-lease success update; a fixed-delay coordinator classifies failures and performs retry or terminal transitions in a new transaction. PostgreSQL row state is the distribution and fencing authority, while scheduling only supplies repeated polling.

**Tech Stack:** Java 25, Spring Boot, Spring JDBC transactions, Spring scheduling, PostgreSQL 18 SQL, Flyway, Micrometer, JUnit 5, Mockito, H2 compatibility tests, Maven/Jacoco.

## Global Constraints

- Preserve standalone operation and modular MSA compatibility with ContextualWisdomLab/.github, naruon, and other CWL services.
- Database objects contain at least two descriptive words and use `snake_case`.
- Worker activation is fail-closed and disabled by default.
- Every public production type and method has beginner-readable Javadoc.
- Added durable-job production code must have zero missed instruction, line, method, or branch coverage.
- Payloads, principals, idempotency keys, job identifiers, lease identifiers, SQL, and exception messages never enter metrics or logs.
- A stale or expired lease cannot commit target effects or state transitions.
- Versioned Flyway migrations are immutable after publication; rollback uses a forward compensating migration.

---

### Task 1: Lock the schema and configuration contracts

**Files:**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobLeaseMigrationTest.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobWorkerPropertiesTest.java`
- Create: `etl-service/src/main/resources/db/migration/V3__add_etl_job_lease_fencing.sql`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobWorkerProperties.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/EtlApplication.java`
- Modify: `etl-service/src/main/resources/application.yml`

**Interfaces:**
- Produces: `EtlJobWorkerProperties` with `enabled`, `fixedDelayMilliseconds`, `initialDelayMilliseconds`, `leaseDurationSeconds`, `maxAttempts`, and `leaseOwnerId`.

- [ ] Write migration and property tests first. Require the three lease columns, lifecycle constraints, claim index, fail-closed defaults, safe owner profile, and all numeric boundaries.
- [ ] Run `./mvnw -B -pl etl-service -Dtest=EtlJobLeaseMigrationTest,EtlJobWorkerPropertiesTest test` and record the expected missing-file/type failure.
- [ ] Add the migration, properties, application registration, and environment-backed defaults.
- [ ] Re-run the focused tests and commit.

### Task 2: Add exclusive claim and exact transition persistence

**Files:**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobLeaseRepositoryIntegrationTest.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobLease.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobLeaseRepository.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/StaleEtlJobLeaseException.java`

**Interfaces:**
- Produces: `Optional<EtlJobLease> claimNext(String leaseOwnerId, Duration leaseDuration, int maxAttempts)`.
- Produces: `markSucceeded`, `releaseForRetry`, and `markFailed`, each returning only after an exact, unexpired lease transition or throwing `StaleEtlJobLeaseException`.

- [ ] Write H2 integration tests for deterministic order, simultaneous single claim, expired reclaim, exhausted terminalization, success, retry, failure, and stale update refusal.
- [ ] Run the focused test and record the missing-type failure.
- [ ] Implement the two-statement lock-and-update claim transaction using `FOR UPDATE SKIP LOCKED` and database `CURRENT_TIMESTAMP`.
- [ ] Implement exact-live-lease transition predicates and stable failure validation.
- [ ] Re-run the focused test and commit.

### Task 3: Couple ETL target effects to terminal success

**Files:**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobExecutionServiceIntegrationTest.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobExecutionService.java`

**Interfaces:**
- Consumes: `EtlJobLease`, `EtlService.processData`, `EtlJobLeaseRepository.markSucceeded`.
- Produces: `void execute(EtlJobLease lease)` in one Spring transaction.

- [ ] Write integration tests proving target rows and `SUCCEEDED` commit together.
- [ ] Add a stale-lease test that changes the claim before execution and asserts both the exception and zero committed target rows.
- [ ] Run the focused test and record the missing-type failure.
- [ ] Implement the minimal transactional service and re-run the tests.
- [ ] Commit.

### Task 4: Add bounded fixed-delay coordination and evidence

**Files:**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobWorkerTest.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobWorker.java`

**Interfaces:**
- Consumes: repository claim/transitions, execution service, worker properties, `MeterRegistry`.
- Produces: one `pollOnce()` invocation that claims at most one job and records finite outcomes.

- [ ] Write tests for no work, success, transient retry, exhausted transient failure, deterministic request failure, non-transient target failure, unexpected failure, and stale evidence.
- [ ] Run the focused test and record the missing-type failure.
- [ ] Implement the conditional worker bean, fixed-delay method, failure classification, retry bound, duration timer, and finite-cardinality outcome counter.
- [ ] Re-run the tests and commit.

### Task 5: Complete operations, privacy, compatibility, and release evidence

**Files:**
- Modify: `docs/etl/durable-job-intake.md`
- Create: `docs/operations/durable-job-worker.md`
- Modify: `CHANGELOG.md`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobMigrationDocumentationTest.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobCoveragePolicyTest.java`

**Interfaces:**
- Produces: authoritative activation, SLO, metrics, failure-code, recovery, retention, and rollback guidance.

- [ ] Add documentation-first tests requiring activation pairs, privacy boundaries, exact failure codes, rollback ordering, and standards references.
- [ ] Update the authoritative docs and changelog.
- [ ] Run `./mvnw -B -pl etl-service test`.
- [ ] Run `./mvnw -B test` across the full reactor.
- [ ] Inspect Jacoco for zero missed durable-job instructions, lines, methods, and branches.
- [ ] Open a stacked draft PR against `ci/hourly-opencode-nvidia-nim`, inspect every review and exact-head check, and mark ready only after all gates pass.
