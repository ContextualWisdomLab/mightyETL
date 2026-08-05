# Durable job polling advisory

## Purpose

Authenticated clients poll `GET /api/etl/jobs/{job_record_id}` while a durable ETL job is active. Without a machine-readable cadence, independently implemented clients tend to use arbitrary tight loops that waste control-plane capacity and produce inconsistent operator behavior.

mightyETL emits the RFC 9110 `Retry-After` response field only when both conditions hold:

1. the owner-scoped status representation is `PENDING` or `RUNNING`; and
2. durable-job execution is explicitly enabled.

The field is advisory: it does not grant authority, alter the job state machine, extend a worker lease, or replace client-side exponential backoff and jitter. Intake-only maintenance mode does not emit a processing cadence because no local worker is available to advance accepted jobs.

## Wire contract

An active job with the worker enabled returns the existing operator-safe status representation, `Cache-Control: no-store`, and a whole-second polling delay:

```http
HTTP/1.1 200 OK
Cache-Control: no-store
Retry-After: 5
Content-Type: application/json

{
  "jobRecordId": "cf4f083f-8c90-4f34-a8b6-b53761de44ef",
  "jobStatus": "RUNNING",
  "attemptCount": 1,
  "createdAt": "2026-08-05T01:00:00Z",
  "updatedAt": "2026-08-05T01:00:05Z"
}
```

`SUCCEEDED` and `FAILED` are terminal and omit `Retry-After`. An active job also omits the field when `mightyetl.etl.jobs.worker.enabled=false`, preventing a maintenance-mode intake deployment from advertising a cadence that cannot advance work. Submission responses, list responses, problem responses, and unrelated controllers are unchanged.

## Cadence derivation

The response advice reads the validated `xtrmetl.etl.jobs.worker.enabled` and `xtrmetl.etl.jobs.worker.fixed-delay-milliseconds` values. The preferred `mightyetl.*` configuration namespace continues to map through the existing compatibility alias processor. The delay is already bounded from one millisecond through one day.

When the worker is enabled, the wire value is calculated as:

```text
retry_after_seconds = ceil(fixed_delay_milliseconds / 1000)
```

Fractional seconds are rounded upward, so one millisecond through one second advertises `Retry-After: 1`, 1,001 milliseconds advertises `Retry-After: 2`, and the one-day maximum advertises `Retry-After: 86400`. This prevents a valid positive scheduler delay from becoming a zero-second tight-polling instruction.

The worker schedule remains a local execution setting rather than a completion promise. Queue depth, target latency, retries, process restarts, and lease recovery can make a job remain active for multiple polling intervals. When execution is disabled, the absent field signals that the service has no active local cadence to advertise; clients must use their maintenance-window or operator-escalation policy instead of tight polling.

## Security and privacy boundary

The polling field contains only a bounded integer. It exposes no request payload, authenticated principal, idempotency key, internal hash, job or lease identifier, SQL, exception message, target identity, or queue depth.

The response still requires authenticated owner scope. A malformed, absent, or foreign-owned identifier returns the same owner-safe not-found problem and does not receive an active-job polling advisory. The advice is scoped to `EtlJobController` and modifies only `EtlJobStatusResponse` bodies.

## Client guidance

Clients should:

1. treat `Retry-After` as the minimum delay before the next status request;
2. add bounded jitter when many jobs are polled concurrently;
3. apply a larger local backoff after transport failures or `429`/`503` responses;
4. stop polling when the returned state is `SUCCEEDED` or `FAILED`;
5. treat an absent field on an active job as a maintenance or externally managed execution condition and use an operator-approved fallback interval;
6. retain their own overall timeout and operator escalation policy.

Clients must not interpret the field as a guarantee that the job will finish within one interval.

## Rollback

The slice adds no database object, migration, persisted state, or API body field. Rolling back the application removes the advisory header while preserving submission, list, status, worker, and lease behavior. Older clients already operate without the header; newer clients must tolerate its absence because HTTP response fields are optional unless a separate client contract makes them mandatory.

## Standards basis

RFC 9110 Section 10.2.3 defines `Retry-After` as either an HTTP-date or a non-negative decimal number of seconds indicating how long a user agent ought to wait before a follow-up request. mightyETL uses the bounded `delay-seconds` form because it is deterministic, timezone-independent, and directly derived from an enabled worker's configured cadence.

### Reference

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110
