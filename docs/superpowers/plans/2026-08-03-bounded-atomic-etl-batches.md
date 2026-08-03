# Bounded Atomic ETL Batches Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace unbounded common-pool fan-out with a bounded, prevalidated, transaction-scoped ETL batch pipeline while preserving the existing `/api/etl/process` contract.

**Architecture:** `EtlService` parses and validates the entire request before the first database write, transforms records directly from the JSON tree without delimiter round-trips, and writes the validated records inside one Spring transaction. `EtlBatchProperties` provides operator-controlled UTF-8 payload and record-count limits using the existing `xtrmetl.*` configuration namespace.

**Tech Stack:** Java 25, Spring Boot 3.5, Spring Transactions, Spring Retry, Jackson, JdbcTemplate, JUnit 5, Mockito.

## Global Constraints

- Preserve `POST /api/etl/process` and its newline-delimited `Processed: <id>` response.
- Preserve the `processed_data` database object name and parameterized SQL.
- Reject oversized or structurally invalid batches before any JDBC call.
- Do not use `CompletableFuture.commonPool()` or create one task per input record.
- Use UTF-8 byte length, not Java character count, for payload enforcement.
- Keep defaults conservative: `max-payload-bytes=1048576`, `max-batch-records=1000`.
- Every new production type and public method requires Javadoc and focused tests.

---

### Task 1: Define the batch-safety contract

**Files:**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/service/EtlServiceBatchSafetyTest.java`

**Interfaces:**
- Consumes: existing `EtlService.processData(String)` and `JdbcTemplate.update(String, Object...)`.
- Produces: executable requirements for `EtlBatchProperties` and the three-argument `EtlService` constructor.

- [ ] **Step 1: Write failing tests**

Add tests that instantiate `new EtlService(jdbcTemplate, new ObjectMapper(), properties)` and verify:

```java
assertThrows(RuntimeException.class, () -> service.processData(oversizedPayload));
verifyNoInteractions(jdbcTemplate);
```

```java
assertThrows(RuntimeException.class, () -> service.processData(batchOverRecordLimit));
verifyNoInteractions(jdbcTemplate);
```

```java
assertThrows(RuntimeException.class, () -> service.processData(batchWhoseSecondRecordHasNoId));
verifyNoInteractions(jdbcTemplate);
```

```java
service.processData("[{\"id\":\"1\",\"name\":\"A:B,C\"}]");
verify(jdbcTemplate).update(
    "INSERT INTO processed_data (data) VALUES (?)",
    "ID:1,NAME:A:B,C,"
);
```

- [ ] **Step 2: Run the focused tests and confirm RED**

Run:

```bash
./mvnw -B -pl etl-service -Dtest=EtlServiceBatchSafetyTest test
```

Expected: compilation failure because `EtlBatchProperties` and the three-argument constructor do not exist.

- [ ] **Step 3: Commit the failing contract**

```bash
git add etl-service/src/test/java/com/xtrmetl/etl/service/EtlServiceBatchSafetyTest.java
git commit -m "test(etl): define bounded atomic batch contract"
```

### Task 2: Add operator-configurable limits

**Files:**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlBatchProperties.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/EtlApplication.java`
- Modify: `etl-service/src/main/resources/application.yml`

**Interfaces:**
- Produces: `EtlBatchProperties#getMaxPayloadBytes()` and `getMaxBatchRecords()`.
- Consumes: environment variables `ETL_MAX_PAYLOAD_BYTES` and `ETL_MAX_BATCH_RECORDS`.

- [ ] **Step 1: Implement the configuration properties**

Create a `@ConfigurationProperties(prefix = "xtrmetl.etl")` class with positive defaults and setters that reject values below `1` using `IllegalArgumentException`.

- [ ] **Step 2: Register the properties**

Change:

```java
@EnableConfigurationProperties(ConnectorProperties.class)
```

to:

```java
@EnableConfigurationProperties({ConnectorProperties.class, EtlBatchProperties.class})
```

- [ ] **Step 3: Add environment-backed YAML defaults**

```yaml
xtrmetl:
  etl:
    max-payload-bytes: ${ETL_MAX_PAYLOAD_BYTES:1048576}
    max-batch-records: ${ETL_MAX_BATCH_RECORDS:1000}
```

- [ ] **Step 4: Run the focused tests**

Run the Task 1 command. Expected: tests compile but fail on missing service behavior.

- [ ] **Step 5: Commit**

```bash
git add etl-service/src/main/java/com/xtrmetl/etl/service/EtlBatchProperties.java \
  etl-service/src/main/java/com/xtrmetl/etl/EtlApplication.java \
  etl-service/src/main/resources/application.yml
git commit -m "feat(etl): configure bounded batch limits"
```

### Task 3: Replace unbounded fan-out with prevalidated transactional processing

**Files:**
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlService.java`

**Interfaces:**
- Consumes: `EtlBatchProperties`, `ObjectMapper`, `JdbcTemplate`.
- Produces: the unchanged `public String processData(String data)` API.

- [ ] **Step 1: Add constructor injection and transaction scope**

Use:

```java
public EtlService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        @Nullable EtlBatchProperties batchProperties
)
```

Default a null test-time properties argument to `new EtlBatchProperties()`. Add `@Transactional` to `processData` while retaining `@Retryable`.

- [ ] **Step 2: Enforce UTF-8 payload and record limits**

Before transforming records, reject requests whose UTF-8 byte length exceeds `maxPayloadBytes` or whose array size exceeds `maxBatchRecords`.

- [ ] **Step 3: Prevalidate and transform every record**

Create a private immutable record:

```java
private record ProcessedRecord(String id, String data) {}
```

Require every array element to be an object with a non-null, non-blank `id`. Transform fields directly from Jackson nodes; do not serialize and split on `,` or `:`.

- [ ] **Step 4: Make amount formatting deterministic**

Use `BigDecimal` with `setScale(2, RoundingMode.HALF_UP)` and `toPlainString()`. Invalid or blank amounts remain `0.00`, preserving the legacy response contract without locale dependence.

- [ ] **Step 5: Write only after the whole batch validates**

After transformation succeeds for all records, execute the existing parameterized insert once per record inside the transaction, then construct the response from the prevalidated IDs.

- [ ] **Step 6: Run focused and existing service tests**

```bash
./mvnw -B -pl etl-service -Dtest=EtlServiceBatchSafetyTest,EtlServiceTest test
```

Expected: all tests pass and no JDBC call occurs for rejected batches.

- [ ] **Step 7: Commit**

```bash
git add etl-service/src/main/java/com/xtrmetl/etl/service/EtlService.java
git commit -m "fix(etl): process bounded batches atomically"
```

### Task 4: Document and verify the production boundary

**Files:**
- Modify: `README.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Produces: operator documentation for payload and record limits.

- [ ] **Step 1: Document the safety limits**

Add the two environment variables, defaults, rejection behavior, transaction boundary, and the fact that transformation is deterministic rather than common-pool fan-out.

- [ ] **Step 2: Update the changelog**

Add an Unreleased entry describing bounded request handling, prevalidation, deterministic amount formatting, and transaction-scoped writes.

- [ ] **Step 3: Run the full reactor**

```bash
./mvnw -B test
```

Expected: `BUILD SUCCESS`, zero failures, and zero errors.

- [ ] **Step 4: Commit**

```bash
git add README.md CHANGELOG.md
git commit -m "docs(etl): document bounded atomic batches"
```
