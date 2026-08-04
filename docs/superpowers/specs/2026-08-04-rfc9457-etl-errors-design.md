# RFC 9457 ETL Error Contract Design

## Goal

Replace ad-hoc plain-text ETL failures with a stable, non-leaking RFC 9457 `application/problem+json` contract while preserving the existing successful `200 text/plain` response from `POST /api/etl/process`.

## Scope

This slice covers failures that reach the synchronous ETL request endpoint. Connector-catalog responses and other services retain their current contracts. Authentication, authorization, routing, gateway, proxy, container, and response-negotiation failures remain governed by the component that rejects the request. The design does not add asynchronous jobs, idempotency, or durable retry state.

## Error taxonomy

| Error code | HTTP status | Meaning | Client-visible detail |
|:-----------|------------:|:--------|:----------------------|
| `etl_payload_too_large` | 413 | UTF-8 payload exceeds the configured byte limit | The ETL request payload exceeds the configured limit. |
| `etl_batch_too_large` | 413 | JSON array exceeds the configured record limit | The ETL request contains too many records. |
| `etl_invalid_json` | 400 | Body is null, malformed JSON, contains an exact duplicate JSON field, or is not a top-level array | The request body must be a valid JSON array. |
| `etl_invalid_record` | 422 | A record, identifier, normalized output key, or transform input violates the ETL semantic contract | One or more ETL records violate the request contract. |
| `etl_target_unavailable` | 503 | A transient target data-access failure remains after retries | The ETL target is temporarily unavailable. |
| `etl_target_failure` | 500 | A deterministic target data-access failure occurs | The ETL target could not process the request. |
| `etl_internal_error` | 500 | An unexpected runtime failure escapes the ETL service's typed boundaries | The ETL request could not be processed. |

Exact duplicate JSON fields are classified as malformed JSON because Jackson rejects them while parsing, before a record tree exists. Distinct field names that collide after locale-independent uppercase normalization are valid JSON but invalid ETL records and therefore return 422.

Every covered problem response includes:

- `type`: stable `urn:mightyetl:problem:<slug>` URI;
- `title`: stable human-readable category;
- `status`: HTTP status;
- `detail`: generic non-sensitive guidance;
- `instance`: the request URI path without query data;
- `errorCode`: stable snake_case machine code.

Exception messages, SQL details, credentials, file paths, stack traces, and nested causes are never copied into the HTTP body.

## Architecture

`EtlService` raises a typed `EtlRequestException` for admission and semantic-input failures. The exception carries only an `EtlRequestError` enum; diagnostic exception causes remain server-side. Spring `DataAccessException` values retain their native type so transaction and retry infrastructure can classify them correctly.

`EtlController` invokes the service inside a narrow boundary. It rethrows typed request and data-access exceptions unchanged and wraps only other `RuntimeException` values raised by the service in `EtlUnexpectedException`. The successful `text/plain` representation is selected only after processing succeeds, allowing clients that accept only `application/problem+json` to receive covered failures without an early 406.

`EtlApiProblemHandler` is a focused `@RestControllerAdvice(assignableTypes = EtlController.class)` that maps typed request failures, body-conversion failures, Spring `DataAccessException` subclasses, and `EtlUnexpectedException` to `ProblemDetail`. It deliberately has no broad `Exception` handler, so framework-owned method, routing, and response-negotiation errors retain their native status semantics.

## Compatibility

- Successful response status, body, and content type remain unchanged.
- Covered error responses intentionally change from plain text to `application/problem+json`.
- The machine-readable `errorCode`, HTTP status, media type, and problem type are the stable compatibility surface; prose may be clarified without changing semantics.
- Existing `mightyetl.etl.*` and `xtrmetl.etl.*` configuration remains unchanged.
- Failures rejected before the controller boundary are not promised to contain an ETL `errorCode`.

## Security and privacy

- Problem bodies use fixed definitions and never raw exception messages.
- `instance` contains only the servlet request URI and excludes query strings.
- Unexpected service exceptions are represented to clients as `etl_internal_error`; logs record only the path and exception class rather than the exception message.
- 413 and 422 classifications prevent callers from mistaking deterministic input rejection for target outages.
- The dedicated unexpected-failure wrapper prevents unrelated MVC failures from being mislabeled as ETL internal errors.

## Testing

Tests must prove:

1. success remains `200 text/plain`;
2. every typed request failure maps to the expected status, media type, type URI, title, detail, instance, and `errorCode`;
3. transient and non-transient data-access failures map to 503 and 500 respectively;
4. raw exception text containing a synthetic secret never appears in the response;
5. unexpected service exceptions cross a dedicated wrapper and map to the generic 500 problem;
6. problem-only clients can receive covered failures without a handler-selection 406;
7. the service emits the correct typed exception for payload, batch, JSON, exact duplicate-field, and semantic-record failures;
8. added production statements and branches reach 100% focused coverage.

## Documentation

Add `docs/api/problem-details.md`, update README error semantics, and record the contract in `CHANGELOG.md` under `Unreleased`. No release is cut until the complete repository release gate is green.
