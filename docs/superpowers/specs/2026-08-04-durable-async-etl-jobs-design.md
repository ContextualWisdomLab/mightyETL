# Durable Asynchronous ETL Jobs Design

## Goal

Add a durable, principal-scoped asynchronous ETL resource that survives client disconnects and service restarts, supports horizontally scaled workers, prevents duplicate target effects, and gives clients a stable status monitor.

## Scope

This vertical slice adds:

- `POST /api/etl/jobs` for validated job submission;
- `GET /api/etl/jobs/{job_record_id}` for owner-scoped status retrieval;
- a PostgreSQL-backed job record and lease state machine;
- a fixed-delay worker that claims one record at a time with `FOR UPDATE SKIP LOCKED`;
- exact-once target effects through the existing durable idempotency ledger;
- bounded attempts, expired-lease recovery, and stable terminal failure codes;
- API, migration, privacy, lease, retry, and retention documentation.

This slice does not add cancellation, result pagination, arbitrary target selection, bulk job listing, application-level payload encryption, or a web UI. Those are separate state-machine and product surfaces.

## Standards basis

RFC 9110 defines `202 Accepted` as a noncommittal response for work accepted but not completed. Its representation ought to describe current status and point to a status monitor. The submission response therefore contains the pending job representation and a `Location` header pointing to the owner-scoped job resource.

PostgreSQL 18 documents `SKIP LOCKED` as unsuitable for general-purpose reads but suitable for avoiding lock contention among multiple consumers of a queue-like table. The claim transaction therefore uses `FOR UPDATE SKIP LOCKED`, while all ownership and terminal-state rules remain explicit application invariants.

Spring fixed-delay scheduling measures the delay from completion of one invocation to the start of the next. The scheduler only wakes workers; PostgreSQL claim semantics own cross-instance work distribution. Duplicate scheduler beans or multiple service replicas do not imply duplicate ownership.

The expired IETF HTTPAPI Idempotency-Key draft is used as design evidence for client-generated submission keys and payload fingerprints. It is not represented as a published RFC.

## API contract

### Submit a job

```http
POST /api/etl/jobs HTTP/1.1
Authorization: Basic <credentials>
Content-Type: application/json
Idempotency-Key: "550e8400-e29b-41d4-a716-446655440000"

[{"id":"record_alpha","name":"accepted"}]
```

A valid new submission returns:

```http
HTTP/1.1 202 Accepted
Location: /api/etl/jobs/7e21d6b8-bcf8-4dc8-931c-2ec1a8fa3d20
Content-Type: application/json
Cache-Control: no-store

{
  "jobRecordId": "7e21d6b8-bcf8-4dc8-931c-2ec1a8fa3d20",
  "jobStatus": "pending",
  "statusUrl": "/api/etl/jobs/7e21d6b8-bcf8-4dc8-931c-2ec1a8fa3d20",
  "attemptCount": 0,
  "submittedAt": "2026-08-04T10:30:00Z",
  "startedAt": null,
  "completedAt": null,
  "responseBody": null,
  "failureCode": null
}
```

`Idempotency-Key` is required for this new endpoint. The service accepts the same quoted RFC 9651 String and retained legacy raw profile as the synchronous endpoint.

- Same principal, semantic key, and identical decoded JSON text returns the same job representation and `Location`.
- Same principal and semantic key with different decoded JSON text returns `422 etl_idempotency_key_reused`.
- A simultaneous same-key submission that cannot acquire the existing nonblocking request lock returns `409 etl_idempotency_request_in_progress`.
- The request is fully parsed and prevalidated before any job row is created.

### Read a job

```http
GET /api/etl/jobs/{job_record_id}
```

The authenticated owner receives `200 application/json` and `Cache-Control: no-store`. A missing job, malformed UUID, or job owned by another principal returns the same `404 etl_job_not_found` problem so the API does not disclose cross-principal existence.

### Stable job states

- `pending`: available for claim;
- `running`: owned by one lease;
- `succeeded`: target effects and response committed;
- `failed`: terminal stable failure code recorded.

No other state string is valid.

## Database model

Flyway migration `V2__create_etl_job_records.sql` creates `etl_job_records` with descriptive multi-word snake_case names:

- `job_record_id UUID` primary key;
- `principal_scope_hash CHAR(64)` owner namespace hash;
- `submission_key_hash CHAR(64)` client semantic-key hash;
- `request_digest CHAR(64)` decoded JSON text digest;
- `request_payload TEXT` nullable after completion;
- `job_status VARCHAR(16)`;
- `attempt_count INTEGER`;
- `lease_owner_id VARCHAR(128)`;
- `lease_expires_at TIMESTAMPTZ`;
- `response_body TEXT`;
- `failure_code VARCHAR(128)`;
- `submitted_at`, `started_at`, `completed_at`, and `updated_at` timestamps.

A unique constraint on `(principal_scope_hash, submission_key_hash)` makes replay lookup durable. Checks constrain hashes, status values, nonnegative attempts, lease-field consistency, and terminal payload clearing. Indexes support owner lookup and oldest-eligible claim order.

Raw principal names and raw client keys are never stored. The request payload is stored only while pending or running and is set to `NULL` in the same transaction that commits `succeeded` or `failed`.

## Component boundaries

### `EtlIdempotencyKey`

