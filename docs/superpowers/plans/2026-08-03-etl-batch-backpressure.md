# ETL Batch Backpressure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent oversized or concurrent ETL requests from exhausting shared JVM and database resources while preserving the existing `/api/etl/process` contract.

**Architecture:** Add typed ETL processing properties and a dedicated bounded executor with caller-runs backpressure. Keep transformation/result ordering compatible, validate the complete batch before scheduling work, and move `EtlService` to constructor injection so the execution policy is explicit and independently testable.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring configuration properties, `ThreadPoolExecutor`, JUnit 5, Mockito.

## Global Constraints

- Keep `etl-service` independently runnable and compatible with the existing gateway/MSA deployment.
- Preserve the current endpoint and response text for accepted batches.
- Default maximum records per request: `1000`.
- Default worker count: `min(8, availableProcessors)`, with a minimum of `1`.
- Default executor queue capacity: `1024`.
- Use a bounded queue and caller-runs backpressure; do not use the JVM common pool.
- Validate all record IDs before scheduling transformations so malformed batches perform no database writes.
- Use `mightyetl.*` as the preferred operator prefix while preserving `xtrmetl.*` compatibility.
- Add complete Javadocs and focused branch coverage for every new class and behavior.

---

### Task 1: Processing policy and executor configuration

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/config/EtlProcessingProperties.java`
- Create: `etl-service/src/main/java/com/xtrmetl/etl/config/EtlExecutorConfiguration.java`
- Test: `etl-service/src/test/java/com/xtrmetl/etl/config/EtlExecutorConfigurationTest.java`

**Interfaces:**
- Produces: `EtlProcessingProperties#getMaxBatchRecords()`, `getMaxConcurrency()`, and `getQueueCapacity()`.
- Produces: Spring bean `etlExecutor` of type `java.util.concurrent.ExecutorService`.

- [ ] **Step 1: Write failing configuration tests**

Test defaults, explicit values, invalid non-positive values, bounded queue capacity, named worker threads, and caller-runs rejection behavior.

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlExecutorConfigurationTest test`
Expected: compilation failure because the properties/configuration classes do not exist.

- [ ] **Step 3: Implement typed properties and bounded executor**

Use `ArrayBlockingQueue`, a fixed-size `ThreadPoolExecutor`, a named thread factory, and `ThreadPoolExecutor.CallerRunsPolicy`. Reject invalid configuration before constructing the executor.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlExecutorConfigurationTest test`
Expected: all focused tests pass.

### Task 2: Batch preflight and dedicated execution

**Files:**
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlService.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/service/EtlServiceTest.java`

**Interfaces:**
- Consumes: `@Qualifier("etlExecutor") Executor` and `EtlProcessingProperties`.
- Preserves: `public String processData(String data)`.

- [ ] **Step 1: Write failing service tests**

Add tests proving oversized batches are rejected before writes, all records require a non-null scalar `id`, malformed later records cause zero writes, the supplied executor is used, and result order matches input order.

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./mvnw -B -pl etl-service -Dtest=EtlServiceTest test`
Expected: new assertions fail against common-pool scheduling and per-record late validation.

- [ ] **Step 3: Implement preflight and executor injection**

Move dependencies to constructor injection. Parse and validate the complete array and record IDs before creating futures. Reject batches larger than `maxBatchRecords`. Pass the dedicated executor to every `CompletableFuture.supplyAsync` call and retain ordered result collection.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=EtlServiceTest test`
Expected: all service tests pass with no common-pool use.

### Task 3: Configuration surface and compatibility alias

**Files:**
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/EtlApplication.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/config/MightyEtlConfigAliasEnvironmentPostProcessor.java`
- Modify: `etl-service/src/main/resources/application.yml`
- Test: `etl-service/src/test/java/com/xtrmetl/etl/config/MightyEtlConfigAliasEnvironmentPostProcessorTest.java`

**Interfaces:**
- Produces operator keys `xtrmetl.etl.max-batch-records`, `xtrmetl.etl.max-concurrency`, and `xtrmetl.etl.queue-capacity`.
- Mirrors preferred `mightyetl.etl.*` keys to the legacy prefix.

- [ ] **Step 1: Write failing alias tests**

Require all three ETL policy keys to participate in modern/legacy dual-read precedence.

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./mvnw -B -pl etl-service -Dtest=MightyEtlConfigAliasEnvironmentPostProcessorTest test`
Expected: new ETL keys are absent from `RELATIVE_KEYS`.

- [ ] **Step 3: Bind and document the properties**

Enable `EtlProcessingProperties`, add the alias keys, and expose environment-variable-backed defaults in `application.yml`.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `./mvnw -B -pl etl-service -Dtest=MightyEtlConfigAliasEnvironmentPostProcessorTest test`
Expected: all alias tests pass.

### Task 4: Operator documentation and release notes

**Files:**
- Modify: `README.md`
- Modify: `TRD.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Documents: batch limit, worker count, queue capacity, caller-runs backpressure, and compatibility defaults.

- [ ] **Step 1: Update operator documentation**

Describe the three environment variables and the rejection/backpressure semantics without claiming transactional atomicity or exactly-once behavior.

- [ ] **Step 2: Record the change under Unreleased**

Add concise `Added`/`Changed` entries in `CHANGELOG.md`; do not cut a release until the full release checklist and all checks are green.

- [ ] **Step 3: Run documentation tests**

Run: `./mvnw -B -pl etl-service -Dtest=DocumentationValidationTest test`
Expected: all documentation consistency tests pass.

### Task 5: Full verification

**Files:**
- No new files.

- [ ] **Step 1: Run all repository tests**

Run: `./mvnw -B test`
Expected: zero failures and zero errors across all modules.

- [ ] **Step 2: Review the diff for secrets and compatibility breaks**

Confirm no credentials, generated artifacts, or changed endpoint payload formats are present.

- [ ] **Step 3: Open a focused pull request**

Target `develop`, request full CI/security checks, and merge only after all required checks and actionable reviews are resolved.
