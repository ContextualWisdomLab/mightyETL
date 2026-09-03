# Bounded atomic ETL batches

## Purpose

`etl-service` accepts JSON arrays at `POST /api/etl/process`. The service validates and transforms the complete request before the first `processed_data` target write, then executes all accepted target writes inside one Spring transaction. When an `Idempotency-Key` is supplied, the successful replay-ledger insert shares that transaction with the target writes.

This closes three production risks in the legacy implementation:

- unbounded one-task-per-record fan-out on the JVM common pool;
- partial target-data writes when a later record is malformed or rejected;
- delimiter and locale corruption caused by serializing fields to text and splitting them again.

## Admission contract

A request is accepted only when all of the following are true:

1. The body does not exceed the configured UTF-8 byte limit.
2. The body parses as a top-level JSON array with no duplicate object-field names.
3. The array does not exceed the configured record limit.
4. Every array element is a JSON object.
5. Every record has a trimmed, non-blank JSON string `id` containing no ISO control, Unicode format-control, Unicode line-separator, or paragraph-separator characters and no more than 256 Unicode code points; numeric JSON identifier types are rejected.
6. Every record's field names remain unique after locale-independent uppercase normalization.
7. Every record can be transformed before any target-table write.

Duplicate field names are rejected rather than accepting parser-dependent “first value” or “last value” semantics. Case variants and other field names that normalize to the same output key are also rejected, preventing ambiguous transformed records such as two `ID` or two `NAME` entries. A rejected request performs no `processed_data` target writes and creates no successful idempotency replay record.

Idempotent requests may acquire the request lock and read the replay ledger before batch validation so an already committed request can be replayed without repeating target work. Those control-plane JDBC operations are not target-data writes. A new request with an invalid record still fails before any `processed_data` mutation or successful ledger insert.

## Transaction contract

After admission and transformation succeed for the full batch, mightyETL inserts each transformed record with the existing parameterized statement:

```sql
INSERT INTO processed_data (data) VALUES (?)
```

`EtlService.processData` is a Spring `@Transactional` boundary. A runtime database failure in any record rolls back earlier target writes from that request. An H2-backed integration test verifies both successful commit and rollback of an earlier insert when a later row violates a target constraint.

`EtlService.processDataIdempotently` uses the same batch transformation and target-write path. Its transaction-scoped request lock and replay-ledger lookup occur first to preserve replay semantics; for a new request, the target writes and successful ledger insert commit or roll back together.

Only `TransientDataAccessException` failures are retried. Invalid input and deterministic target constraints fail immediately instead of repeating the same batch.

## Deterministic transformation

Fields are transformed directly from the Jackson JSON tree; values are not split on commas or colons.

- Field names use locale-independent uppercase normalization and must remain unique after normalization.
- `NAME` values use locale-independent uppercase conversion.
- `EMAIL` values use locale-independent lowercase conversion.
- `AMOUNT` values use `BigDecimal`, `HALF_UP`, scale `2`, and `toPlainString()`.
- Malformed, blank, excessive-precision, or extreme-scale `AMOUNT` values are rejected as invalid records before any target-table write instead of being rewritten to a valid-looking zero.
- Batch admission remains all-or-nothing: if any record has an invalid `AMOUNT`, the request performs no `processed_data` target writes and creates no successful idempotency replay record.
- Nested arrays and objects are retained as compact JSON instead of collapsing to empty text.
- Response lines remain `Processed: <id>` in input order. Identifier whitespace, ISO control, Unicode format-control, Unicode line-separator characters, and length are bounded so one record cannot inject, visually reorder, conceal, or amplify response lines.

Rows created by historic releases that rewrote invalid amounts to zero cannot be distinguished from genuine zero values after the fact without independent source-system evidence. Reconcile affected historical data from an authoritative source before making accounting or compliance decisions.

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
- Monitor invalid-record rejection rates and reconcile upstream amount-format changes before retrying rejected inputs.
- Do not log raw amount values or request payloads when diagnosing rejections; use bounded request/error metadata instead.
- Use descriptive string identifiers. Numeric JSON identifier types are rejected to keep identifier contracts explicit and stable across systems.

## Remaining boundary

The direct `/api/etl/process` path remains synchronous and stores a text representation in `processed_data.data`. Principal-scoped idempotent replay is documented in `docs/etl/idempotent-retries.md`, and durable job intake is documented in `docs/etl/durable-job-intake.md`; neither changes the target-write validation boundary described here. Completed durable worker execution, high-volume ingestion orchestration, and typed target schemas remain separate product milestones.
