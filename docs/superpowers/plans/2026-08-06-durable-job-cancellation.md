# Durable ETL Job Cancellation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add owner-safe, idempotent, lease-fenced cancellation for pending and running durable ETL jobs.

**Architecture:** Extend the existing durable job service with one transactional conditional-update authority. Persist only hashed cancellation identity and fixed machine codes, keep the current operator-safe response model, and let existing exact-lease predicates roll back a worker transaction when cancellation wins. Add a PostgreSQL migration, HTTP endpoint, deterministic integration tests, and standards-backed operations documentation.

**Tech Stack:** Java 25, Spring Framework transaction management, Spring MVC, JdbcTemplate, PostgreSQL 18, H2 integration tests, JUnit 5, Mockito, JaCoCo, Maven.

## Global Constraints

- Preserve standalone operation and modular MSA integration.
- Do not modify the existing review agent, provider configuration, or credential names.
- Do not use `COPILOT_GITHUB_TOKEN`.
- Every introduced database object name contains at least two descriptive words and uses `snake_case`.
- Raw principals, cancellation keys, payloads, hashes, lease identifiers, SQL, and exception messages never enter client responses, logs, or metric tags.
- Added production statement and branch coverage remains 100%.
- Every public production API has beginner-readable Javadoc.
- No project test may be skipped.
- Update `CHANGELOG.md` and APA 7th doctoring before merge.

---

### Task 1: Lock the cancellation state and migration contract

**Files:**
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobMigrationDocumentationTest.java`
- Create: `etl-service/src/main/resources/db/migration/V6__add_etl_job_cancellation.sql`

**Interfaces:**
- Consumes: existing `etl_job_records` status, payload, lease, and failure constraints.
- Produces: `CANCELLED`, `cancellation_key_hash`, `cancellation_code`, and `job_cancelled_at` schema contract.

- [ ] **Step 1: Write the failing migration assertions**

Require the V6 file, `CANCELLED` lifecycle, cancellation hash/code/timestamp fields, terminal payload clearing, non-running lease clearing, format constraints, and explicit rollback guidance.

- [ ] **Step 2: Run the focused test and observe failure**

Run:

```bash
./mvnw -B -pl etl-service -Dtest=EtlJobMigrationDocumentationTest test
```

Expected: failure because V6 does not exist.

- [ ] **Step 3: Add the migration**

Use `ALTER TABLE ... DROP CONSTRAINT ... ADD CONSTRAINT ...` so a clean install and an upgrade both converge on the same lifecycle invariants.

- [ ] **Step 4: Run the focused test**

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add etl-service/src/main/resources/db/migration/V6__add_etl_job_cancellation.sql \
  etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobMigrationDocumentationTest.java
git commit -m "feat(etl): add durable job cancellation schema"
```

### Task 2: Define the service-level cancellation contract test-first

**Files:**
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobServiceIntegrationTest.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobCancellation.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobStatus.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobService.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlRequestError.java`

**Interfaces:**
- Produces: `EtlJobCancellation cancelOwned(UUID, String, String)`.
- Produces: `EtlJobCancellation(EtlJobSnapshot snapshot, boolean replayed)`.

- [ ] **Step 1: Add failing tests**

Cover pending cancellation, payload clearing, same-key replay, quoted/raw key normalization, different-key rejection, owner isolation, missing identifier, succeeded conflict, failed conflict, running lease clearing, and invalid-key validation before JDBC access.

- [ ] **Step 2: Run the focused integration test**

Expected: compile or assertion failure because cancellation APIs and schema fields are absent.

- [ ] **Step 3: Add `CANCELLED` and stable request errors**

Add fixed RFC 9457 metadata for cancellation-key required/reused/in-progress and already-succeeded/already-failed conflicts.

- [ ] **Step 4: Add the immutable cancellation result**

Validate both fields and expose no persistence identity.

- [ ] **Step 5: Implement one conditional-update authority**

Validate inputs before database access, hash principal and normalized key, perform the PENDING/RUNNING update, then classify a zero-row result through one owner-scoped read.

- [ ] **Step 6: Run the focused test**

Expected: pass.

- [ ] **Step 7: Commit**

```bash
git add etl-service/src/main/java/com/xtrmetl/etl/job \
  etl-service/src/main/java/com/xtrmetl/etl/service/EtlRequestError.java \
  etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobServiceIntegrationTest.java
