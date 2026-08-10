# Durable asynchronous ETL job intake

## Scope

`POST /api/etl/jobs` creates a durable, authenticated-principal-scoped ETL job resource. Durable
execution is implemented as a separate opt-in worker boundary: mightyETL executes at most one
eligible durable job per worker poll, while PostgreSQL owns cross-replica claim arbitration through
`FOR UPDATE SKIP LOCKED`, lease fencing, bounded attempts, and exact conditional lifecycle
transitions. Authenticated operators can list recent jobs in their own principal namespace through
deterministic keyset pagination.

Both externally reachable intake and background execution remain disabled by default. Durable job
intake is disabled by default and is absent unless an operator explicitly sets the preferred
`mightyetl.etl.jobs.intake-enabled=true` property, its supported legacy alias
`xtrmetl.etl.jobs.intake-enabled=true`, or `ETL_JOB_INTAKE_ENABLED=true`. The worker is disabled by
default and does not poll until an operator explicitly enables either the preferred
`mightyetl.etl.jobs.worker.enabled=true` property or its supported legacy alias
`xtrmetl.etl.jobs.worker.enabled=true`. When both full namespaces are supplied, `mightyetl.*` wins.
Keeping intake and execution as separate opt-ins allows an operator to stage schema and API rollout
without silently starting background target writes.

The existing synchronous `POST /api/etl/process` endpoint remains unchanged.

## Submit a job

A client sends:

```http
POST /api/etl/jobs HTTP/1.1
Authorization: Basic <credentials>
Content-Type: application/json
Idempotency-Key: "550e8400-e29b-41d4-a716-446655440000"

[{"id":"record_alpha","name":"accepted"}]
```

The service requires:

- the same authenticated principal for every retry;
- the same semantic `Idempotency-Key`; and
- byte-for-byte same JSON text for every retry of that key.

The preferred header representation is an RFC 9651 quoted String. The legacy raw safe-ASCII profile
remains accepted for compatibility and normalizes to the same semantic key.

A new or replayed durable submission returns RFC 9110 `202 Accepted` because acceptance does not mean
that processing has completed. The representation describes the current state and the `Location`
header identifies the status monitor:

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

All successful and covered problem responses for durable job resources include
`Cache-Control: no-store`. These authenticated operational resources must not be retained by shared
or private caches.

A retry that resolves to the same durable resource returns the same job identifier and
`Idempotency-Replayed: true`. Reusing the same principal-scoped key with different JSON text returns
`422 etl_job_submission_key_reused`. A concurrent creation attempt that cannot acquire the
transaction-level submission lock returns `409 etl_job_submission_in_progress` rather than waiting
without a client-visible bound.

## List owned jobs

```http
GET /api/etl/jobs?limit=50 HTTP/1.1
Authorization: Basic <credentials>
```

The endpoint returns only jobs owned by the same authenticated principal. Rows are ordered by
`created_at DESC, job_record_id DESC`; the UUID is a deterministic tie-breaker when multiple jobs
share one database timestamp. The default page size is 50 and the canonical accepted range is 1
through 100. Values such as `0`, `101`, `01`, signed values, whitespace-padded values, or non-decimal
text fail with `400 etl_invalid_job_page_limit` before table access.

The service fetches one additional row beyond the requested page size. That row is never returned;
it only proves that another page exists. When a following page exists, the response body contains an
opaque cursor and RFC 8288 Web Linking advertises the same continuation target:

```http
HTTP/1.1 200 OK
Cache-Control: no-store
Link: </api/etl/jobs?limit=50&cursor=eyJ2IjoxLCJ0IjoiMjAyNi0wOC0wOVQwMzowMDowMFoiLCJpIjoiY2Y0ZjA4M2YtOGM5MC00ZjM0LWE4YjYtYjUzNzYxZGU0NGVmIn0>; rel="next"
Content-Type: application/json
```

The cursor is a canonical unpadded Base64 URL encoding of the final returned creation timestamp and
job identifier. Clients must treat it as opaque. Each next-page query independently binds the
current authenticated principal hash and applies a strict tuple boundary equivalent to “older
creation timestamp, or the same timestamp with a lower UUID.” Cursor contents never grant authority
and contain no payload, raw principal, submission key, or internal hash. Malformed, oversized,
incomplete, non-canonical, or stale-format cursors fail closed with
`400 etl_invalid_job_page_cursor` before database access. A terminal or empty page omits both the
next cursor and the `Link` header.

Pagination guarantees no duplicate or omitted rows while traversing an unchanged dataset. Concurrent
insertions are visible according to their ordering position; an operational cursor is not a frozen
snapshot. Consumers that require a legally frozen audit set must use an explicit transactional or
warehouse snapshot.

## Read job status

