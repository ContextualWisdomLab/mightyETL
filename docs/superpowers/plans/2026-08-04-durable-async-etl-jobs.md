# Durable Asynchronous ETL Jobs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a durable principal-scoped asynchronous ETL job API, PostgreSQL lease queue, and horizontally safe worker with exact-once target effects.

**Architecture:** A new job package separates the HTTP resource, domain records, configuration, durable-store port, PostgreSQL adapter, submission service, execution transaction, and scheduled worker. The worker claims with `FOR UPDATE SKIP LOCKED`; execution reuses the existing idempotency ledger and commits target rows, response ledger, terminal job state, and payload clearing in one transaction.

**Tech Stack:** Java 25, Spring Boot 3.5.16, Spring JDBC, Spring Scheduling, Spring Retry, PostgreSQL 18 semantics, Flyway, Jackson, JUnit 5, Mockito, H2 test support.

## Global Constraints

- Preserve `POST /api/etl/process` behavior and media types.
- Require an authenticated principal and valid `Idempotency-Key` for job submission.
- Return RFC 9110 `202 Accepted` with a status representation and `Location` monitor URI.
- Use only descriptive two-word-or-longer snake_case database identifiers.
- Never expose raw payloads, principal names, raw keys, hashes, leases, SQL, or exception messages.
- Use database claim ownership rather than scheduler-instance uniqueness.
- Clear request payloads in terminal states.
- Keep cancellation, listing, and application-level encryption out of this slice.
- Add complete Javadoc and focused statement/branch coverage for every new production path.
- Record the feature under `Unreleased` without publishing a release.

---

### Task 1: Shared idempotency-key and hashing utility

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlIdempotencyKey.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/service/EtlIdempotencyKeyTest.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlService.java`

**Interfaces:**
- Produces: `static String normalize(String value)`, `static String sha256(String value)`, and `static String scopedHash(String domain, String scope, String semanticKey)`.
- Consumes: RFC 9651 quoted String or retained legacy raw profile of 16 to 128 safe ASCII characters.

- [ ] **Step 1: Write failing normalization and hashing tests**

Cover quoted/raw equivalence, malformed keys, nulls, deterministic SHA-256, and domain-separated scoped hashes.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlIdempotencyKeyTest test`
Expected: compilation failure because `EtlIdempotencyKey` does not exist.

- [ ] **Step 3: Implement the package-private utility**

Use precompiled regexes, `MessageDigest.getInstance("SHA-256")`, UTF-8, length-prefixed scope composition, and fixed non-sensitive exceptions through `EtlRequestException`.

- [ ] **Step 4: Refactor `EtlService` to use the utility**

Remove duplicated regex and digest code while preserving all public behavior.

- [ ] **Step 5: Verify GREEN and regression behavior**

Run: `./mvnw -B -pl etl-service -Dtest=EtlIdempotencyKeyTest,EtlServiceIdempotencyIntegrationTest,EtlServiceIdempotencyConflictTest test`
Expected: all selected tests pass.

### Task 2: Side-effect-free ETL admission validation

**Files:**
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlService.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/service/EtlServiceAdmissionValidationTest.java`

**Interfaces:**
- Produces: `public void validateData(@Nullable String data)`.
- Guarantees: identical payload, JSON, record-count, identifier, duplicate-field, and transform-input validation as processing, with zero JDBC writes.

- [ ] **Step 1: Write failing validation tests**

Prove valid input returns normally; invalid JSON, invalid records, payload overflow, and duplicate normalized fields use existing typed errors; no JDBC method is invoked.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlServiceAdmissionValidationTest test`
Expected: compilation failure because `validateData` does not exist.

- [ ] **Step 3: Implement minimal validation**

Extract parse-and-prepare behavior into a private method shared by `validateData` and `processDataInCurrentTransaction`.

- [ ] **Step 4: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlServiceAdmissionValidationTest,EtlServiceBatchSafetyTest test`
Expected: all selected tests pass.

### Task 3: Job domain model and configuration

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobStatus.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobView.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobClaim.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobProperties.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobDomainTest.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobPropertiesTest.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/EtlApplication.java`

**Interfaces:**
- `EtlJobStatus`: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, serialized through lowercase `wireValue()` and strict `fromDatabase(String)`.
- `EtlJobView`: owner-safe API record.
- `EtlJobClaim`: internal payload-bearing claimed record.
- `EtlJobProperties`: enabled, poll delay, lease duration, maximum attempts.

- [ ] **Step 1: Write failing model and bounds tests**

