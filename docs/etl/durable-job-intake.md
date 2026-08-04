# Durable asynchronous ETL job intake

## Scope

`POST /api/etl/jobs` creates a durable, authenticated-principal-scoped ETL job resource. This
bounded intake slice persists accepted work and exposes its status monitor; it does not execute jobs
yet. Execution, PostgreSQL `FOR UPDATE SKIP LOCKED` claiming, lease fencing, bounded attempts,
terminal payload clearing, and crash recovery belong to the following worker and lease-fencing slice.

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
Idempotency-Replayed: false
Content-Type: application/json

{
  "jobRecordId": "cf4f083f-8c90-4f34-a8b6-b53761de44ef",
  "jobStatus": "PENDING",
  "statusUrl": "/api/etl/jobs/cf4f083f-8c90-4f34-a8b6-b53761de44ef"
}
```

A retry that resolves to the same durable resource returns the same job identifier and
`Idempotency-Replayed: true`. Reusing the same principal-scoped key with different JSON text returns
`409 etl_job_submission_key_reused`. A concurrent creation attempt that cannot acquire the
transaction-level submission lock returns `409 etl_job_submission_in_progress` rather than waiting
without a client-visible bound.

## Read job status

```http
GET /api/etl/jobs/{job_record_id} HTTP/1.1
Authorization: Basic <credentials>
```

The service hashes the current authenticated principal and queries by both principal scope and job
identifier. A missing identifier and an identifier owned by another principal both return
`404 etl_job_not_found`; callers cannot use this endpoint to probe another tenant's job existence.

The response excludes the request payload, raw principal, raw submission key, and all internal
hashes. Before worker execution is implemented, newly accepted jobs remain `PENDING` with an
`attemptCount` of zero.

## Validation and persistence

Before lock or table access, mightyETL enforces the same configured UTF-8 payload and record-count
bounds used by synchronous ETL admission. The complete body must be a JSON array, duplicate JSON
fields are rejected, every element must be an object with a safe textual `id`, and normalized field
names must remain unique.

Flyway migration `V2__create_etl_job_records.sql` creates `etl_job_records`. All schema objects use
descriptive multi-word `snake_case` names. The database stores:

- an opaque UUID job identifier;
- SHA-256 hashes of the principal scope, semantic submission key, and exact JSON text;
- the request payload needed by the future worker;
- status, attempt, failure, and timestamp fields.

Raw authenticated principal names and raw idempotency keys are never persisted. The request payload
is sensitive operational data and must inherit the classification of its source records. It must be
cleared when a later worker reaches a terminal state; until that worker slice ships, operators must
apply database access control, encryption, backup, and retention policy accordingly.

## Operational boundary

This slice deliberately does not advertise job completion or background execution. Deployments that
need completed asynchronous processing must wait for the worker and lease-fencing slice. The next
slice must claim jobs safely across replicas, fence stale lease owners, commit target effects and
terminal success atomically, reclaim expired leases, bound attempts, publish stable failure codes,
and clear the stored request payload at terminal state.

## Standards basis

- RFC 9110 Section 15.3.3 defines `202 Accepted` as noncommittal and recommends that the response
  describe current status and point to a status monitor.
- RFC 9457 supplies the problem-details representation used by deterministic submission and lookup
  failures.
- RFC 9651 defines the current Structured Fields String syntax accepted for `Idempotency-Key`.
- The expired IETF HTTPAPI `Idempotency-Key` draft-07 is used only as work-in-progress design
  evidence for unique client keys, request fingerprints, conflict handling, and tenant-isolation
  security concerns. It expired on April 18, 2026 and is not represented as a published RFC.

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