```http
GET /api/etl/jobs/{job_record_id} HTTP/1.1
Authorization: Basic <credentials>
```

The service hashes the current authenticated principal and queries by both principal scope and job
identifier. A malformed or missing identifier and an identifier owned by another principal all
return `404 etl_job_not_found`; callers cannot use this endpoint to probe another tenant's job
existence.

The response excludes the request payload, raw principal, raw submission key, and all internal
hashes. Timestamps are explicit ISO-8601 strings. A newly accepted job begins as `PENDING`. When the
worker claims it, the database records `RUNNING`, a lease owner, an opaque lease token, lease expiry,
and the incremented attempt count. Terminal success or failure clears the retained request payload.

## Worker execution and lease fencing

The scheduled worker claims at most one eligible row per poll. The claim repository serializes the
selection in PostgreSQL with `FOR UPDATE SKIP LOCKED`, so concurrent replicas do not wait on or
execute the same currently claimable row. Eligibility includes new `PENDING` work and reclaimable
expired `RUNNING` work while the configured maximum attempt count has not been exhausted.

Every mutable lifecycle transition is fenced by the exact claim identity. Success, retry release,
and terminal failure must still match the job record, lease owner, opaque lease token, and valid
lease boundary expected by the caller. A stale or superseded worker therefore cannot overwrite a
newer owner's state. Stale transitions surface as a finite `stale` worker outcome rather than being
silently accepted.

`EtlJobExecutionService` is transactional. It validates the retained job and durable response-ledger
identity, executes or replays the target operation, and then marks the exact live lease `SUCCEEDED`
in the same transaction. If the success transition is stale, Spring rolls back the target and
response-ledger effects from that execution attempt.

Transient Spring data-access failures are released for retry only while attempts remain. Exhausted
transient failures become `etl_target_unavailable`; non-transient data-access failures become
`etl_target_failure`; deterministic request and integrity failures retain their stable application
error codes; and unexpected runtime failures become `etl_internal_error`. The worker does not place
raw payloads, principals, submission keys, job identifiers, lease identifiers, SQL, exception class
names, or exception messages into metric labels.

Worker telemetry uses the fixed terminal outcome vocabulary `idle`, `succeeded`, `retried`, `failed`,
and `stale`. Each completed poll records exactly one outcome and one matching duration sample,
including idle polls and database failures while persisting retry or terminal state.

## Worker configuration

The production worker remains fail-closed until explicitly activated. Supported keys include:

- `mightyetl.etl.jobs.worker.enabled` / `xtrmetl.etl.jobs.worker.enabled`;
- `mightyetl.etl.jobs.worker.fixed-delay-milliseconds` /
  `xtrmetl.etl.jobs.worker.fixed-delay-milliseconds`;
- `mightyetl.etl.jobs.worker.initial-delay-milliseconds` /
  `xtrmetl.etl.jobs.worker.initial-delay-milliseconds`;
- `mightyetl.etl.jobs.worker.lease-duration-seconds` /
  `xtrmetl.etl.jobs.worker.lease-duration-seconds`;
- `mightyetl.etl.jobs.worker.max-attempts` / `xtrmetl.etl.jobs.worker.max-attempts`; and
- `mightyetl.etl.jobs.worker.lease-owner-id` / `xtrmetl.etl.jobs.worker.lease-owner-id`.

Defaults are bounded in production configuration, and property validation rejects non-positive delay,
lease-duration, and attempt settings as well as blank or oversized lease-owner identifiers. Operators
should assign a stable, non-secret owner identifier per worker instance and size lease duration above
normal execution latency while retaining enough margin for crash recovery through expired-lease
reclamation.

## Validation and persistence

Before lock or table access, mightyETL enforces the same configured UTF-8 payload and record-count
bounds used by synchronous ETL admission. The complete body must be a JSON array, duplicate JSON
fields are rejected, every element must be an object with a safe textual `id`, and normalized field
names must remain unique.

Flyway migrations use descriptive multi-word `snake_case` objects:

- `V2__create_etl_job_records.sql` creates `etl_job_records` and the principal-scoped submission
  uniqueness contract;
- worker migrations add the lease-fencing fields and concurrent claim-eligibility index; and
- `V5__add_etl_job_owner_pagination_index.sql` creates `etl_job_owner_pagination_index` on
  `principal_scope_hash`, `created_at DESC`, and `job_record_id DESC`, matching the owner-scoped
  keyset ordering contract.

The database stores an opaque UUID job identifier; SHA-256 hashes of principal scope, semantic
submission key, and exact JSON text; the request payload only while nonterminal; and lifecycle,
attempt, failure, lease-owner/token/expiry, and timestamp fields.

The stable lifecycle vocabulary is `PENDING`, `RUNNING`, `SUCCEEDED`, and `FAILED`. Database checks
require a non-null request payload only for nonterminal states and require the payload to be null for
terminal states. Terminal payload clearing is therefore a persistence invariant, not merely an
application convention.

