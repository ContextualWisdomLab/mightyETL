# Bounded atomic ETL batches

## Purpose

`etl-service` accepts JSON arrays at `POST /api/etl/process`. The service validates and transforms the complete request before the first database write, then executes all accepted writes inside one Spring transaction.

This closes three production risks in the legacy implementation:

- unbounded one-task-per-record fan-out on the JVM common pool;
- partial database writes when a later record is malformed or rejected;
- delimiter and locale corruption caused by serializing fields to text and splitting them again.

## Admission contract

A request is accepted only when all of the following are true:

1. The body does not exceed the configured UTF-8 byte limit.
2. The body parses as a top-level JSON array with no duplicate object-field names.
3. The array does not exceed the configured record limit.
4. Every array element is a JSON object.
5. Every record has a trimmed, non-blank JSON string `id` containing no ISO control, Unicode line-separator, or paragraph-separator characters and no more than 256 Unicode code points; numeric JSON identifier types are rejected.
6. Every record's field names remain unique after locale-independent uppercase normalization.
7. Every record can be transformed before the first JDBC call.

Exact duplicate field names are rejected rather than accepting parser-dependent “first value” or “last value” semantics. Case variants and other field names that normalize to the same output key are also rejected, preventing ambiguous transformed records such as two `ID` or two `NAME` entries. A rejected request performs no database writes.

## Transaction contract

After admission and transformation succeed for the full batch, mightyETL inserts each transformed record with the existing parameterized statement:

```sql
INSERT INTO processed_data (data) VALUES (?)
```

`EtlService.processData` is a Spring `@Transactional` boundary. A runtime database failure in any record rolls back earlier writes from that request. An H2-backed integration test verifies both successful commit and rollback of an earlier insert when a later row violates a target constraint.

Only `TransientDataAccessException` failures are retried. Invalid input and deterministic target constraints fail immediately instead of repeating the same batch.

## Deterministic transformation

Fields are transformed directly from the Jackson JSON tree; values are not split on commas or colons.

- Field names use locale-independent uppercase normalization and must remain unique after normalization.
- `NAME` values use locale-independent uppercase conversion.
- `EMAIL` values use locale-independent lowercase conversion.
- `AMOUNT` values use `BigDecimal`, `HALF_UP`, scale `2`, and `toPlainString()`.
- Invalid, excessive-precision, or extreme-scale amounts retain the legacy fallback `0.00` without expanding attacker-controlled exponents into huge strings.
- Nested arrays and objects are retained as compact JSON instead of collapsing to empty text.
- Response lines remain `Processed: <id>` in input order. Identifier whitespace, control and Unicode line-separator characters, and length are bounded so one record cannot inject or amplify response lines.

## Configuration

Preferred keys use the mightyETL product namespace; compatibility keys remain available:

| Preferred property | Compatibility property | Environment variable | Default | Hard ceiling |
|:-------------------|:-----------------------|:---------------------|--------:|-------------:|
| `mightyetl.etl.max-payload-bytes` | `xtrmetl.etl.max-payload-bytes` | `ETL_MAX_PAYLOAD_BYTES` | `1048576` | `67108864` |
| `mightyetl.etl.max-batch-records` | `xtrmetl.etl.max-batch-records` | `ETL_MAX_BATCH_RECORDS` | `1000` | `100000` |

When both full property namespaces are set, `mightyetl.*` wins through `MightyEtlConfigAliasEnvironmentPostProcessor`.

Example:

```bash
export ETL_MAX_PAYLOAD_BYTES=1048576
export ETL_MAX_BATCH_RECORDS=1000
```

Values outside the supported range fail configuration binding instead of silently disabling admission control.

## Operational guidance

- Keep the payload limit aligned with gateway and ingress body-size limits. The service-level check occurs after the MVC stack has materialized the request string and is not a substitute for edge enforcement.
- Keep the record limit below the transaction size that the target database can commit within the request timeout and lock budget.
- Monitor request latency, transaction duration, rollback rate, database pool wait time, and rejected payload/record-limit errors before raising either limit.
- Use descriptive string identifiers. Numeric JSON identifier types are rejected to keep identifier contracts explicit and stable across systems.

## Remaining boundary

The current API is synchronous and stores a text representation in `processed_data.data`. High-volume ingestion, asynchronous job state, idempotency keys, durable retry queues, and typed target schemas remain separate product milestones; this change does not claim those capabilities.
