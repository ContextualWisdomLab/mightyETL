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

The root job has all three fields null. Every replay row has all three fields non-null, an immediate source different from itself, a root different from itself, and a generation from 1 through 100. The composite owner-scoped foreign keys use `ON DELETE RESTRICT` so terminal history cannot disappear through cascade deletion and one tenant cannot reference another tenant's source or root.

The referenced key is the named support constraint:

```text
etl_job_owner_identity_unique
UNIQUE (job_record_id, principal_scope_hash)
```

Each source and root relationship includes the new row's `principal_scope_hash`:

```text
FOREIGN KEY (replay_source_job_record_id, principal_scope_hash)
  REFERENCES etl_job_records (job_record_id, principal_scope_hash)

FOREIGN KEY (replay_root_job_record_id, principal_scope_hash)
  REFERENCES etl_job_records (job_record_id, principal_scope_hash)
```

Those declarative constraints establish owner scope, existence, self-reference rejection, completeness, deletion restriction, and the generation bound. They do not by themselves prove that a source is terminal, that the selected root is the first lineage row, that the new row preserves the immediate source digest, or that each replay hop advances exactly one generation. The migration therefore adds a database trigger and PL/pgSQL trigger function as the relational authority for continuity:

```text
validate_etl_job_replay_lineage()
etl_job_replay_lineage_guard_trigger
```

On replay insertion, the database trigger requires all of the following:

- the new derived row starts as `PENDING`, attempt zero, with a retained payload;
- the immediate source exists in the same principal scope and is `FAILED` or `CANCELLED`;
- the new row's `request_digest` exactly equals the immediate source `request_digest`;
- the root exists in the same principal scope, is terminal, and has all lineage fields null;
- generation 1 uses the same row for immediate source and root;
- later generations inherit the exact first root and equal the source generation plus one.

On updates that name any lineage column, the trigger rejects every changed value. Lineage fields are immutable after insertion, so a maintenance script, import path, or future service cannot silently reparent a job after descendants exist.

A terminal row becomes durable evidence when a descendant names it as an immediate source or lineage root. Child insertion locks the immediate source and root with PostgreSQL `FOR UPDATE` before it can validate and commit. A parent update already owns the parent row lock before its trigger executes; when the update would alter terminal replay evidence, the trigger performs an indexed descendant existence lookup without taking child row locks and then returns before the INSERT-only source/root locking path. This establishes one lock direction and avoids child-to-ancestor lock inversion:

```text
child insertion → lock source/root → validate → commit or reject
parent mutation → parent row already locked → read descendant existence → commit or reject
```

If a parent update commits first, a later child validates the resulting evidence. If child insertion obtains the parent lock first, a later conflicting parent update waits, observes the committed descendant, and fails closed. Ordinary lifecycle updates remain possible before the first descendant exists; referenced replay evidence is immutable after that point, so an already-created descendant cannot silently acquire a different historical meaning.

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

The application independently validates the owner-scoped source, source digest, inherited root, and root-row identity before insertion. PostgreSQL independently rejects cross-owner, nonterminal, digest-divergent, discontinuous, derived-root, mutable-lineage, and referenced-evidence mutation. Relational rows remain authoritative even when lineage is later exported as W3C PROV.

## Replay lookup indexes

The trigger's immutable-evidence check and PostgreSQL's self-referencing foreign keys need bounded source/root lookup paths as durable history grows. The design therefore separates schema authority from online index construction:

- `V7__add_etl_job_replay_lineage.sql` remains transactional and contains columns, constraints, trigger, and function only;
- `V8__add_etl_job_replay_source_lookup_index.sql` owns one partial `CREATE INDEX CONCURRENTLY` for `(replay_source_job_record_id, principal_scope_hash)`;
- `V9__add_etl_job_replay_root_lookup_index.sql` owns one partial `CREATE INDEX CONCURRENTLY` for `(replay_root_job_record_id, principal_scope_hash)`;
- each concurrent migration has its own `.sql.conf` with `executeInTransaction=false`;
- migration verification requires both indexes to be present, ready, and valid.

One concurrent index per nontransactional migration gives each failure one auditable Flyway repair boundary. A cancelled build can leave an invalid index, so rollout must inspect PostgreSQL catalog state, remove only the failed artifact with `DROP INDEX CONCURRENTLY`, repair the exact migration record under the approved deployment procedure, and rerun without editing an applied migration.

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
9. require the inherited root to exist in the owner namespace and have null lineage fields;
10. insert one new `PENDING` row with the verified payload and lineage;
11. let PostgreSQL validate same-owner references, immediate-source digest equality, terminal source, first root, exact generation continuity, initial lifecycle, and the transition from mutable lifecycle state to referenced immutable evidence;
12. return only the new operator-safe job identity.

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

All covered request failures use the existing RFC 9457 problem model without payload, principal, key, hash, lineage internals, SQL, or exception text. A trigger rejection indicates internally inconsistent repository state or an unauthorized writer and is treated as an operator-visible integrity incident rather than reflected with raw database text.

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

The export must not weaken owner authorization or replace database constraints and trigger enforcement.

## Verification

The exact-head suite must prove:

1. failed and cancelled sources each create a distinct pending job;
2. source status, terminal evidence, timestamps, and cleared payload remain unchanged;
3. payload mismatch fails before insertion;
4. same source, key, and payload replay one new job;
5. same key with another source or payload fails closed;
6. foreign and missing sources remain indistinguishable;
7. pending, running, and succeeded sources are rejected;
8. replay of replay preserves the first root and increments generation exactly once;
9. generation 100 cannot create generation 101;
10. concurrent creation produces one row and an in-progress or later replay outcome;
11. the new job can be claimed and follows normal lifecycle contracts;
12. service defense in depth rejects an inherited root that is itself a replay row;
13. PostgreSQL 18 rejects cross-owner references, nonterminal sources, immediate-source digest mismatch, generation-one root divergence, derived roots, skipped generations, lineage mutation, and mutation of referenced root or immediate-source evidence;
14. PostgreSQL 18 applies V8 and V9 independently and requires both replay lookup indexes to be ready and valid;
15. migration completeness, source and root constraints, trigger/function presence, self-reference prohibition, descriptive naming, deletion restriction, concurrent-index recovery, rollout, and rollback are documented and tested;
16. all added production statements and branches retain zero-missed configured coverage and no project test is skipped.

## Operational limitation

Replay verifies that the resupplied payload matches the immutable source digest. It does not prove that replaying a connector is economically or externally safe. A target that cannot provide transactional or idempotent effects requires connector-specific policy before replay is enabled for that connector.

## References — APA 7th

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110

Nottingham, M., Wilde, E., & Dalal, S. (2023). *Problem details for HTTP APIs* (RFC 9457). RFC Editor. https://www.rfc-editor.org/rfc/rfc9457

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: Constraints*. https://www.postgresql.org/docs/18/ddl-constraints.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: CREATE INDEX*. https://www.postgresql.org/docs/18/sql-createindex.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: CREATE TRIGGER*. https://www.postgresql.org/docs/18/sql-createtrigger.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: INSERT*. https://www.postgresql.org/docs/18/sql-insert.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: PL/pgSQL trigger functions*. https://www.postgresql.org/docs/18/plpgsql-trigger.html

World Wide Web Consortium. (2013). *PROV-O: The PROV ontology*. https://www.w3.org/TR/prov-o/