Cover lowercase status mapping, unknown/null status rejection, record null invariants, property defaults, and unsafe configuration bounds.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobDomainTest,EtlJobPropertiesTest test`
Expected: compilation failure because the job model does not exist.

- [ ] **Step 3: Implement records, enum, and configuration**

Use immutable records, `Instant`, `UUID`, constructor invariants, and `@ConfigurationProperties(prefix = "xtrmetl.etl.jobs")`.

- [ ] **Step 4: Enable properties and scheduling**

Add `@EnableScheduling` and register `EtlJobProperties` in `EtlApplication`.

- [ ] **Step 5: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobDomainTest,EtlJobPropertiesTest test`
Expected: all selected tests pass.

### Task 4: Durable store port and PostgreSQL adapter

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobStore.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/PostgresEtlJobStore.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/PostgresEtlJobStoreTest.java`

**Interfaces:**
- `Optional<EtlJobView> findBySubmission(String principalHash, String submissionHash)`
- `EtlJobView insertPending(UUID jobId, String principalHash, String submissionHash, String digest, String payload)`
- `Optional<EtlJobView> findOwned(UUID jobId, String principalHash)`
- `Optional<EtlJobClaim> claimNext(String leaseOwner, Duration leaseDuration)`
- `void markSucceeded(UUID jobId, String leaseOwner, String responseBody)`
- `void markFailed(UUID jobId, String leaseOwner, String failureCode)`
- `void releaseForRetry(UUID jobId, String leaseOwner)`

- [ ] **Step 1: Write failing SQL adapter tests**

Use a capturing `JdbcTemplate` to verify parameter order, row mapping, no string interpolation, `FOR UPDATE SKIP LOCKED`, `CURRENT_TIMESTAMP`, lease-owner predicates, payload clearing, and zero-update stale-owner failures.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=PostgresEtlJobStoreTest test`
Expected: compilation failure because store classes do not exist.

- [ ] **Step 3: Implement the port and adapter**

Use only parameterized JDBC. Claim with a data-modifying CTE and return one row. Throw `IllegalStateException` when terminal or retry updates do not affect exactly one owned row.

- [ ] **Step 4: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=PostgresEtlJobStoreTest test`
Expected: all adapter tests pass.

### Task 5: Submission and owner-scoped retrieval service

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobService.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobServiceTest.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlRequestError.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/service/EtlRequestExceptionTest.java`

**Interfaces:**
- `EtlJobView submit(String payload, String idempotencyKey, String principalName)`
- `EtlJobView get(UUID jobId, String principalName)`
- New stable 404 error: `JOB_NOT_FOUND` / `etl_job_not_found`.

- [ ] **Step 1: Write failing request-error and service tests**

Cover prevalidation-before-lock, new insert, same-key replay, same-key/different-payload conflict, lock contention, cross-principal separation, malformed principal, and indistinguishable missing/foreign jobs.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobServiceTest,EtlRequestExceptionTest test`
Expected: compilation failure because the service and job error do not exist.

- [ ] **Step 3: Implement the stable job-not-found error**

Use HTTP 404, `urn:mightyetl:problem:etl-job-not-found`, fixed title/detail, and no identifiers.

- [ ] **Step 4: Implement submission and retrieval**

Use domain-separated hashes (`job-principal`, `job-submission`, `job-payload`), the shared key normalizer, `EtlService.validateData`, and `EtlRequestLock.tryLock`. Insert a random UUID only after validation, lock, and replay lookup.

- [ ] **Step 5: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobServiceTest,EtlRequestExceptionTest test`
Expected: all selected tests pass.

### Task 6: Transactional job executor

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobExecutor.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobExecutorTest.java`

**Interfaces:**
- `void execute(EtlJobClaim claim)` with `@Transactional`.

- [ ] **Step 1: Write failing executor tests**

Prove internal idempotency uses job UUID and principal hash, success records the exact response, and stale lease failure propagates to roll back the transaction boundary.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobExecutorTest test`
Expected: compilation failure because the executor does not exist.

- [ ] **Step 3: Implement minimal executor**

Call `EtlService.processDataIdempotently(claim.requestPayload(), claim.jobRecordId().toString(), claim.principalScopeHash())`, then `markSucceeded` with the current lease token.

- [ ] **Step 4: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobExecutorTest test`
Expected: all selected tests pass.

### Task 7: Scheduled worker and retry policy

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobWorker.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobWorkerTest.java`

**Interfaces:**
- `void poll()` scheduled with property-backed fixed delay and initial delay.

- [ ] **Step 1: Write failing worker tests**

