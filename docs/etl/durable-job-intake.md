# Durable asynchronous ETL jobs

## Scope and activation

`POST /api/etl/jobs` creates a durable, authenticated-principal-scoped ETL job resource. A separate
lease-fenced worker claims accepted jobs across replicas, replays or writes the durable response
ledger, writes target rows, and commits terminal state atomically. Authenticated operators can also
list recent jobs in their own principal namespace through deterministic keyset pagination.

Both intake and execution capabilities are fail-closed:

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

## List owned jobs

```http
GET /api/etl/jobs?limit=50 HTTP/1.1
Authorization: Basic <credentials>
```

The endpoint returns only jobs owned by the same authenticated principal. It orders rows by
`created_at DESC, job_record_id DESC`; the UUID is a deterministic tie-breaker when jobs share one
database timestamp. The default page size is 50 and the accepted canonical range is 1 through 100.
Values such as `0`, `101`, `01`, signed values, whitespace-padded values, and non-decimal text fail
with `400 etl_invalid_job_page_limit` before table access.

The service fetches one additional row beyond the requested page size. That row is not returned; it
only proves that another page exists. When a following page is available, the body includes an opaque
URL-safe cursor and the response advertises the same target through RFC 8288 Web Linking:

```http
HTTP/1.1 200 OK
Cache-Control: no-store
Link: </api/etl/jobs?limit=50&cursor=eyJvcGFxdWUiOiJleGFtcGxlIn0>; rel="next"
Content-Type: application/json

{
  "jobs": [
    {
      "jobRecordId": "cf4f083f-8c90-4f34-a8b6-b53761de44ef",
      "jobStatus": "SUCCEEDED",
      "attemptCount": 1,
      "createdAt": "2026-08-05T01:00:00Z",
      "updatedAt": "2026-08-05T01:00:05Z"
    }
  ],
  "nextCursor": "eyJvcGFxdWUiOiJleGFtcGxlIn0"
}
```

The actual cursor is a canonical unpadded Base64 URL encoding of the last returned creation timestamp
and job identifier. Clients must treat it as opaque. Each following query still binds the current
principal hash and applies a strict tuple boundary equivalent to “older timestamp, or the same
timestamp with a lower UUID.” Cursor contents never grant authority and reveal no payload, principal,
submission key, or hash. Malformed, oversized, incomplete, non-canonical, or stale-format cursors fail
closed with `400 etl_invalid_job_page_cursor` before database access. A terminal or empty page omits
both `nextCursor` and the `Link` header.

Pagination guarantees no duplicates or omissions while traversing an unchanged dataset. Concurrent
insertions are visible according to their ordering position and do not convert a cursor into a
snapshot transaction. Consumers needing a legally frozen audit set must export from an explicit
transactional or warehouse snapshot rather than treating this operational list as one.

## Read job status

```http
GET /api/etl/jobs/{job_record_id} HTTP/1.1
Authorization: Basic <credentials>
```

The query binds the current principal hash and job identifier. A malformed, missing, or foreign-owned
identifier returns the same `404 etl_job_not_found`, preventing tenant-existence probing.

The representation exposes only the opaque job identifier, stable lifecycle state, bounded attempt
count, stable failure code where applicable, and timestamps. It excludes request payload, raw
principal, raw submission key, internal hashes, lease identifiers, SQL, and response-ledger data.

## Lifecycle and distribution

Flyway migrations create descriptive multi-word `snake_case` objects:

- `V2__create_etl_job_records.sql` creates `etl_job_records` and the submission uniqueness contract;
- `V3__add_etl_job_lease_fencing.sql` transactionally adds `lease_claim_id`, `lease_owner_id`,
  `lease_expires_at`, legacy-data repair, and lifecycle constraints;
- `V4__add_etl_job_claim_eligibility_index.sql` concurrently adds
  `etl_job_claim_eligibility_index` for oldest eligible queue-like claims without blocking writers;
- `V5__add_etl_job_owner_pagination_index.sql` concurrently adds
  `etl_job_owner_pagination_index` on `principal_scope_hash`, `created_at DESC`, and
  `job_record_id DESC` for the exact owner-scoped ordering contract.

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

List and status representations exclude payloads, raw principals, raw keys, hashes, lease identifiers,
SQL, and exception messages. Metrics and ordinary logs must not include those values or unbounded
error classes. Operational procedures and metric contracts are authoritative in
`docs/operations/durable-job-worker.md`. Claim-index deployment and invalid-index recovery are
specified in `docs/operations/durable-job-claim-index-rollout.md`.

## Migration and rollback

