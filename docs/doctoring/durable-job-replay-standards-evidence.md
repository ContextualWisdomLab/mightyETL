# Durable job replay standards evidence

## Decision

mightyETL models replay as creation of a new durable job derived from an immutable terminal source. The source is never returned to `PENDING`. Only owner-scoped `FAILED` and `CANCELLED` sources are eligible, and the operator must resupply byte-identical bounded JSON whose SHA-256 digest equals the source `request_digest`.

The accepted replay response uses `202 Accepted` with a monitor URI because durable admission does not assert completion. Deterministic failures use RFC 9457 problem details. PostgreSQL owns the replay transaction, uniqueness, owner-scoped foreign keys, immediate-source digest equality, exact source/root/generation continuity, lineage-column immutability, referenced-evidence immutability, and source-row serialization. Replay lineage is compatible with PROV-O derivation semantics, while the relational database remains authoritative.

## Normative mapping

| Product contract | Primary authority | Application |
|---|---|---|
| Noncommittal durable admission | RFC 9110, section 15.3.3 | `202 Accepted`, `Location`, and no claim of execution completion |
| Stable machine-readable failures | RFC 9457 | Fixed problem type, title, status, detail, and `error_code` without exception text |
| Atomic new-row creation and conflict handling | PostgreSQL 18 `INSERT` and transaction documentation | One transaction validates source ownership, digest, replay identity, and lineage before inserting one new job |
| Same-owner source and root existence | PostgreSQL 18 constraints | Composite foreign keys bind source and root to the new row's `principal_scope_hash`, and `ON DELETE RESTRICT` protects retained history |
| Exact request and lineage transition | PostgreSQL 18 `CREATE TRIGGER` and PL/pgSQL trigger functions | A row-level `BEFORE INSERT OR UPDATE OF` trigger validates terminal source, immediate-source digest equality, first root, exact generation successor, initial pending lifecycle, and lineage-column immutability |
| Online descendant and foreign-key lookup | PostgreSQL 18 `CREATE INDEX`, `pg_index`, and system-information functions | Separate partial source/root indexes are built with one `CREATE INDEX CONCURRENTLY` per nontransactional Flyway migration and must match their exact ready, valid, nonunique, one-column partial-index definitions |
| Derivation lineage | W3C PROV-O | New job is derived from the immediate source and preserves an immutable first-root/generation chain |
| Domain separation rationale | NIST SP 800-185 | Replay-key hashing uses a versioned replay-specific domain; the SHA-256 construction does not claim cSHAKE or TupleHash conformance |

## Why constraints and a trigger are both required

The composite owner-scoped foreign keys prove that the named source and root exist in the same tenant namespace. The complete-lineage check proves that lineage fields are either all null or all present, bounds generation, and rejects direct self-reference. Those declarative rules cannot express all cross-row temporal invariants:

- source must already be `FAILED` or `CANCELLED`;
- the replay row `request_digest` must equal the immediate source `request_digest`;
- the root must be the first job, with every lineage field null;
- generation one must use the same row as source and root;
- every later generation must inherit that root and equal the immediate source generation plus one;
- an existing replay row must never be reparented; and
- terminal evidence must become immutable after any descendant references the row.

PostgreSQL `CREATE TRIGGER` permits a row-level trigger to run before selected insert or update events, and PL/pgSQL trigger functions receive `NEW`, `OLD`, `TG_OP`, and related context. mightyETL uses that database mechanism to reject invalid writes before persistence. The service repeats source-digest and inherited-root checks as defense in depth, but a maintenance script or import path cannot bypass the relational authority merely by omitting Java validation.

The trigger raises the fixed SQLSTATE class `23514` without embedding principal values, job identifiers, payloads, hashes, SQL, or exception causes. Application and operator logs must classify the failure with a finite internal integrity code rather than copying raw database text.

## Concurrency and lock-order evidence

Child insertion needs an exact source/root snapshot. The INSERT trigger therefore locks the immediate source and root with PostgreSQL `FOR UPDATE` before validating status, digest, root identity, and generation continuity.

A parent UPDATE already owns the parent row lock before the `BEFORE UPDATE` trigger executes. The immutable-evidence path performs an indexed descendant existence lookup without taking child row locks, rejects the mutation when a descendant exists, and returns before the INSERT-only source/root lock path. This establishes one lock direction:

```text
child insertion → source/root row locks
parent mutation → existing parent row lock + descendant existence read
```

A parent update that commits first defines the evidence a later child validates. A child that obtains the source/root lock first causes a later conflicting parent update to wait; after the child commits, the parent sees the descendant and fails closed. The parent never locks a child and then reaches back to an ancestor, avoiding child-to-ancestor lock inversion.