git commit -m "feat(etl): cancel owner-scoped durable jobs"
```

### Task 3: Add the authenticated HTTP cancellation action

**Files:**
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/controller/EtlJobController.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobControllerTest.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobControllerFailureTest.java`

**Interfaces:**
- Consumes: `EtlJobService.cancelOwned`.
- Produces: `POST /api/etl/jobs/{jobRecordId}/cancellation`.

- [ ] **Step 1: Add failing controller tests**

Cover first cancellation, replay header, authentication, missing key, malformed identifier, typed conflict, data-access failure, and unexpected failure.

- [ ] **Step 2: Run focused controller tests**

Expected: failure because the route is absent.

- [ ] **Step 3: Implement the endpoint**

Parse authentication and identifier before service access, preserve typed/data-access failures, wrap unexpected runtime failures, return `200`, `no-store`, weak ETag, and `Idempotency-Replayed`.

- [ ] **Step 4: Run focused tests**

Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add etl-service/src/main/java/com/xtrmetl/etl/controller/EtlJobController.java \
  etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobControllerTest.java \
  etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobControllerFailureTest.java
git commit -m "feat(etl): expose durable job cancellation action"
```

### Task 4: Prove worker and representation compatibility

**Files:**
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobLeaseRepositoryIntegrationTest.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/controller/EtlJobPollingAdviceTest.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobConditionalStatusTest.java`

**Interfaces:**
- Consumes: existing exact-live-lease success predicate, polling advice, and ETag generation.
- Produces: regression evidence that cancellation is terminal and invalidates stale workers and validators.

- [ ] **Step 1: Add a running-cancellation lease test**

Claim a job, cancel its row through the service contract, and prove `markSucceeded` raises `StaleEtlJobLeaseException` with no terminal overwrite.

- [ ] **Step 2: Add polling and conditional tests**

Prove `CANCELLED` never emits `Retry-After` and the committed cancellation produces a different validator from the active representation.

- [ ] **Step 3: Run focused tests**

Expected: pass.

- [ ] **Step 4: Commit**

```bash
git add etl-service/src/test/java/com/xtrmetl/etl
git commit -m "test(etl): prove cancellation race and HTTP invariants"
```

### Task 5: Complete operations, changelog, and exact-head verification

**Files:**
- Modify: `docs/etl/durable-job-intake.md`
- Create: `docs/operations/durable-job-cancellation.md`
- Modify: `CHANGELOG.md`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobMigrationDocumentationTest.java`

**Interfaces:**
- Produces: operator rollout/rollback, race, privacy, and connector-limitation evidence.

- [ ] **Step 1: Update documentation tests first**

Require endpoint, replay, conflicts, cancellation-first/success-first outcomes, transactional-target limitation, migration name, and rollback instructions.

- [ ] **Step 2: Update authoritative documentation**

Include Mermaid state/race diagrams, PostgreSQL locking behavior, RFC 9110/9457 mapping, telemetry, rollout, and rollback.

- [ ] **Step 3: Update `CHANGELOG.md` under Unreleased**

Record the buyer-visible cancellation action, terminal state, idempotency, lease invalidation, migration, and limitations.

- [ ] **Step 4: Run all verification**

```bash
./mvnw -B test
git diff --check
git status --short
```

Expected: every reactor module succeeds, JaCoCo configured production statement and branch coverage remains 100%, and no file is skipped.

- [ ] **Step 5: Commit**

```bash
git add docs CHANGELOG.md etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobMigrationDocumentationTest.java
git commit -m "docs(etl): document durable job cancellation"
```

## Plan self-review

- Every design requirement maps to a task.
- No raw principal or cancellation key crosses the persistence or response boundary.
- `EtlJobCancellation`, controller, migration, service status branches, worker stale outcome, polling behavior, and ETag invalidation all have explicit tests.
- Public signatures and names are consistent across tasks.
- No placeholder or deferred implementation instruction remains.
