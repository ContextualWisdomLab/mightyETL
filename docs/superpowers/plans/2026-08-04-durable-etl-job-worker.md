# Durable ETL Job Worker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Execute accepted ETL jobs safely across multiple replicas with PostgreSQL row claiming,
lease fencing, atomic target effects and terminal success, bounded retries, and terminal payload
clearing.

**Architecture:** A short-transaction `EtlJobStore` claims or transitions rows; a separate
transactional `EtlJobExecutionService` composes the existing durable ETL ledger with terminal
success; and a bounded `EtlJobWorker` schedules polling but never owns correctness. PostgreSQL row
locks and opaque lease tokens are the ownership boundary.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Framework 6.2 transactions and scheduling,
`JdbcTemplate`, PostgreSQL 18, Flyway, JUnit 5, H2 transaction integration tests, Mockito, JaCoCo.

## Global Constraints

- Production worker activation remains disabled by default.
- All new database objects use descriptive multi-word `snake_case` names.
- All new production classes, records, public methods, and configuration properties have complete Javadocs.
- Every added production statement and branch is covered; JaCoCo allows zero missed instructions,
  lines, methods, or branches in `com.xtrmetl.etl.job.*`.
- No skipped or ignored tests.
- No raw principal, submission key, payload, lease token, SQL, exception text, or stack trace enters
  a client response or durable failure code.
- PostgreSQL owns claim distribution; scheduler-instance uniqueness is not a correctness assumption.
- Cancellation, prioritization, and dead-letter replay remain out of scope.

---

### Task 1: Lock the worker model and configuration contract with failing tests

**Files:**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobWorkerPropertiesTest.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobClaimTest.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobWorkerActivationTest.java`

**Interfaces:**
- Produces: `EtlJobWorkerProperties`, `EtlJobClaim`, and the worker scheduling annotation contract.

- [ ] **Step 1: Write failing tests for property defaults and every lower/upper bound**

Assert defaults `false`, `5000`, `300000`, `5000`, `3`, and `1`; assert the accepted boundary
values; assert each out-of-range value throws `IllegalArgumentException`; assert retry delay is
`min(base * attempt, 3_600_000)` and rejects attempt numbers below one.

- [ ] **Step 2: Write failing tests for immutable claim invariants**

Construct one valid claim and assert every accessor. Assert null identifiers, token, payload, expiry,
non-positive attempt count, and expiry before claim time are rejected.

- [ ] **Step 3: Write a failing reflection test for fixed-delay scheduling**

Require `EtlJobWorker.poll()` to carry
`@Scheduled(fixedDelayString = "${xtrmetl.etl.jobs.worker.poll-delay-millis:5000}")` and require the
worker class to be disabled unless `xtrmetl.etl.jobs.worker.enabled=true`.

- [ ] **Step 4: Run the focused tests and verify RED**

Run:

```bash
./mvnw -pl etl-service -Dtest=EtlJobWorkerPropertiesTest,EtlJobClaimTest,EtlJobWorkerActivationTest test
```

Expected: compilation failure because the worker types do not exist.

- [ ] **Step 5: Commit the RED tests**

```bash
git add etl-service/src/test/java/com/xtrmetl/etl/job
git commit -m "test(etl): define durable worker model contracts"
```

---

### Task 2: Add the lease migration and migration contract tests

**Files:**
- Create: `etl-service/src/main/resources/db/migration/V3__add_etl_job_worker_leases.sql`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobWorkerMigrationTest.java`

**Interfaces:**
- Produces: `lease_token`, `lease_expires_at`, `next_attempt_at`, `processed_record_count`, and
  `etl_job_worker_queue_index`.

- [ ] **Step 1: Write the migration test first**

Read the migration as UTF-8 and assert the four exact column names, multi-word constraint names,
`RUNNING` lease coupling, successful-count coupling, failed-code coupling, partial queue index,
`PENDING`/`RUNNING` eligibility, and absence of one-word database object declarations.

- [ ] **Step 2: Run the migration test and verify RED**

```bash
./mvnw -pl etl-service -Dtest=EtlJobWorkerMigrationTest test
```