Cover disabled mode, no claim, successful execute, retryable in-progress conflict, transient failure below and at ceiling, deterministic typed failure, unexpected runtime failure, and stale lease update propagation.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobWorkerTest test`
Expected: compilation failure because the worker does not exist.

- [ ] **Step 3: Implement one-claim fixed-delay worker**

Generate an opaque process prefix once and a UUID lease token per claim. Never log payload, principal, key, or hashes. Release or fail through lease-owner predicates.

- [ ] **Step 4: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobWorkerTest test`
Expected: all selected tests pass.

### Task 8: HTTP resource and problem boundary

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/controller/EtlJobController.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/controller/EtlJobControllerTest.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/controller/EtlApiProblemHandler.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/controller/EtlControllerTest.java`

**Interfaces:**
- `POST /api/etl/jobs`
- `GET /api/etl/jobs/{jobRecordId}`

- [ ] **Step 1: Write failing controller tests**

Cover 202, JSON body, `Location`, `Cache-Control: no-store`, same-job replay, missing key, missing principal, GET success, malformed UUID 404, foreign/missing 404, and no leakage.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobControllerTest test`
Expected: compilation failure because the controller does not exist.

- [ ] **Step 3: Implement the controller**

Use `ResponseEntity.accepted()`, relative status URI, JSON records, and explicit no-store headers. Parse UUID inside the controller and map malformed input to `JOB_NOT_FOUND`.

- [ ] **Step 4: Extend controller advice scope**

Include both ETL controllers without broadening to unrelated services.

- [ ] **Step 5: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobControllerTest,EtlControllerTest test`
Expected: all selected tests pass.

### Task 9: Flyway migration and configuration

**Files:**
- Create: `etl-service/src/main/resources/db/migration/V2__create_etl_job_records.sql`
- Modify: `etl-service/src/main/resources/application.yml`
- Modify: `docker-compose.yml`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/EtlJobSchemaDocumentationTest.java`

**Interfaces:**
- Produces: `etl_job_records` schema and `xtrmetl.etl.jobs.*` runtime configuration.

- [ ] **Step 1: Write failing migration/config contract tests**

Assert all schema identifiers are multi-word snake_case, exact status/check values, unique and claim indexes, payload-terminal constraint, property defaults, and compose environment names.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobSchemaDocumentationTest test`
Expected: failure because migration and settings do not exist.

- [ ] **Step 3: Add migration and configuration**

Use explicit names for all constraints and indexes. Add `ETL_JOB_ENABLED`, `ETL_JOB_POLL_DELAY_MS`, `ETL_JOB_LEASE_DURATION_SECONDS`, and `ETL_JOB_MAX_ATTEMPTS`.

- [ ] **Step 4: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobSchemaDocumentationTest test`
Expected: all schema/config tests pass.

### Task 10: Operator documentation and changelog

**Files:**
- Create: `docs/etl/durable-async-jobs.md`
- Modify: `docs/api/problem-details.md`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/EtlJobDocumentationTest.java`

**Interfaces:**
- Produces: complete public and operator contract with APA 7th references.

- [ ] **Step 1: Write failing documentation alignment tests**

Require 202 status-monitor semantics, Location, owner isolation, job states, SKIP LOCKED, lease recovery, payload clearing, retry ceiling, no-store, no cancellation claim, and exact environment variables.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobDocumentationTest test`
Expected: failure because documentation is absent or incomplete.

- [ ] **Step 3: Write documentation and changelog**

Keep standards distinctions explicit and references in APA 7th form.

- [ ] **Step 4: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlJobDocumentationTest test`
Expected: all documentation tests pass.

### Task 11: Full verification and PR readiness

**Files:**
- Review all changed files.

- [ ] **Step 1: Run focused ETL module tests**

Run: `./mvnw -B -pl etl-service test`
Expected: zero failures and zero skipped tests.

- [ ] **Step 2: Run full reactor tests**

Run: `./mvnw -B test`
Expected: all modules pass.

- [ ] **Step 3: Run package/build verification**

Run: `./mvnw -B clean verify`
Expected: successful reactor build.

- [ ] **Step 4: Inspect diff and naming**

Confirm no single-word database object identifiers, no payload/principal/key logging, no mutable public collections, no missing Javadocs, and no unrelated dependency drift.

- [ ] **Step 5: Open ready PR only after exact-head checks**

Create the PR as draft for external RED/GREEN evidence, then mark ready only after the current head passes CI, Dependency Review, SBOM, Semgrep, Security Scan, CodeRabbit, and review-thread inspection.