## Why online indexes are separate migrations

The self-referencing foreign keys and immutable-evidence lookup need indexes beginning with `replay_source_job_record_id` and `replay_root_job_record_id`. Building them with ordinary `CREATE INDEX` would block production writes. PostgreSQL `CREATE INDEX CONCURRENTLY` preserves table availability but cannot run inside a transaction block and can leave an invalid index after cancellation or failure.

mightyETL therefore uses two migrations:

- `V8__add_etl_job_replay_source_lookup_index.sql` owns only `etl_job_replay_source_lookup_index`;
- `V9__add_etl_job_replay_root_lookup_index.sql` owns only `etl_job_replay_root_lookup_index`.

Each companion `.sql.conf` sets `executeInTransaction=false`. One index per nontransactional migration makes failure, Flyway repair, and rollback independently auditable. PostgreSQL migration verification requires both `pg_index.indisready` and `pg_index.indisvalid`.

Ready and valid flags alone do not prove that a same-named index has the required definition. `pg_index.indnkeyatts` and `pg_index.indnatts` establish that the contract has exactly one key attribute and no included attributes, while `indisunique` proves the expected nonunique shape. `pg_get_indexdef` reconstructs each indexed column and `pg_get_expr` reconstructs each stored partial predicate. The verifier therefore rejects a same-named index unless it targets the exact source or root column and has the matching `IS NOT NULL` predicate. An interrupted or definition-mismatched build is not accepted as passing evidence; operators remove only the failed artifact with `DROP INDEX CONCURRENTLY`, repair the exact Flyway migration record through the approved process, and rerun without editing an applied migration.

## Security and privacy boundary

The HTTP response and ordinary telemetry exclude raw principals, replay keys, payloads, request digests, internal hashes, source/root identifiers, lease identifiers, SQL, target identities, and exception messages. Foreign-owned and absent source identifiers remain indistinguishable. `SUCCEEDED` is excluded because repeating a committed target effect is not safe merely because the original request bytes are known.

Connector replay is enabled only when target effects participate in the mightyETL transaction or the connector supplies independently tested idempotency or compensation. Payload equality is evidence of replay fidelity, not evidence that an external system will suppress duplicate effects.

## Verification obligations

- real PostgreSQL 18 migration rehearsal for trigger and function presence, composite self-referencing foreign keys, and `ON DELETE RESTRICT`;
- both replay lookup indexes present, ready, valid, nonunique, one-column, and bound to the exact source/root columns and `IS NOT NULL` predicates after separate V8/V9 concurrent builds;
- exact-payload acceptance and immediate-source digest mismatch rejection at both service and database boundaries;
- owner-safe missing/foreign behavior;
- same-key replay, key reuse conflict, and concurrent admission tests;
- source immutability and source/root/generation lineage tests;
- database rejection of nonterminal sources, generation-one root divergence, derived roots, skipped generations, cross-owner references, lineage mutation, and referenced-evidence mutation;
- lock-order regression proving the parent UPDATE path does not lock descendants before ancestor validation;
- service defense-in-depth rejection when an inherited root is itself a replay row;
- generation-bound rejection;
- ordinary worker claim, lease fencing, cancellation, polling, and ETag compatibility;
- transactional V7 rollback rehearsal plus operational V8/V9 concurrent-index rollback and invalid-index recovery evidence;
- configured production instruction, line, method, and branch coverage with zero misses;
- direct-base CI, dependency, SBOM, SAST, security, review-thread, and independent-approval gates before merge.

## References — APA 7th edition

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110

National Institute of Standards and Technology. (2016). *SHA-3 derived functions: cSHAKE, KMAC, TupleHash, and ParallelHash* (NIST Special Publication 800-185). U.S. Department of Commerce. https://doi.org/10.6028/NIST.SP.800-185

Nottingham, M., Wilde, E., & Dalal, S. (2023). *Problem details for HTTP APIs* (RFC 9457). RFC Editor. https://www.rfc-editor.org/rfc/rfc9457

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: Constraints*. https://www.postgresql.org/docs/18/ddl-constraints.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: CREATE INDEX*. https://www.postgresql.org/docs/18/sql-createindex.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: CREATE TRIGGER*. https://www.postgresql.org/docs/18/sql-createtrigger.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: INSERT*. https://www.postgresql.org/docs/18/sql-insert.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: pg_index*. https://www.postgresql.org/docs/18/catalog-pg-index.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: PL/pgSQL trigger functions*. https://www.postgresql.org/docs/18/plpgsql-trigger.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: System information functions and operators*. https://www.postgresql.org/docs/18/functions-info.html

World Wide Web Consortium. (2013). *PROV-O: The PROV ontology*. https://www.w3.org/TR/prov-o/