Expected: FAIL because the V3 migration is absent.

- [ ] **Step 3: Add the minimal PostgreSQL migration**

Use parameter-free DDL only. Add the columns, constraints, and queue index defined in the approved
design; drop `etl_job_status_created_index` before creating the eligibility-oriented replacement.

- [ ] **Step 4: Run the migration test and verify GREEN**

```bash
./mvnw -pl etl-service -Dtest=EtlJobWorkerMigrationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add etl-service/src/main/resources/db/migration/V3__add_etl_job_worker_leases.sql \
  etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobWorkerMigrationTest.java
git commit -m "feat(etl): add durable worker lease schema"
```

---

### Task 3: Implement bounded worker properties and claim model

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobWorkerProperties.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobClaim.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/config/MightyEtlConfigAliasEnvironmentPostProcessor.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/config/MightyEtlConfigAliasEnvironmentPostProcessorTest.java`
- Modify: `etl-service/src/main/resources/application.yml`

**Interfaces:**
- Produces:
  - `boolean isEnabled()` / `setEnabled(boolean)`
  - bounded millisecond and attempt accessors
  - `long retryDelayForAttempt(int attemptCount)`
  - `record EtlJobClaim(UUID jobRecordId, UUID leaseToken, String requestPayload,
    int attemptCount, Instant claimedAt, Instant leaseExpiresAt)`

- [ ] **Step 1: Extend alias tests before alias production code**

Require preferred `mightyetl.etl.jobs.worker.*` keys to mirror to every supported legacy key and
require legacy-only values to mirror back without overriding the preferred namespace.

- [ ] **Step 2: Run alias and model tests and confirm RED**

```bash
./mvnw -pl etl-service -Dtest=MightyEtlConfigAliasEnvironmentPostProcessorTest,EtlJobWorkerPropertiesTest,EtlJobClaimTest test
```

Expected: failures for missing aliases and types.

- [ ] **Step 3: Implement properties and claim model minimally**

Use `@Component("etlJobWorkerProperties")` and
`@ConfigurationProperties(prefix = "xtrmetl.etl.jobs.worker")`. Implement one shared range helper
and saturating multiplication for retry delay. Validate all claim constructor arguments.

- [ ] **Step 4: Add fail-closed application defaults and aliases**

Add `ETL_JOB_WORKER_ENABLED`, `ETL_JOB_WORKER_POLL_DELAY_MILLIS`,
`ETL_JOB_WORKER_LEASE_DURATION_MILLIS`, `ETL_JOB_WORKER_RETRY_DELAY_MILLIS`,
`ETL_JOB_WORKER_MAX_ATTEMPTS`, and `ETL_JOB_WORKER_JOBS_PER_POLL`. Add every relative key to the
alias post-processor.

- [ ] **Step 5: Run focused tests and verify GREEN**

```bash
./mvnw -pl etl-service -Dtest=MightyEtlConfigAliasEnvironmentPostProcessorTest,EtlJobWorkerPropertiesTest,EtlJobClaimTest test
```

Expected: PASS with zero failures.

- [ ] **Step 6: Commit**

```bash
git add etl-service/src/main etl-service/src/test/java/com/xtrmetl/etl/config \
  etl-service/src/test/java/com/xtrmetl/etl/job
git commit -m "feat(etl): configure bounded durable workers"
```

---

### Task 4: Build transactional claim and fenced failure transitions

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobStore.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobStoreIntegrationTest.java`

**Interfaces:**
- Produces:
  - `@Nullable EtlJobClaim claimNext()`
  - `boolean recordFailure(EtlJobClaim claim, String failureCode, boolean retryable)`

- [ ] **Step 1: Write the H2 schema fixture and claim tests**

Create `etl_job_records` with the complete V2+V3 constraints. Insert two deterministic jobs. Assert
oldest-first selection, incremented attempts, unique lease token, future expiry, and empty queue
returning `null`.

- [ ] **Step 2: Write concurrent non-overlap and reclaim tests**

Use two independent `TransactionTemplate` calls and latches. Hold the first selected row lock open;
require the second store call to return no claim rather than block or return the same row. Insert an
expired `RUNNING` row and assert reclaim writes a different token and increments the attempt.

