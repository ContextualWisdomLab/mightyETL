# Durable job polling contract

## Purpose

Authenticated clients poll `GET /api/etl/jobs/{job_record_id}` while a durable ETL job is active. Without a machine-readable cadence, independently implemented clients tend to use arbitrary tight loops that waste control-plane capacity and produce inconsistent operator behavior. Without a representation validator, every unchanged poll also retransmits the complete JSON status body.

mightyETL therefore provides two complementary RFC 9110 mechanisms:

- `Retry-After` is emitted only while the owner-scoped status is `PENDING` or `RUNNING`, giving clients a bounded minimum polling delay.
- A weak `ETag` is emitted on every successful owner-scoped status response, allowing an authenticated client to send `If-None-Match` and receive an empty `304 Not Modified` response when the complete operator-visible representation is unchanged.

Neither mechanism grants authority, alters the job state machine, extends a worker lease, authorizes shared-cache storage, or replaces client-side exponential backoff and jitter.

## Wire contract

An active job returns the existing operator-safe status representation, `Cache-Control: no-store`, a weak entity tag, and a whole-second polling delay:

```http
HTTP/1.1 200 OK
Cache-Control: no-store
ETag: W/"86b79e..."
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

The client can explicitly validate that representation on its next owner-authorized request:

```http
GET /api/etl/jobs/cf4f083f-8c90-4f34-a8b6-b53761de44ef HTTP/1.1
Authorization: Bearer <credential>
If-None-Match: W/"86b79e..."
```

When the complete representation is unchanged, Spring MVC applies RFC 9110 weak comparison and returns no representation body:

```http
HTTP/1.1 304 Not Modified
Cache-Control: no-store
ETag: W/"86b79e..."
Retry-After: 5
```

When any represented value changes, including lifecycle state, attempt count, failure code, or update timestamp, the request returns `200 OK`, the current JSON representation, and a new weak tag. `SUCCEEDED` and `FAILED` are terminal and omit `Retry-After` while retaining their validator. Submission responses, list responses, problem responses, and unrelated controllers do not receive a status entity tag.

## Cadence derivation

The response advice reads the validated `xtrmetl.etl.jobs.worker.fixed-delay-milliseconds` value. The preferred `mightyetl.*` configuration namespace continues to map through the existing compatibility alias processor. The value is already bounded from one millisecond through one day.

The wire value is calculated as:

```text
retry_after_seconds = ceil(fixed_delay_milliseconds / 1000)
```

Fractional seconds are rounded upward, so one millisecond through one second advertises `Retry-After: 1`, 1,001 milliseconds advertises `Retry-After: 2`, and the one-day maximum advertises `Retry-After: 86400`. This prevents a valid positive scheduler delay from becoming a zero-second tight-polling instruction.

The worker schedule remains a local execution setting rather than a completion promise. Queue depth, target latency, retries, process restarts, and lease recovery can make a job remain active for multiple polling intervals.

## Entity-tag derivation

The controller first authenticates the caller, parses the opaque UUID, and performs the existing owner-scoped lookup. Only the resulting operator-safe `EtlJobStatusResponse` is eligible for a validator. Every response field is converted to canonical length-prefixed text and then SHA-256 hashed. The hexadecimal digest becomes a weak HTTP entity tag.

Length prefixes prevent adjacent field values from creating ambiguous input. Hashing keeps even the operator-safe values out of the header. The tag changes when any represented field changes and is deterministic across replicas for the same representation. It is intentionally weak because the contract validates semantic status equivalence rather than byte-for-byte transfer encoding.

`Cache-Control: no-store` remains authoritative: intermediaries and clients must not persist the tenant-scoped representation for reuse. The validator supports an explicit authenticated conditional request and does not turn the status endpoint into a publicly cacheable resource.

## Security and privacy boundary

The polling delay contains only a bounded integer. The entity tag contains only a one-way digest of the complete operator-safe status representation. Neither header contains the request payload, raw authenticated principal, idempotency key, internal principal hash, lease identifier, SQL, exception message, target identity, or queue depth.

The response still requires authenticated owner scope. A malformed, absent, or foreign-owned identifier returns the same owner-safe not-found problem and receives neither a status validator nor an active-job polling advisory. Conditional evaluation occurs after that lookup, so `If-None-Match`, including `*`, cannot become a cross-principal existence oracle.

The polling advice is scoped to `EtlJobController` and modifies only `EtlJobStatusResponse` bodies. The entity tag is created only by the controller's single-job status method. Submission, list, problem, and unrelated responses remain outside both mechanisms.

## Client guidance

Clients should:

1. retain the most recent status `ETag` only in process memory when permitted by their own security policy;
2. send that value in `If-None-Match` on the next authenticated status request;
3. treat `304 Not Modified` as confirmation that the previously received representation is still current, not as a new representation;
4. treat `Retry-After` as the minimum delay before the next status request;
5. add bounded jitter when many jobs are polled concurrently;
6. apply a larger local backoff after transport failures or `429`/`503` responses;
7. stop polling when the returned state is `SUCCEEDED` or `FAILED`;
8. retain their own overall timeout and operator escalation policy.

Clients must not interpret either header as a guarantee that the job will finish within one interval or as permission to access another principal's resource.

## Compatibility and rollback

This slice adds no database object, migration, persisted state, API body field, shared cache, or mutation precondition. Existing clients that do not send `If-None-Match` continue receiving the same `200` JSON representation plus ignorable response headers. Conditional clients must tolerate a `200` response with a replacement tag whenever the representation changes or the validator is unavailable after rollback.

Rolling back the application removes the entity-tag builder and conditional response behavior together with the advisory header if the preceding slice is also rolled back. Submission, list, status body, worker, lease, authorization, and persistence behavior remain intact. No database rollback is required.

## Standards basis

RFC 9110 Section 10.2.3 defines `Retry-After` as either an HTTP-date or a non-negative decimal number of seconds indicating how long a user agent ought to wait before a follow-up request. mightyETL uses the bounded `delay-seconds` form because it is deterministic, timezone-independent, and directly derived from the configured worker cadence.

RFC 9110 Sections 8.8 and 13.1.2 define entity tags and require weak comparison for `If-None-Match`. For GET and HEAD, a false `If-None-Match` precondition produces `304 Not Modified`. Spring MVC documents that an `ETag` supplied through `ResponseEntity` participates in conditional request processing and yields an empty `304` response when unchanged.

### References

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110

Spring Framework. (2026). *HTTP caching: Spring Web MVC*. https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/mvc-caching.html