Raw authenticated principal names and raw idempotency keys are never persisted. The retained request
payload is sensitive operational data and inherits the classification of its source records. While a
job is `PENDING` or `RUNNING`, operators must protect it with database access control, encryption,
backup, and retention policy appropriate to the underlying records.

## Pagination migration and rollback

The V5 owner-pagination index uses PostgreSQL `CREATE INDEX CONCURRENTLY` so inserts, updates, and
deletes remain available while PostgreSQL builds the index. Its migration-local companion file
`V5__add_etl_job_owner_pagination_index.sql.conf` contains `executeInTransaction=false` because
PostgreSQL rejects concurrent index creation inside a transaction block.

Concurrent index creation can wait for transactions and can leave an invalid index after a failed
build. Production rollout therefore requires catalog inspection of index validity and readiness,
plus representative monitoring of duration, I/O, replication lag, and transaction age. A matching
object name alone is not proof that the index is usable.

Older application binaries ignore this additive index. After rolling back binaries that depend on
the list access path, remove the database object outside a transaction block only after list traffic
is withdrawn and an execution-plan review confirms the operational boundary:

```sql
DROP INDEX CONCURRENTLY etl_job_owner_pagination_index;
```

Dropping the index does not change query semantics, but it can turn an owner-scoped list operation
into an unacceptable scan, so removal is an explicit performance rollback rather than an emergency
schema shortcut.

## Operational boundary

Enabling intake alone still does not start background processing; enabling the worker alone does not
create externally submitted jobs. A production deployment that wants asynchronous execution must
intentionally enable both surfaces and apply the worker migrations first. Rollback is operationally
safe by disabling the worker property: existing durable rows remain in PostgreSQL and expired
`RUNNING` leases become reclaimable when a compatible worker is enabled again. Operators must not
manually rewrite lease tokens or terminal status to manufacture recovery.

This slice establishes durable execution, lease fencing, bounded retries, terminal payload clearing,
finite worker telemetry, and owner-scoped keyset pagination. Polling advisories, conditional status
reads, cancellation, and replay remain separate later stack items and must not be represented as part
of this boundary until their own exact-head gates pass.

## Standards basis

- RFC 9110 Section 15.3.3 defines `202 Accepted` as noncommittal and recommends that the response
  describe current status and point to a status monitor.
- RFC 8288 defines the Web Linking model and HTTP `Link` header used for the optional next-page
  relationship.
- RFC 9457 supplies the problem-details representation used by deterministic submission, lookup, and
  execution failures.
- RFC 9651 defines the current Structured Fields String syntax accepted for `Idempotency-Key`.
- The expired IETF HTTPAPI `Idempotency-Key` draft-07 is used only as work-in-progress design
  evidence for unique client keys, request fingerprints, `422` payload conflicts, and tenant-isolation
  security concerns. It expired on April 18, 2026 and is not represented as a published RFC.
- PostgreSQL 18 documents explicit ordering, row-locking semantics, multicolumn B-tree behavior, and
  the availability and recovery trade-offs of concurrent index construction.
- Flyway script configuration supports migration-local transaction overrides required by PostgreSQL
  DDL that cannot execute in a transaction block.

### References

- Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor.
  https://www.rfc-editor.org/rfc/rfc9110
- Jena, J., & Dalal, S. (2025). *The Idempotency-Key HTTP header field*
  (draft-ietf-httpapi-idempotency-key-header-07, expired April 18, 2026). Internet Engineering Task
  Force. https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
- Nottingham, M. (2017). *Web linking* (RFC 8288). RFC Editor.
  https://doi.org/10.17487/RFC8288
- Nottingham, M., & Wilde, E. (2023). *Problem details for HTTP APIs* (RFC 9457). RFC Editor.
  https://www.rfc-editor.org/rfc/rfc9457
- Nottingham, M., & Kamp, P. (2024). *Structured field values for HTTP* (RFC 9651). RFC Editor.
  https://www.rfc-editor.org/rfc/rfc9651
- PostgreSQL Global Development Group. (2026). *CREATE INDEX*. PostgreSQL 18 documentation.
  https://www.postgresql.org/docs/18/sql-createindex.html
- PostgreSQL Global Development Group. (2026). *Multicolumn indexes*. PostgreSQL 18 documentation.
  https://www.postgresql.org/docs/18/indexes-multicolumn.html
- PostgreSQL Global Development Group. (2026). *SELECT*. PostgreSQL 18 documentation.
  https://www.postgresql.org/docs/18/sql-select.html
- Redgate Software. (2026). *Flyway script configuration*. Flyway documentation.
  https://documentation.red-gate.com/flyway/reference/script-configuration
