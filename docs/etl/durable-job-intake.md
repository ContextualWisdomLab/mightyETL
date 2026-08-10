# Durable asynchronous ETL job intake

## Scope

`POST /api/etl/jobs` creates a durable, authenticated-principal-scoped ETL job resource. Durable
execution is now implemented as a separate opt-in worker boundary: mightyETL executes at most one
eligible durable job per worker poll, while PostgreSQL owns cross-replica claim arbitration through
`FOR UPDATE SKIP LOCKED`, lease fencing, bounded attempts, and exact conditional lifecycle
transitions.

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

Flyway migration `V2__create_etl_job_records.sql` creates `etl_job_records`; later worker migrations
add lease-fencing columns and a partial eligibility index for claim scans. All schema objects use
descriptive multi-word `snake_case` names. The database stores:

- an opaque UUID job identifier;
- SHA-256 hashes of the principal scope, semantic submission key, and exact JSON text;
- the request payload only while the job remains nonterminal;
- status, attempt, failure, lease-owner/token/expiry, and lifecycle timestamp fields.

The stable lifecycle vocabulary is `PENDING`, `RUNNING`, `SUCCEEDED`, and `FAILED`. Database checks
require a non-null request payload only for nonterminal states and require the payload to be null for
terminal states. Terminal payload clearing is therefore a persistence invariant, not merely an
application convention.

Raw authenticated principal names and raw idempotency keys are never persisted. The retained request
payload is sensitive operational data and inherits the classification of its source records. While a
job is `PENDING` or `RUNNING`, operators must protect it with database access control, encryption,
backup, and retention policy appropriate to the underlying records.

## Operational boundary

Enabling intake alone still does not start background processing; enabling the worker alone does not
create externally submitted jobs. A production deployment that wants asynchronous execution must
intentionally enable both surfaces and apply the worker migrations first. Rollback is operationally
safe by disabling the worker property: existing durable rows remain in PostgreSQL and expired
`RUNNING` leases become reclaimable when a compatible worker is enabled again. Operators must not
manually rewrite lease tokens or terminal status to manufacture recovery.

This slice establishes durable execution, lease fencing, bounded retries, terminal payload clearing,
and finite worker telemetry. Higher-level job-list pagination, polling advisories, conditional status
reads, cancellation, and replay remain separate later stack items and must not be represented as part
of this boundary until their own exact-head gates pass.

## Standards basis

- RFC 9110 Section 15.3.3 defines `202 Accepted` as noncommittal and recommends that the response
  describe current status and point to a status monitor.
- RFC 9457 supplies the problem-details representation used by deterministic submission, lookup, and
  execution failures.
- RFC 9651 defines the current Structured Fields String syntax accepted for `Idempotency-Key`.
- The expired IETF HTTPAPI `Idempotency-Key` draft-07 is used only as work-in-progress design
  evidence for unique client keys, request fingerprints, `422` payload conflicts, and tenant-isolation
  security concerns. It expired on April 18, 2026 and is not represented as a published RFC.
- PostgreSQL row locking and `SKIP LOCKED` semantics are the database authority for concurrent claim
  behavior; the worker does not attempt to replace that arbitration with process-local locking.

### References

- Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor.
  https://www.rfc-editor.org/rfc/rfc9110
- Jena, J., & Dalal, S. (2025). *The Idempotency-Key HTTP header field*
  (draft-ietf-httpapi-idempotency-key-header-07, expired April 18, 2026). Internet Engineering Task
  Force. https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
- Nottingham, M., & Wilde, E. (2023). *Problem details for HTTP APIs* (RFC 9457). RFC Editor.
  https://www.rfc-editor.org/rfc/rfc9457
- Nottingham, M., & Kamp, P. (2024). *Structured field values for HTTP* (RFC 9651). RFC Editor.
  https://www.rfc-editor.org/rfc/rfc9651
- PostgreSQL Global Development Group. (2026). *SELECT*. PostgreSQL 18 documentation.
  https://www.postgresql.org/docs/18/sql-select.html