- [ ] **Step 3: Write failure-transition tests**

Assert retryable failure with remaining attempts returns to `PENDING`, retains payload, clears lease,
and schedules a future attempt. Assert exhausted transient, deterministic, and non-transient failure
set `FAILED`, clear payload, clear lease, and persist only the supplied stable code. Assert a stale
token returns `false` without changing the row.

- [ ] **Step 4: Run store tests and verify RED**

```bash
./mvnw -pl etl-service -Dtest=EtlJobStoreIntegrationTest test
```

Expected: compilation failure because `EtlJobStore` is absent.

- [ ] **Step 5: Implement parameterized SQL and short transactions**

Use deterministic `ORDER BY`, `LIMIT 1 FOR UPDATE SKIP LOCKED`, a conditional claim update, and
fenced retry/terminal updates. Never interpolate identifiers, values, or failure codes into SQL.

- [ ] **Step 6: Run store tests and verify GREEN**

```bash
./mvnw -pl etl-service -Dtest=EtlJobStoreIntegrationTest test
```

Expected: PASS. If H2 does not implement the PostgreSQL locking clause, keep the SQL contract test
and execute the concurrency case against the repository PostgreSQL integration service; do not skip
or convert the case to a mock-only success.

- [ ] **Step 7: Commit**

```bash
git add etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobStore.java \
  etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobStoreIntegrationTest.java
git commit -m "feat(etl): claim jobs with fenced leases"
```

---

### Task 5: Expose one non-retrying in-transaction ETL execution port

**Files:**
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlService.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/service/EtlServiceIdempotencyTransactionBoundaryTest.java`

**Interfaces:**
- Produces: `EtlIdempotencyResult processDataIdempotentlyInCurrentTransaction(
  String data, String idempotencyKey, String principalScope)`.

- [ ] **Step 1: Add failing transaction-boundary tests**

Require the method to reject calls without an actual transaction, process target rows and ledger
inside the caller transaction, roll both back when the caller marks rollback-only, and replay a
committed caller transaction without another target write.

- [ ] **Step 2: Run and verify RED**

```bash
./mvnw -pl etl-service -Dtest=EtlServiceIdempotencyTransactionBoundaryTest test
```

Expected: compilation failure for the missing method.

- [ ] **Step 3: Extract the existing idempotent body without changing the HTTP API**

Keep `processDataIdempotently` annotated with `@Retryable` and `@Transactional`, but delegate its
body to the new method. The new method performs one attempt and requires the caller's active
transaction; it has no retry annotation.

- [ ] **Step 4: Run focused and existing idempotency tests**

```bash
./mvnw -pl etl-service -Dtest='EtlServiceIdempotency*Test,EtlServiceTransactionIntegrationTest' test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add etl-service/src/main/java/com/xtrmetl/etl/service/EtlService.java \
  etl-service/src/test/java/com/xtrmetl/etl/service/EtlServiceIdempotencyTransactionBoundaryTest.java
git commit -m "refactor(etl): expose transaction-scoped idempotent execution"
```

---

### Task 6: Compose target effects and terminal success atomically

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobLeaseLostException.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobExecutionService.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobExecutionServiceIntegrationTest.java`

**Interfaces:**
- Produces: `int execute(EtlJobClaim claim)`; throws `EtlJobLeaseLostException` when ownership is
  absent, stale, or expired.

- [ ] **Step 1: Write success and privacy tests**

Insert one `RUNNING` claim, execute it, and assert target count, ledger count, `SUCCEEDED`, null
payload, null lease fields, null failure code, and exact `processed_record_count`. Assert no raw
payload or token is returned by the method.

- [ ] **Step 2: Write stale and atomic rollback tests**

Assert a wrong token and expired lease create no target or ledger rows. Add a temporary database
constraint that rejects the terminal update; require target and ledger inserts to roll back and the
job to remain `RUNNING`.

- [ ] **Step 3: Write exactly-once replay test**

