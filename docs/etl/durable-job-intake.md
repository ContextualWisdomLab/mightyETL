# Durable asynchronous ETL jobs

## Scope and activation

`POST /api/etl/jobs` creates a durable, authenticated-principal-scoped ETL job resource. A separate
lease-fenced worker claims accepted jobs across replicas, replays or writes the durable response
ledger, writes target rows, and commits terminal state atomically.

Both capabilities are fail-closed:

```text
mightyetl.etl.jobs.intake-enabled=false
mightyetl.etl.jobs.worker.enabled=false
```

The supported legacy aliases use the `xtrmetl.*` namespace. Environment variables are
`ETL_JOB_INTAKE_ENABLED` and `ETL_JOB_WORKER_ENABLED`. When both full namespaces are supplied,
`mightyetl.*` wins. Intake may be enabled alone for a controlled retention window, and the worker may
be enabled alone to drain accepted work. The existing synchronous `POST /api/etl/process` endpoint
remains unchanged.

## Submit a job

```http
POST /api/etl/jobs HTTP/1.1
Authorization: Basic <credentials>
Content-Type: application/json
Idempotency-Key: "550e8400-e29b-41d4-a716-446655440000"

[{"id":"record_alpha","name":"accepted"}]
```

The service requires the same authenticated principal, the same semantic idempotency key, and
byte-for-byte identical JSON text for every retry. The preferred header representation is an RFC
9651 quoted String. The legacy raw safe-ASCII profile remains accepted and normalizes to the same
semantic key.

A new or replayed submission returns RFC 9110 `202 Accepted`; acceptance does not mean processing is
complete. The `Location` header identifies the owner-scoped status monitor:

```http
HTTP/1.1 202 Accepted
Location: /api/etl/jobs/{job_record_id}
Cache-Control: no-store
Idempotency-Replayed: false
Content-Type: application/json

{
  "jobRecordId": "cf4f083f-8c90-4f34-a8b6-b53761de44ef",
  "jobStatus": "PENDING",
  "statusUrl": "/api/etl/jobs/cf4f083f-8c90-4f34-a8b6-b53761de44ef"
}
```

All successful and covered problem responses include `Cache-Control: no-store`. A replay returns the
same job identifier and `Idempotency-Replayed: true`. Reusing one principal-scoped key with different
JSON returns `422 etl_job_submission_key_reused`. A concurrent creation attempt that cannot acquire
the transaction-level submission lock returns `409 etl_job_submission_in_progress`.

## Read job status

```http
GET /api/etl/jobs/{job_record_id} HTTP/1.1
Authorization: Basic <credentials>
```

The query binds the current principal hash and job identifier. A malformed, missing, or foreign-owned
identifier returns the same `404 etl_job_not_found`, preventing tenant-existence probing.

The representation exposes only the opaque job identifier, stable lifecycle state, bounded attempt
count, stable failure code where applicable, status URL, and timestamps. It excludes request payload,
raw principal, raw submission key, internal hashes, lease identifiers, SQL, and response-ledger data.

## Lifecycle and distribution

Flyway migrations create descriptive multi-word `snake_case` objects:

- `V2__create_etl_job_records.sql` creates `etl_job_records` and the submission uniqueness contract;
- `V3__add_etl_job_lease_fencing.sql` adds `lease_claim_id`, `lease_owner_id`,
  `lease_expires_at`, lifecycle constraints, and `etl_job_claim_eligibility_index`.

The stable lifecycle is `PENDING`, `RUNNING`, `SUCCEEDED`, and `FAILED`.

Each fixed-delay poll handles at most one job. PostgreSQL, not scheduler uniqueness, distributes work:

1. rows at the attempt limit are terminalized and their payloads are cleared;
2. one oldest eligible `PENDING` row or expired `RUNNING` row is selected with
   `FOR UPDATE SKIP LOCKED`;
3. a fresh claim identifier, process owner identifier, database-derived expiry, and incremented
   attempt count are persisted;
4. execution verifies the retained payload digest and acquires a domain-separated response-ledger
   lock derived only from stored hashes;
5. an existing matching response is replayed, or target rows and `etl_idempotency_records` are written;
6. `SUCCEEDED` is committed only for the exact unexpired claim in the same transaction.

If the lease is expired or superseded, the final transition fails and rolls back target and ledger
writes. An expired row can be reclaimed with a new claim identifier. A stale worker therefore cannot
commit duplicate target effects or terminalize a newer owner's work.

## Retry and failure behavior

Transient database failures return the job to `PENDING` while attempts remain. At the configured
limit they become `FAILED` with `etl_target_unavailable`. Non-transient database failures use
`etl_target_failure`. Persisted payload or ledger identity conflicts use
`etl_job_integrity_failure`. Unexpected runtime failures use `etl_internal_error`. Deterministic ETL
validation retains its existing stable `etl_*` request code. Eligible rows already at the attempt
limit use `etl_worker_attempts_exhausted`.

Every retry or terminal transition repeats the exact live lease predicate. A zero-row transition is
stale evidence and does not overwrite the authoritative owner.

## Validation, privacy, and retention

Before submission lock or table access, mightyETL enforces configured UTF-8 payload and record-count
bounds. The complete body must be a JSON array, duplicate JSON fields are rejected, every element
must be an object with a safe textual `id`, and normalized field names must remain unique.

The database stores an opaque UUID, SHA-256 hashes of principal scope, semantic submission key, and
exact JSON text, the retained request payload while nonterminal, lifecycle and attempt fields, and
lease metadata while running. Raw principal names and raw idempotency keys are never persisted.

The request payload inherits the source records' data classification. Database constraints require a
payload for nonterminal rows and require it to be null for terminal rows. Success, deterministic
failure, attempts exhaustion, and non-retryable failure clear the payload in their terminal
transition. Apply least privilege, encryption, backup, restore, and retention controls while data is
retained.

Metrics and ordinary logs must not include payloads, principals, client keys, hashes, job or lease
identifiers, SQL, exception messages, or unbounded error classes. Operational procedures and metric
contracts are authoritative in `docs/operations/durable-job-worker.md`.

## Standards basis

- RFC 9110 Section 15.3.3 defines `202 Accepted` as noncommittal and recommends a current-status
  representation and status monitor.
- RFC 9457 supplies deterministic problem-details representations.
- RFC 9651 defines the accepted Structured Fields String syntax.
- PostgreSQL 18 documents `SKIP LOCKED` as unsuitable for a general consistent view but useful for
  avoiding contention among multiple consumers of a queue-like table.
- Spring fixed-delay scheduling measures each delay from completion of the preceding invocation.
- OpenTelemetry SQL/PostgreSQL semantic conventions define stable database telemetry fields; raw
  query text and parameters remain privacy-sensitive opt-in data.

### References

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor.
https://www.rfc-editor.org/rfc/rfc9110

Nottingham, M., & Wilde, E. (2023). *Problem details for HTTP APIs* (RFC 9457). RFC Editor.
https://www.rfc-editor.org/rfc/rfc9457

Nottingham, M., & Kamp, P. (2024). *Structured field values for HTTP* (RFC 9651). RFC Editor.
https://www.rfc-editor.org/rfc/rfc9651

OpenTelemetry Authors. (2026). *OpenTelemetry semantic conventions 1.43.0: Semantic conventions for
SQL databases client operations*. Cloud Native Computing Foundation.
https://opentelemetry.io/docs/specs/semconv/db/sql/

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: SELECT*.
https://www.postgresql.org/docs/18/sql-select.html

Spring Authors. (2026). *Task execution and scheduling*. Broadcom.
https://docs.spring.io/spring-framework/reference/integration/scheduling.html
