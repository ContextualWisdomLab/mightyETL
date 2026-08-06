# Durable ETL Job Replay Design

## Purpose

mightyETL terminalizes failed and cancelled jobs by clearing their retained request payload. Operators still need a safe way to retry the same intended work without mutating terminal history, trusting an unverified replacement payload, or losing the relationship between the original and the new attempt.

This design adds one authenticated owner-scoped replay action. The client resupplies the complete bounded JSON payload; mightyETL validates it through the existing intake contract and requires its SHA-256 digest to equal the immutable terminal source digest before creating a new `PENDING` resource.

## API contract

```http
POST /api/etl/jobs/{source_job_record_id}/replays
Authorization: <existing authenticated principal>
Idempotency-Key: "new-replay-key"
Content-Type: application/json

[{"id":"record_alpha","name":"accepted"}]
```

A first accepted replay returns RFC 9110 `202 Accepted`, `Location` for the new job, `Cache-Control: no-store`, and `Idempotency-Replayed: false`. The same principal, replay key, source job, and byte-identical payload returns the same new job with `Idempotency-Replayed: true`.

The source remains terminal and unchanged. Only `FAILED` and `CANCELLED` are replayable. Active sources conflict because they still own or may own execution. `SUCCEEDED` conflicts because a first-slice replay could duplicate committed target effects.

## Immutable relational lineage

Migration `V7__add_etl_job_replay_lineage.sql` adds:

```text
replay_source_job_record_id
replay_root_job_record_id
replay_generation_count
```

The root job has all three fields null. Every replay row has all three fields non-null, an immediate source different from itself, a root different from itself, and a generation from 1 through 100. Self-referencing foreign keys use `ON DELETE RESTRICT` so terminal history cannot disappear through cascade deletion.

For the first replay:

```text
source = terminal root job
root = terminal root job
generation = 1
```

For replay of a replay:

```text
source = immediate terminal replay
root = inherited first job
generation = source generation + 1
```

The application verifies that source and inherited root are owner-scoped to the same principal. Database constraints remain the structural boundary; the relational rows are authoritative even when lineage is later exported as W3C PROV.

## Replay-key authority

The replay key is normalized through the same bounded quoted-or-legacy safe profile as other idempotency keys. The new job stores a versioned principal-scoped replay identity in the existing `submission_key_hash` field:

```text
SHA-256(
  "mightyetl:durable-job-replay:v1:"
  || principal_scope_hash
  || ":"
  || normalized_replay_key
)
```

This isolates replay keys from ordinary submission keys and from another tenant. Within one principal namespace, the same replay key can identify only one new job. Reusing it with another source or payload fails with `etl_job_replay_key_reused`.

A transaction-level lock derived from the replay identity serializes concurrent creation. The table's existing principal-plus-submission-hash unique constraint remains the second integrity boundary. A concurrent request that cannot acquire the lock returns `etl_job_replay_in_progress`; retrying after the first transaction completes replays the committed new job.

## Database transaction

One transaction performs the following sequence:

1. validate source identifier, replay key, principal, and complete bounded payload before lock or table access;
2. compute principal, replay-key, and payload digests;
3. acquire the replay-key transaction lock;
4. find and classify an existing job using that replay identity;
5. select the owner-scoped source and immutable lineage;
6. require terminal `FAILED` or `CANCELLED`;
7. require the supplied payload digest to equal the source `request_digest`;
8. derive root and bounded generation;
9. insert one new `PENDING` row with the verified payload and lineage;
10. return only the new operator-safe job identity.

The source is never updated. Read-then-write state resurrection is prohibited.

## Error taxonomy

| HTTP | Stable code | Meaning |
| ---: | --- | --- |
| 400 | `etl_job_replay_key_required` | Replay key is missing or outside the bounded profile. |
| 404 | `etl_job_not_found` | Source is malformed, missing, or foreign-owned. |
| 409 | `etl_job_replay_in_progress` | Another transaction owns the principal-scoped replay identity. |
| 409 | `etl_job_replay_source_active` | Source is `PENDING` or `RUNNING`. |
| 409 | `etl_job_replay_source_succeeded` | Source already committed successful effects. |
| 409 | `etl_job_replay_generation_exhausted` | Generation 100 cannot create generation 101. |
| 422 | `etl_job_replay_payload_mismatch` | Resupplied JSON does not match the immutable source digest. |
| 422 | `etl_job_replay_key_reused` | Replay key already identifies another source or payload. |

All failures use the existing RFC 9457 problem model without payload, principal, key, hash, lineage internals, SQL, or exception text.

## Worker compatibility

The new row is an ordinary `PENDING` job. Existing worker claim, lease fencing, retry, success, failure, cancellation, pagination, polling, and conditional-status contracts apply unchanged. No replay-specific worker or scheduler exists. Only lineage and admission differ.

## Provenance export

A later JSON-LD adapter may map the relational evidence as:

```text
source job    → prov:Entity
replay action → prov:Activity
new job       → prov:Entity
new job       → prov:wasDerivedFrom → source job
replay action → prov:used → source job
new job       → prov:wasGeneratedBy → replay action
```

The export must not weaken owner authorization or replace database constraints.

## Verification

The exact-head suite must prove:

1. failed and cancelled sources each create a distinct pending job;
2. source status, terminal evidence, timestamps, and cleared payload remain unchanged;
3. payload mismatch fails before insertion;
4. same source, key, and payload replay one new job;
5. same key with another source or payload fails closed;
6. foreign and missing sources remain indistinguishable;
7. pending, running, and succeeded sources are rejected;
8. replay of replay preserves root and increments generation;
9. generation 100 cannot create generation 101;
10. concurrent creation produces one row and an in-progress or later replay outcome;
11. the new job can be claimed and follows normal lifecycle contracts;
12. migration completeness, self-reference prohibition, naming, deletion restriction, rollout, and rollback are documented and tested;
13. all added production statements and branches retain zero-missed configured coverage and no project test is skipped.

## Operational limitation

Replay verifies that the resupplied payload matches the immutable source digest. It does not prove that replaying a connector is economically or externally safe. A target that cannot provide transactional or idempotent effects requires connector-specific policy before replay is enabled for that connector.

## References — APA 7th

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110

Nottingham, M., Wilde, E., & Dalal, S. (2023). *Problem details for HTTP APIs* (RFC 9457). RFC Editor. https://www.rfc-editor.org/rfc/rfc9457

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: INSERT*. https://www.postgresql.org/docs/18/sql-insert.html

World Wide Web Consortium. (2013). *PROV-O: The PROV ontology*. https://www.w3.org/TR/prov-o/