Complete a job, then deliberately restore the same job to `RUNNING` with the same payload and a new
token as a crash-recovery simulation. Execute again and assert the durable ledger replays while the
target contains exactly one effect.

- [ ] **Step 4: Run and verify RED**

```bash
./mvnw -pl etl-service -Dtest=EtlJobExecutionServiceIntegrationTest test
```

Expected: compilation failure for missing execution types.

- [ ] **Step 5: Implement the leased-row lock and conditional terminal update**

Lock by job UUID and token, require `RUNNING` and `lease_expires_at > CURRENT_TIMESTAMP`, call the
new transaction-scoped ETL method with principal namespace `durable_etl_job_worker`, count result
lines, and conditionally update success with the same fence.

- [ ] **Step 6: Run and verify GREEN**

```bash
./mvnw -pl etl-service -Dtest=EtlJobExecutionServiceIntegrationTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add etl-service/src/main/java/com/xtrmetl/etl/job \
  etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobExecutionServiceIntegrationTest.java
git commit -m "feat(etl): commit job success with target effects"
```

---

### Task 7: Add the bounded worker orchestrator and failure classification

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobWorker.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobSchedulingConfiguration.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobWorkerTest.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobWorkerEndToEndTest.java`

**Interfaces:**
- Produces: `boolean runOnce()` and scheduled `void poll()`.

- [ ] **Step 1: Write unit tests for every classification branch**

Mock only the store and execution service. Assert empty queue, success, stale lease, transient data
access, non-transient data access, deterministic `EtlRequestException`, unexpected runtime failure,
retryability flag, stable code, and stopping after `jobs-per-poll` or first empty claim.

- [ ] **Step 2: Write a shared-database end-to-end test**

Create two worker instances sharing the same datasource, enqueue one job, run them concurrently, and
assert one terminal success, one ledger row, and one target effect.

- [ ] **Step 3: Run and verify RED**

```bash
./mvnw -pl etl-service -Dtest=EtlJobWorkerTest,EtlJobWorkerEndToEndTest,EtlJobWorkerActivationTest test
```

Expected: compilation failure for the missing worker.

- [ ] **Step 4: Implement the orchestrator and scheduling configuration**

Use `@ConditionalOnBooleanProperty` with `matchIfMissing=false`; keep `poll()` void; call `runOnce()`
sequentially up to the configured bound; never catch `Error`; and never persist exception text.

- [ ] **Step 5: Run and verify GREEN**

```bash
./mvnw -pl etl-service -Dtest=EtlJobWorkerTest,EtlJobWorkerEndToEndTest,EtlJobWorkerActivationTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add etl-service/src/main/java/com/xtrmetl/etl/job \
  etl-service/src/test/java/com/xtrmetl/etl/job
git commit -m "feat(etl): execute bounded durable job polls"
```

---

### Task 8: Add the successful-record summary to the owner-scoped API

**Files:**
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobSnapshot.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobStatusResponse.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobService.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobModelTest.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobControllerTest.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobServiceIntegrationTest.java`

**Interfaces:**
- Produces: nullable `Integer processedRecordCount()` on internal and wire status records.

- [ ] **Step 1: Update tests first**

Require successful status to include the exact nonnegative count, nonterminal/failed status to omit
it, negative counts to be rejected, and payload/hash/lease fields to remain absent.

- [ ] **Step 2: Run and verify RED**

```bash
./mvnw -pl etl-service -Dtest=EtlJobModelTest,EtlJobControllerTest,EtlJobServiceIntegrationTest test
```

Expected: compilation or assertion failures for the missing count.

- [ ] **Step 3: Extend queries, mapping, records, and JSON response**

Select `processed_record_count`; map SQL null with `getObject(..., Integer.class)`; validate
nonnegative values; and include it with `NON_NULL` serialization only.

- [ ] **Step 4: Run and verify GREEN**

```bash
./mvnw -pl etl-service -Dtest=EtlJobModelTest,EtlJobControllerTest,EtlJobServiceIntegrationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add etl-service/src/main/java/com/xtrmetl/etl/job \
  etl-service/src/main/java/com/xtrmetl/etl/controller/EtlJobController.java \
  etl-service/src/test/java/com/xtrmetl/etl/job
git commit -m "feat(etl): report successful durable job counts"
```