A focused utility owns quoted/legacy key normalization and SHA-256 helpers. The synchronous and asynchronous paths use the same semantic-key rules without duplicating regexes.

### `EtlService`

Adds a side-effect-free `validateData` method that applies the same payload, JSON, record-count, identifier, duplicate-field, and transformation validation as synchronous processing. The job submission path calls it before persistence. Existing synchronous behavior remains unchanged.

### `EtlJobStore`

A port that owns durable job records. It exposes submission lookup/insert, owner-scoped retrieval, one-record claim, terminal success/failure, and retry release. The interface documents optimistic lease-owner predicates.

### `PostgresEtlJobStore`

The PostgreSQL adapter uses parameterized JDBC only. Claiming is one data-modifying CTE that selects the oldest pending or expired-running row with `FOR UPDATE SKIP LOCKED`, assigns a new lease owner and expiry, increments attempts, and returns the claimed record.

### `EtlJobService`

Owns client submission and retrieval. It validates the body, normalizes the key, hashes principal/key/payload with domain separation, takes the existing transaction advisory try-lock, replays an existing job when the digest matches, rejects digest conflicts, and inserts a new UUID job.

### `EtlJobExecutor`

A separate Spring bean owns one execution transaction. It calls `EtlService.processDataIdempotently` with the job UUID as the internal semantic key and the stored principal hash as its namespace, then marks success using both job ID and current lease owner. Target rows, durable response ledger, job success, and payload clearing commit atomically. A stale lease owner update count of zero aborts the transaction.

### `EtlJobWorker`

A fixed-delay scheduled bean creates an opaque per-process worker ID and a fresh lease owner token for each claim. It claims at most one job per invocation. On success it delegates to the executor. On failure it uses a separate store transaction:

- in-progress idempotency conflict or transient target failure below the attempt ceiling: release to `pending`;
- deterministic request failure: mark `failed` with its stable code;
- exhausted transient failure: mark `failed` as `etl_target_unavailable`;
- unexpected runtime failure: mark `failed` as `etl_internal_error`.

All updates require the current lease owner token. A stale worker cannot alter a reclaimed job.

## Lease semantics

- The default lease is five minutes and must be positive.
- A pending row or running row whose `lease_expires_at` is earlier than database `CURRENT_TIMESTAMP` is eligible.
- Claiming commits before target work begins.
- A reclaimed worker receives a new lease token.
- If an old worker reaches terminal update after reclaim, its update affects zero rows and its transaction rolls back, including target effects and the internal idempotency ledger.
- If the new worker collides with the old worker’s internal transaction lock, it releases the job to pending for a later attempt.

## Error and privacy boundaries

- Submission and retrieval never return raw payloads, principal names, hashes, lease identifiers, SQL, or exception messages.
- `GET` uses indistinguishable 404 behavior for missing, malformed, and foreign-owned IDs.
- Terminal failures expose only a fixed `failureCode` selected from existing ETL machine codes.
- API responses use `Cache-Control: no-store`.
- Server logs may include job UUID and stable code, but never payload, principal, raw key, or hashes.

## Configuration

`EtlJobProperties` binds under `xtrmetl.etl.jobs` with the preferred `mightyetl.etl.jobs` alias:

- `enabled` default `true`;
- `poll-delay-ms` default `1000`, minimum `100`;
- `lease-duration-seconds` default `300`, minimum `30`;
- `max-attempts` default `5`, range `1..100`.

Scheduling remains enabled even when job execution is disabled; the worker method returns without database access when `enabled=false`.

## Testing

Tests must prove:

1. key normalization is shared and compatible with the synchronous endpoint;
2. job properties reject unsafe values;
3. submission prevalidation occurs before lock/store access;
4. replay, payload conflict, simultaneous submission conflict, and principal isolation;
5. controller `202`, `Location`, no-store, owner retrieval, and indistinguishable 404 behavior;
6. PostgreSQL SQL uses parameterized statements, `FOR UPDATE SKIP LOCKED`, database time, lease-owner predicates, and payload clearing;
7. claim mapping for pending and reclaimed rows;
8. executor success is atomic and stale lease ownership aborts;
9. worker retry, failure, disabled, no-job, and attempt-ceiling branches;
10. migration identifiers and constraints follow the multi-word snake_case rule;
11. documentation and changelog match the public contract;
12. all added production statements and branches have focused tests and no ignored or skipped tests.

## Rollout and compatibility

The existing `POST /api/etl/process` contract is unchanged. Migration V2 is additive. The async worker can be disabled before rollout while the API remains unavailable with a documented service configuration; this slice defaults it on for a complete standalone product. Older application versions ignore the additive table.

The `Unreleased` changelog records the API, migration, and worker behavior. This feature alone does not justify a versioned release because cancellation, queue observability, and production PostgreSQL load evidence remain subsequent commercial-readiness gates.

## References

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110

Jena, J., & Dalal, S. (2025). *The Idempotency-Key HTTP header field* (Internet-Draft draft-ietf-httpapi-idempotency-key-header-07; expired April 18, 2026). Internet Engineering Task Force. https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/

PostgreSQL Global Development Group. (2026). *SELECT*. PostgreSQL 18 documentation. https://www.postgresql.org/docs/18/sql-select.html

Spring Team. (2026). *Task execution and scheduling*. Spring Framework reference documentation. https://docs.spring.io/spring-framework/reference/integration/scheduling.html
