# RFC 9457 ETL Error Contract Design

## Goal

Replace ad-hoc plain-text ETL failures with a stable, non-leaking RFC 9457 `application/problem+json` contract while preserving the existing successful `200 text/plain` response from `POST /api/etl/process`.

## Scope

This slice covers only the synchronous ETL request endpoint. Connector-catalog responses and other services retain their current contracts. The design does not add asynchronous jobs, idempotency, or durable retry state.

## Error taxonomy

| Error code | HTTP status | Meaning | Client-visible detail |
|:-----------|------------:|:--------|:----------------------|
| `etl_payload_too_large` | 413 | UTF-8 payload exceeds the configured byte limit | The ETL request payload exceeds the configured limit. |
| `etl_batch_too_large` | 413 | JSON array exceeds the configured record limit | The ETL request contains too many records. |
| `etl_invalid_json` | 400 | Body is null, malformed JSON, or not a top-level array | The request body must be a valid JSON array. |
| `etl_invalid_record` | 422 | A record, identifier, duplicate field, normalized key, or transform input violates the ETL contract | One or more ETL records violate the request contract. |
| `etl_target_unavailable` | 503 | A transient target data-access failure remains after retries | The ETL target is temporarily unavailable. |
| `etl_target_failure` | 500 | A deterministic or unexpected target data-access failure occurs | The ETL target could not process the request. |
| `etl_internal_error` | 500 | An unexpected application failure escapes the typed boundaries | The ETL request could not be processed. |

Every problem response includes:

- `type`: stable `urn:mightyetl:problem:<slug>` URI;
- `title`: stable human-readable category;
- `status`: HTTP status;
- `detail`: generic non-sensitive guidance;
- `instance`: the request URI;
- `errorCode`: stable snake_case machine code.

Exception messages, SQL details, credentials, file paths, stack traces, and nested causes are never copied into the HTTP body.

## Architecture

`EtlService` raises a typed `EtlRequestException` for admission and semantic-input failures. The exception carries only an `EtlRequestError` enum; diagnostic exception causes remain server-side.

`EtlApiProblemHandler` is a focused `@RestControllerAdvice(assignableTypes = EtlController.class)` that maps typed request failures and Spring `DataAccessException` subclasses to `ProblemDetail`. A final handler returns a generic internal-error problem without exposing implementation details.

`EtlController` becomes a thin success-path adapter and no longer catches broad exceptions.

## Compatibility

- Successful response status, body, and content type remain unchanged.
- Error responses intentionally change from plain text to `application/problem+json`.
- The machine-readable `errorCode` is the stable compatibility surface; prose may be clarified without changing semantics.
- Existing `mightyetl.etl.*` and `xtrmetl.etl.*` configuration remains unchanged.

## Security and privacy

- Problem bodies use fixed text selected by enum, never raw exception messages.
- `instance` contains only the servlet request URI and excludes query strings.
- Unknown exceptions are logged by Spring infrastructure but represented to clients as `etl_internal_error`.
- 413 and 422 classifications prevent callers from mistaking deterministic input rejection for target outages.

## Testing

Tests must prove:

1. success remains `200 text/plain`;
2. every typed request failure maps to the expected status, media type, type URI, title, detail, instance, and `errorCode`;
3. transient and non-transient data-access failures map to 503 and 500 respectively;
4. raw exception text containing a synthetic secret never appears in the response;
5. unexpected exceptions map to the generic 500 problem;
6. the service emits the correct typed exception for payload, batch, JSON, and record failures;
7. added production statements and branches reach 100% focused coverage.

## Documentation

Add `docs/api/problem-details.md`, update README error semantics, and record the contract in `CHANGELOG.md` under `Unreleased`. No release is cut until the complete repository release gate is green.