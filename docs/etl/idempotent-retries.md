# Idempotent ETL retries

## Purpose

`POST /api/etl/process` changes database state and is not inherently idempotent. A client that
loses the HTTP response cannot otherwise know whether retrying will duplicate the accepted batch.
Clients may opt into durable replay protection by sending an `Idempotency-Key` header.

The unkeyed API remains backward compatible. Idempotency is activated only when the header is
present.

## Client contract

Send all of the following:

- the same authenticated principal;
- the same `Idempotency-Key` value; and
- the byte-for-byte same JSON request body.

The ETL service currently resolves its principal through its implemented Spring Security HTTP Basic
configuration. A deployment may place the service behind a gateway, but that gateway must preserve
or establish downstream authentication that the ETL service itself can verify. Gateway-only Reactor
security context is not a downstream service principal and must not be treated as one.

mightyETL accepts a deliberately narrow key profile: 16 to 128 characters from
`A-Z`, `a-z`, `0-9`, `.`, `_`, `:`, and `-`. UUIDs satisfy this profile. Generate high-entropy,
unguessable keys and use a new key for every logically new batch.

Example for the authentication mechanism implemented by the ETL service:

```http
POST /api/etl/process HTTP/1.1
Authorization: Basic <credentials>
Content-Type: application/json
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000

[{"id":"record_alpha","name":"accepted"}]
```

A successful first execution returns the existing `200 text/plain` body and:

```http
Idempotency-Replayed: false
```

A retry after the first transaction committed returns the exact stored body without another target
write and includes:

```http
Idempotency-Replayed: true
```

## Error contract

| HTTP | `errorCode` | Meaning |
| ---: | --- | --- |
| 400 | `etl_invalid_idempotency_key` | The key is absent from the supported safe profile. |
| 401 | `etl_idempotency_principal_required` | A keyed request has no authenticated principal namespace. |
| 422 | `etl_idempotency_key_reused` | The same principal-scoped key already committed a different payload digest. |

Errors use the repository's RFC 9457 `application/problem+json` contract and never include the raw
key, principal, payload, SQL text, or exception message.

## Transaction and concurrency guarantees

The client key is namespaced by the authenticated principal and then SHA-256 hashed. The request
body is independently SHA-256 hashed. The database stores the hashes and successful response body;
it does not store the raw client key, principal name, or request payload.

For a new key, mightyETL performs this sequence in one retryable Spring transaction:

1. acquire a PostgreSQL transaction-level advisory lock derived from the scoped key hash;
2. inspect `etl_idempotency_records` for a prior committed result;
3. prevalidate and transform the complete ETL batch;
4. write all accepted target rows;
5. insert the durable response ledger row; and
6. commit both target rows and ledger together.

Java callers must invoke the idempotent method through the Spring-managed service proxy or establish
an explicit transaction boundary before invocation. Direct construction and same-class
self-invocation do not activate Spring's annotation advice. mightyETL therefore fails closed before
request-lock or JDBC access when no actual transaction is active, rather than silently weakening the
atomicity and lock-lifetime guarantees.

Competing requests with the same principal-scoped key wait for the same transaction lock. After the
first commit, the waiter observes and replays the ledger row. A rollback releases the advisory lock
and leaves neither target rows nor a false success ledger entry.

A 64-bit prefix selects the advisory lock, while the full 256-bit hash remains the ledger primary
key. A rare advisory-prefix collision can delay an unrelated request but cannot make it replay the
wrong response.

## Schema migration

Flyway migration `V1__create_etl_idempotency_records.sql` creates:

- `etl_idempotency_records`;
- hash-format constraints; and
- `etl_idempotency_created_at_index` for retention operations.

New empty databases migrate automatically. For an existing non-empty schema that has no Flyway
history table, verify the target database and set `FLYWAY_BASELINE_ON_MIGRATE=true` for the first
upgraded startup. The configured baseline version is `0`, so migration `V1` still executes. Return
the setting to `false` after the Flyway history has been established. The default is fail-closed to
avoid silently baselining the wrong database.

The repository's local Compose database is a known exception because
`docker/postgres/init/01_schema.sql` intentionally creates the legacy bootstrap tables before the ETL
service starts. The local Compose stack sets `FLYWAY_BASELINE_ON_MIGRATE=true` for that service only;
the application baseline version remains `0`, so migration `V1` executes instead of being marked as
already applied. This local compatibility setting is not a recommendation to enable automatic
baselining for an unverified production database.

Rollback of the application does not require dropping the ledger table; older code ignores it. If a
reviewed data-retention decision requires destructive removal, stop keyed writes, retain any needed
audit evidence, and apply:

```sql
DROP INDEX IF EXISTS etl_idempotency_created_at_index;
DROP TABLE IF EXISTS etl_idempotency_records;
```

## Retention and privacy boundary

This slice intentionally does not delete ledger rows automatically. Operators must set a retention
period that is longer than the maximum supported client retry window and compatible with their
privacy, audit, and incident-response obligations. The stored response contains the same record IDs
returned to the authenticated caller, so it must be treated according to the classification of
those identifiers.

A reviewed purge can use the indexed timestamp after the retry window expires:

```sql
DELETE FROM etl_idempotency_records
WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '30 days';
```

Do not shorten retention without coordinating client retry behavior; a purged key can be accepted as
a new request and therefore can produce a second write.

## Standards basis

- HTTP Semantics defines POST as non-idempotent by default and advises clients not to retry a
  non-idempotent request automatically unless they know the resource semantics make the retry safe.
- The expired IETF HTTPAPI working-group draft for `Idempotency-Key` describes client-generated
  unique keys, payload fingerprints, conflict handling, and defenses against injection and
  cross-client data leakage. It is used as design evidence, not represented as a published RFC.
- PostgreSQL documents `pg_advisory_xact_lock` as an exclusive transaction-level advisory lock that
  waits when necessary and is released at transaction completion.

### References

- Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP Semantics* (RFC 9110).
  RFC Editor. https://www.rfc-editor.org/rfc/rfc9110
- Jena, J., & Dalal, S. (2025). *The Idempotency-Key HTTP Header Field*
  (draft-ietf-httpapi-idempotency-key-header-07, expired April 18, 2026). Internet Engineering Task
  Force. https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
- PostgreSQL Global Development Group. (2026). *Advisory lock functions*. PostgreSQL 18
  documentation. https://www.postgresql.org/docs/current/functions-admin.html
