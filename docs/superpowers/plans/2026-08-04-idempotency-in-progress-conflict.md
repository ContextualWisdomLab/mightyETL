# Idempotency In-Progress Conflict Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Return an immediate RFC 9457 409 problem when a concurrent request is already processing the same principal-scoped idempotency key.

**Architecture:** Replace the blocking transaction advisory-lock abstraction with a non-blocking Boolean acquisition contract. The PostgreSQL adapter uses `pg_try_advisory_xact_lock`; `EtlService` maps an unavailable lock to a stable typed request error before ledger or target access while preserving committed replay and atomic transaction semantics.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring JDBC, PostgreSQL transaction advisory locks, JUnit 5, Mockito, H2 integration tests.

## Global Constraints

- Preserve unkeyed `200 text/plain` behavior.
- Preserve completed keyed replay and same-key/different-payload 422 behavior.
- Return concurrent same-principal/same-semantic-key requests as RFC 9457 `409 Conflict`.
- Never expose raw keys, principal names, payloads, hashes, SQL, or exception messages.
- Use descriptive two-word-or-longer snake_case database identifiers; no schema change is required.
- Preserve Spring transaction fail-closed behavior and modular standalone deployment.
- Add beginner-readable Javadocs and focused statement/branch coverage for every new path.
- Record the behavior change under `Unreleased` in `CHANGELOG.md`.

---

### Task 1: Stable in-progress error classification

**Files:**
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlRequestError.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/service/EtlRequestExceptionTest.java`

**Interfaces:**
- Produces: `EtlRequestError.IDEMPOTENCY_REQUEST_IN_PROGRESS` with status 409 and machine code `etl_idempotency_request_in_progress`.

- [ ] **Step 1: Write the failing enum contract test**

Add an expected row containing:

```java
Arguments.of(
    EtlRequestError.IDEMPOTENCY_REQUEST_IN_PROGRESS,
    HttpStatus.CONFLICT,
    "etl_idempotency_request_in_progress",
    "urn:mightyetl:problem:etl-idempotency-request-in-progress",
    "ETL idempotency request in progress",
    "A request with the same principal-scoped Idempotency-Key is still being processed."
)
```

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlRequestExceptionTest test`
Expected: compilation failure because the enum constant does not exist.

- [ ] **Step 3: Implement the enum constant**

Add the fixed metadata to `EtlRequestError` without deriving text from an exception.

- [ ] **Step 4: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlRequestExceptionTest test`
Expected: all enum and exception contract tests pass.

### Task 2: Non-blocking request-lock contract

**Files:**
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlRequestLock.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/PostgresEtlRequestLock.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/service/PostgresEtlRequestLockTest.java`

**Interfaces:**
- Produces: `boolean EtlRequestLock.tryLock(String idempotencyKeyHash)`.
- PostgreSQL SQL: `SELECT pg_try_advisory_xact_lock(?)`.

- [ ] **Step 1: Write failing adapter tests**

Require the SQL above, verify `true` and `false` are returned faithfully, verify malformed hashes never touch JDBC, and verify a null database Boolean throws `IllegalStateException`.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=PostgresEtlRequestLockTest test`
Expected: compilation or assertion failures because the current API blocks and returns void.

- [ ] **Step 3: Implement minimal non-blocking acquisition**

Change the interface to `tryLock`. In the PostgreSQL adapter, derive the existing signed 64-bit key and use `JdbcTemplate.queryForObject(LOCK_SQL, Boolean.class, advisoryKey)`. Return the Boolean after a non-null check.

- [ ] **Step 4: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=PostgresEtlRequestLockTest test`
Expected: all adapter tests pass.

### Task 3: Service-level conflict before database work

**Files:**
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlService.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/service/EtlServiceIdempotencyConflictTest.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/service/EtlServiceIdempotencyIntegrationTest.java`
- Modify any existing test lock lambdas or fakes that implement `EtlRequestLock`.

**Interfaces:**
- Consumes: `EtlRequestLock.tryLock`.
- Produces: `EtlRequestException(IDEMPOTENCY_REQUEST_IN_PROGRESS)` when acquisition returns false.

- [ ] **Step 1: Write a failing focused conflict test**

Construct `EtlService` with a lock returning `false`, invoke the idempotent method inside a `TransactionTemplate`, and assert the exact error classification. Verify no ledger lookup or target insert occurs after the lock call.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlServiceIdempotencyConflictTest test`
Expected: failure because a false lock result is not represented.

- [ ] **Step 3: Implement the conflict boundary**

After hash and digest derivation, call `tryLock`. Throw the typed conflict before `findStoredIdempotencyRecord` when false. Keep all later replay/write behavior unchanged.

- [ ] **Step 4: Update integration lock adapters**

Use immediate `ReentrantLock.tryLock()` semantics in the test adapter and retain transaction-completion release. Replace the old blocking concurrency expectation with deterministic focused coverage: one transaction holds the key, a concurrent transaction receives the typed 409 classification, and a later retry after commit replays the stored response.

- [ ] **Step 5: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlServiceIdempotencyConflictTest,EtlServiceIdempotencyIntegrationTest test`
Expected: conflict, replay, rollback, and principal isolation tests pass.

### Task 4: Public contract and documentation

**Files:**
- Modify: `docs/etl/idempotent-retries.md`
- Modify: `docs/api/problem-details.md`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/documentation/EtlProblemDetailsDocumentationTest.java`

**Interfaces:**
- Documents: 409 code/type/detail, immediate non-blocking semantics, retry guidance, no `Retry-After`, and the accepted 64-bit advisory-prefix collision tradeoff.

- [ ] **Step 1: Write failing documentation assertions**

Require `etl_idempotency_request_in_progress`, `409`, `pg_try_advisory_xact_lock`, immediate conflict, and retry-with-backoff guidance in the operator docs and changelog.

- [ ] **Step 2: Verify RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlProblemDetailsDocumentationTest test`
Expected: documentation assertions fail.

- [ ] **Step 3: Update documentation and changelog**

State that simultaneous same-key requests no longer wait, completed retries still replay, completion time is unknown so no `Retry-After` is emitted, and a rare advisory-prefix collision can cause a false 409 but not a wrong replay.

- [ ] **Step 4: Verify GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlProblemDetailsDocumentationTest test`
Expected: documentation alignment tests pass.

### Task 5: Exact-head verification and merge

**Files:**
- No additional production files.

- [ ] **Step 1: Run focused ETL tests**

Run: `./mvnw -B -pl etl-service test`
Expected: zero failures and errors.

- [ ] **Step 2: Run full reactor**

Run: `./mvnw -B test`
Expected: all modules pass.

- [ ] **Step 3: Open a focused pull request**

Request CI, Dependency Review, SBOM, Semgrep, Security Scan, CodeRabbit, and review-thread validation.

- [ ] **Step 4: Merge only exact-head green**

Confirm Ubuntu, macOS, Windows, all security jobs, SBOM, dependency review, CodeRabbit, mergeability, and unresolved threads against the same head SHA before squash merge.
