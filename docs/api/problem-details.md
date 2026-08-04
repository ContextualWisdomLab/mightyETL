# ETL API problem details

## Contract

`POST /api/etl/process` uses [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html) Problem Details for HTTP APIs for request-body, validation, idempotency, target, and unexpected application failures that reach the ETL controller boundary. The media type is:

```text
application/problem+json
```

Successful `POST /api/etl/process` responses remain `200 text/plain` with one `Processed: <id>` line per accepted record. Only covered failure responses use the problem-details representation. Keyed successes also return `Idempotency-Replayed: false` for the first committed execution or `Idempotency-Replayed: true` when a prior response is replayed.

Routing, authentication, authorization, gateway, reverse-proxy, and servlet-container failures remain governed by the component that rejects the request. Clients must not assume that a request rejected before the ETL controller will carry an ETL `errorCode`.

Every ETL problem body contains:

| Field | Contract |
|:------|:---------|
| `type` | Stable `urn:mightyetl:problem:<slug>` identifier |
| `title` | Stable human-readable category |
| `status` | HTTP status code |
| `detail` | Fixed non-sensitive client guidance |
| `instance` | Request path without query-string data |
| `errorCode` | Stable snake_case machine code |

The response never includes internal exception text, nested causes, SQL statements, credentials, file-system paths, raw idempotency keys, authenticated principal names, request payloads, or stack traces. Clients should branch on `errorCode`, not parse prose from `title` or `detail`.

## Error catalog

| HTTP | `errorCode` | `type` | Meaning | Client action |
|----:|:------------|:-------|:--------|:--------------|
| 413 | `etl_payload_too_large` | `urn:mightyetl:problem:etl-payload-too-large` | UTF-8 request bytes exceed `max-payload-bytes` | Reduce the request body or request an operator-reviewed limit change. |
| 413 | `etl_batch_too_large` | `urn:mightyetl:problem:etl-batch-too-large` | Record count exceeds `max-batch-records` | Split the array into smaller requests. |
| 400 | `etl_invalid_json` | `urn:mightyetl:problem:etl-invalid-json` | Body is absent, malformed, has an exact duplicate JSON field, or is not a top-level array | Correct the JSON document before retrying. |
| 422 | `etl_invalid_record` | `urn:mightyetl:problem:etl-invalid-record` | A record, identifier, normalized output key, or semantic transform input violates the ETL contract | Correct the record content before retrying. |
| 400 | `etl_invalid_idempotency_key` | `urn:mightyetl:problem:etl-invalid-idempotency-key` | The header is neither a supported quoted RFC 9651 String nor a compatible legacy raw value, or its semantic key is outside the bounded safe profile | Generate a 16-to-128-character high-entropy key using the documented character set and send the preferred quoted representation. |
| 401 | `etl_idempotency_principal_required` | `urn:mightyetl:problem:etl-idempotency-principal-required` | A keyed request reached the controller without an authenticated principal namespace | Authenticate and resend the request with the same JSON text and semantic key. |
| 409 | `etl_idempotency_request_in_progress` | `urn:mightyetl:problem:etl-idempotency-request-in-progress` | Another transaction is still processing the same principal-scoped semantic key | Retry the same key and identical JSON text with bounded exponential backoff and jitter. No `Retry-After` is emitted because completion time is unknown. |
| 422 | `etl_idempotency_key_reused` | `urn:mightyetl:problem:etl-idempotency-key-reused` | The same principal-scoped semantic key already committed a different payload digest | Do not retry with that semantic key; use the original JSON text or generate a new key for a new logical batch. |
| 503 | `etl_target_unavailable` | `urn:mightyetl:problem:etl-target-unavailable` | The target failed with a transient data-access condition after the configured retry policy | Retry with bounded exponential backoff and jitter. For keyed requests, preserve the same semantic key and identical JSON text. |
| 500 | `etl_target_failure` | `urn:mightyetl:problem:etl-target-failure` | The target rejected work with a non-transient data-access failure | Stop automatic retries and involve an operator. |
| 500 | `etl_internal_error` | `urn:mightyetl:problem:etl-internal-error` | An unexpected application failure occurred | Stop automatic retries and involve an operator. |

## Content negotiation

The endpoint does not constrain handler selection to the successful `text/plain` representation. A caller that sends `Accept: application/problem+json` can therefore receive a covered failure response without an early `406` caused by the success media type. Successful responses remain explicitly `text/plain`.

## Example

```http
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "type": "urn:mightyetl:problem:etl-invalid-record",
  "title": "Invalid ETL record",
  "status": 422,
  "detail": "One or more ETL records violate the request contract.",
  "instance": "/api/etl/process",
  "errorCode": "etl_invalid_record"
}
```

The fixed detail deliberately does not reveal which internal parser, validation branch, target constraint, or database implementation produced the failure. Operators should correlate server-side telemetry through the platform tracing and logging context rather than expanding the public response.

## Retry guidance

RFC 9457 standardizes error representation; it does not make retries idempotent. Retry safety depends on the operation's semantics and, for ambiguous ETL outcomes, whether the client supplied a valid principal-scoped idempotency key.

- `400`, `413`, and record-validation `422` responses are deterministic request failures. Correct or split the request; blind retries repeat the same rejection.
- `etl_idempotency_key_reused` is also deterministic. A new logical payload requires a new semantic key.
- `etl_idempotency_request_in_progress` means no request correction is required. Retry the same semantic key and identical JSON text with bounded exponential backoff and jitter.
- `503` represents a transient target failure. Retry only with a bounded attempt count, exponential backoff, and jitter.
- `500` requires operator investigation. Do not automatically retry `500` responses.

An unkeyed retry repeats the whole accepted transaction and can duplicate business effects after an ambiguous transport failure. A keyed retry is principal-scoped and durable: preserve the same semantic key and identical decoded JSON text to replay the prior committed response without another target write. The preferred quoted RFC 9651 representation and the retained legacy raw representation normalize to the same semantic key. Concurrent same-key requests use PostgreSQL `pg_try_advisory_xact_lock` and receive an immediate 409 instead of waiting. See [`docs/etl/idempotent-retries.md`](../etl/idempotent-retries.md) for the complete key, migration, concurrency, and retention contract.

## Compatibility

The stable compatibility surface consists of HTTP status, media type, `type`, and `errorCode`. The existing successful plain-text body is unchanged. Covered error responses intentionally replace the legacy ad-hoc plain-text `400` response with RFC 9457 JSON.

The advice is scoped to `EtlController`; CDC endpoints, connector catalog success responses, and unrelated services do not inherit this error contract automatically.