Apply Flyway migrations in version order. V3 remains transactional so lease columns, legacy-data
repair, and lifecycle constraints commit together. V4 and V5 are isolated additive index migrations.
Both use PostgreSQL `CREATE INDEX CONCURRENTLY` so inserts, updates, and deletes remain available while
the indexes are built. Their matching `.sql.conf` files contain `executeInTransaction=false` because
PostgreSQL rejects concurrent index creation inside a transaction block. The application also sets
`spring.flyway.postgresql.transactional-lock=false`, selecting Flyway's PostgreSQL session-lock mode
required for concurrent index DDL.

Concurrent index creation performs more work and can wait for transactions that could affect an
index. Measure duration, I/O, replication lag, and transaction age in a representative staging
environment, then schedule production rollout with explicit monitoring. If a build fails, PostgreSQL
can leave an invalid index. Inspect catalog validity rather than treating a matching object name or
schema-history row as usable evidence. The claim-index recovery procedure is documented separately;
the same fail-closed catalog inspection, concurrent removal, root-cause correction, approved Flyway
repair, and unchanged migration replay applies to the pagination index.

Application rollback is compatible with either additional index because older binaries ignore them.
After rolling back every binary that depends on the relevant access path, run the database-only
rollback outside a transaction block:

```sql
DROP INDEX CONCURRENTLY etl_job_owner_pagination_index;
DROP INDEX CONCURRENTLY etl_job_claim_eligibility_index;
```

Dropping the pagination index while the list endpoint is active preserves query correctness but can
cause an unacceptable owner-list scan cost. Dropping the claim index while workers are active can
cause expensive claim scans and contention. Do not remove either index until its traffic is withdrawn
and an execution-plan review confirms the rollback boundary.

## Standards basis

- RFC 9110 Section 15.3.3 defines `202 Accepted` as noncommittal and recommends a current-status
  representation and status monitor.
- RFC 8288 defines the Web Linking model and the HTTP `Link` header used for the optional next-page
  relationship.
- RFC 9457 supplies deterministic problem-details representations.
- RFC 9651 defines the accepted Structured Fields String syntax.
- PostgreSQL 18 requires explicit `ORDER BY` for guaranteed result ordering and recommends a unique
  ordering when `LIMIT` is used.
- PostgreSQL 18 documents that equality constraints on leading multicolumn B-tree keys plus a range
  constraint on the next key efficiently limit the scanned index portion.
- PostgreSQL 18 documents that ordinary index construction blocks writes, while concurrent index
  construction preserves writes with additional scans, waits, and invalid-index recovery caveats.
- PostgreSQL 18 documents `SKIP LOCKED` as unsuitable for a general consistent view but useful for
  avoiding contention among multiple consumers of a queue-like table.
- Flyway script configuration supports a migration-matched `.sql.conf` file and the
  `executeInTransaction=false` override required for non-transactional PostgreSQL DDL.
- Flyway's PostgreSQL integration documents session-level migration locking for statements such as
  `CREATE INDEX CONCURRENTLY`.
- Spring fixed-delay scheduling measures each delay from completion of the preceding invocation.
- OpenTelemetry SQL/PostgreSQL semantic conventions define stable database telemetry fields; raw
  query text and parameters remain privacy-sensitive opt-in data.

### References

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor.
https://www.rfc-editor.org/rfc/rfc9110

Nottingham, M. (2017). *Web linking* (RFC 8288). RFC Editor.
https://doi.org/10.17487/RFC8288

Nottingham, M., & Wilde, E. (2023). *Problem details for HTTP APIs* (RFC 9457). RFC Editor.
https://www.rfc-editor.org/rfc/rfc9457

Nottingham, M., & Kamp, P. (2024). *Structured field values for HTTP* (RFC 9651). RFC Editor.
https://www.rfc-editor.org/rfc/rfc9651

OpenTelemetry Authors. (2026). *OpenTelemetry semantic conventions 1.43.0: Semantic conventions for
SQL databases client operations*. Cloud Native Computing Foundation.
https://opentelemetry.io/docs/specs/semconv/db/sql/

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: CREATE INDEX*.
https://www.postgresql.org/docs/18/sql-createindex.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: Introduction to indexes*.
https://www.postgresql.org/docs/18/indexes-intro.html

Redgate Software. (2026). *Flyway PostgreSQL transactional lock setting*.
https://documentation.red-gate.com/fd/flyway-postgresql-transactional-lock-setting-277579114.html

Redgate Software. (2026). *Flyway script configuration*.
https://documentation.red-gate.com/flyway/reference/script-configuration