---

### Task 9: Update operator documentation, standards traceability, and changelog

**Files:**
- Modify: `docs/etl/durable-job-intake.md`
- Create: `docs/etl/durable-job-worker.md`
- Modify: `docs/api/problem-details.md`
- Modify: `CHANGELOG.md`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/EtlJobWorkerDocumentationTest.java`

**Interfaces:**
- Produces: complete operator activation, lease, retry, privacy, crash-recovery, and APA 7 source
  contract.

- [ ] **Step 1: Write documentation-alignment tests first**

Require all worker properties and environment variables, PostgreSQL 18 `SKIP LOCKED`, Spring fixed
delay, lease fencing, terminal payload clearing, stable failure codes, crash cases, MSA boundary,
APA 7 references, and the explicit exclusions for cancellation/priority/DLQ.

- [ ] **Step 2: Run and verify RED**

```bash
./mvnw -pl etl-service -Dtest=EtlJobWorkerDocumentationTest test
```

Expected: FAIL until the documents are updated.

- [ ] **Step 3: Write the operator documentation and update CHANGELOG**

Replace the obsolete “intake does not execute” wording. Explain independent intake/worker flags,
safe rollout, multiple replicas, bounded throughput, lease sizing, retry codes, status count,
retention, telemetry privacy, crash semantics, and rollback.

- [ ] **Step 4: Run documentation tests and verify GREEN**

```bash
./mvnw -pl etl-service -Dtest=EtlJobWorkerDocumentationTest,DocumentationValidationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add docs CHANGELOG.md etl-service/src/test/java/com/xtrmetl/etl/documentation
git commit -m "docs: document durable ETL worker operations"
```

---

### Task 10: Enforce complete coverage and run full verification

**Files:**
- Modify: `etl-service/pom.xml` only if the existing package-level JaCoCo rule does not include a
  newly generated class.
- Modify: focused tests only to cover genuine uncovered behavior; do not exclude production code.

**Interfaces:**
- Consumes: all prior worker types and tests.
- Produces: exact-head release-quality verification evidence.

- [ ] **Step 1: Run all `etl-service` tests and JaCoCo check**

```bash
./mvnw -pl etl-service test
```

Expected: zero failures, zero errors, zero skipped tests, and zero missed durable-job instructions,
lines, methods, or branches.

- [ ] **Step 2: Inspect the report, not only the Maven exit code**

Confirm `etl-service/target/site/jacoco/index.html` analyzes nonzero durable-job classes and every
class is 100%. A report that says “0 classes” is a failed coverage configuration even if Maven exits
zero.

- [ ] **Step 3: Run the complete reactor**

```bash
./mvnw -B test
```

Expected: every reactor module `SUCCESS`; no test failure, error, or skip.

- [ ] **Step 4: Build packages**

```bash
./mvnw -B package -DskipTests
```

Expected: reactor `BUILD SUCCESS`.

- [ ] **Step 5: Review the exact diff and database naming**

```bash
git diff --check develop...HEAD
git diff --name-only develop...HEAD
grep -RInE 'CREATE (TABLE|INDEX) [a-z]+([ ;(]|$)|CONSTRAINT [a-z]+([ ;(]|$)' \
  etl-service/src/main/resources/db/migration
```

Expected: no whitespace errors and no newly introduced one-word database object names.

- [ ] **Step 6: Commit any test-only coverage closure**

```bash
git add etl-service
 git commit -m "test(etl): close durable worker coverage gaps"
```

Skip the commit only when there is no diff.

- [ ] **Step 7: Open the PR as draft, request review, and verify current-head Checks**

Create a draft PR to `develop`, obtain thread-aware review state, address every actionable current
thread, then mark ready only after the complete same-head CI, dependency review, SBOM, SAST, and
security workflows succeed.

- [ ] **Step 8: Merge only the exact reviewed head**

Use the expected head SHA. After merge, query open PRs again. Do not release merely because this
bounded slice merged; release requires repository-wide release gates, version alignment, and an
updated dated CHANGELOG section.
