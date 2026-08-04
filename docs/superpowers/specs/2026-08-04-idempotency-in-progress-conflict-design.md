# Idempotency In-Progress Conflict Design

## Goal

Prevent one concurrent retry from occupying a request thread while another transaction processes the same principal-scoped idempotency key. A duplicate that arrives before the original transaction completes returns an immediate RFC 9457 `409 Conflict`; a retry after completion still replays the committed response.

## Scope

This slice changes only keyed synchronous ETL requests. Unkeyed requests, committed-response replay, payload-conflict detection, transaction atomicity, and the durable ledger schema remain unchanged. It does not add asynchronous jobs, polling endpoints, cancellation, or automatic retention.

## Standards basis

The expired IETF HTTPAPI Idempotency-Key draft describes a concurrent retry as a resource conflict and recommends HTTP 409 with a problem response. PostgreSQL exposes `pg_try_advisory_xact_lock(bigint)`, which returns immediately with `true` when an exclusive transaction-level advisory lock is acquired and `false` when the lock is unavailable. RFC 9110 defines 409 for conflicts with the current state of the target resource.

The Internet-Draft is design evidence rather than a published RFC. The public documentation must retain that distinction.

## API contract

Add the stable request classification:

| Field | Value |
|:------|:------|
| HTTP status | `409 Conflict` |
| `errorCode` | `etl_idempotency_request_in_progress` |
| `type` | `urn:mightyetl:problem:etl-idempotency-request-in-progress` |
| `title` | `ETL idempotency request in progress` |
| `detail` | `A request with the same principal-scoped Idempotency-Key is still being processed.` |

The response uses the existing RFC 9457 controller advice and does not include the raw key, principal, payload, lock key, SQL, or exception message. No `Retry-After` header is emitted because completion time is unknown. Clients may retry the same semantic key and identical decoded JSON text with bounded backoff and jitter.

## Architecture

`EtlRequestLock` becomes a non-blocking acquisition contract:

```java
boolean tryLock(String idempotencyKeyHash);
```

`PostgresEtlRequestLock` derives the same signed 64-bit advisory key as today and executes:

```sql
SELECT pg_try_advisory_xact_lock(?)
```

It returns the database Boolean result and fails closed if the database unexpectedly returns `null`.

`EtlService.processDataIdempotently` performs the existing validation and active-transaction checks, derives the principal-scoped key hash and payload digest, then calls `tryLock`. A `false` result raises `IDEMPOTENCY_REQUEST_IN_PROGRESS` before ledger lookup or target writes. A `true` result preserves the existing lookup, replay, payload-conflict, write, ledger, and commit sequence.

## Concurrency semantics

- First request acquires the transaction-level lock and proceeds.
- A concurrent request for the same principal-scoped semantic key receives 409 immediately.
- A request using the same client key under a different principal scope uses a different hash and is unaffected.
- A later retry after commit acquires the lock and replays the stored response.
- Transaction rollback releases the lock automatically; a later retry can become the new first execution.
- A rare 64-bit advisory-prefix collision may cause a false 409 between unrelated full hashes, but cannot replay or overwrite the wrong ledger row because the full 256-bit hash remains the primary key. This accepted tradeoff must remain documented.

## Testing

Tests must prove:

1. the request-error enum exposes the exact stable 409 metadata;
2. `PostgresEtlRequestLock` uses `pg_try_advisory_xact_lock`, returns true/false faithfully, rejects malformed hashes before JDBC, and rejects a null JDBC Boolean;
3. the service raises the 409 classification before ledger lookup or target writes when lock acquisition fails;
4. a later retry after a successful commit still replays;
5. controller parameterized problem tests include the new error automatically;
6. documentation lists the 409 response, retry guidance, non-blocking behavior, and accepted advisory-prefix collision tradeoff;
7. all added production statements and branches have focused tests.

## Compatibility and rollout

The change intentionally alters only simultaneous same-key behavior from waiting to immediate 409. Completed replay behavior remains compatible. No database migration is required. The change is recorded under `Unreleased` in `CHANGELOG.md`.

## References

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110

Jena, J., & Dalal, S. (2025). *The Idempotency-Key HTTP header field* (draft-ietf-httpapi-idempotency-key-header-07, expired April 18, 2026). Internet Engineering Task Force. https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/

PostgreSQL Global Development Group. (2026). *System administration functions: Advisory lock functions*. PostgreSQL 18 documentation. https://www.postgresql.org/docs/current/functions-admin.html
