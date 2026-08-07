# Durable job replay standards evidence

## Decision

mightyETL models replay as creation of a new durable job derived from an immutable terminal source. The source is never returned to `PENDING`. Only owner-scoped `FAILED` and `CANCELLED` sources are eligible, and the operator must resupply byte-identical bounded JSON whose SHA-256 digest equals the source `request_digest`.

The accepted replay response uses `202 Accepted` with a monitor URI because durable admission does not assert completion. Deterministic failures use RFC 9457 problem details. PostgreSQL owns the replay transaction, uniqueness, owner-scoped foreign keys, exact source/root/generation continuity, lineage-column immutability, and source-row serialization. Replay lineage is compatible with PROV-O derivation semantics, while the relational database remains authoritative.

## Normative mapping

| Product contract | Primary authority | Application |
|---|---|---|
| Noncommittal durable admission | RFC 9110, section 15.3.3 | `202 Accepted`, `Location`, and no claim of execution completion |
| Stable machine-readable failures | RFC 9457 | Fixed problem type, title, status, detail, and `error_code` without exception text |
| Atomic new-row creation and conflict handling | PostgreSQL 18 `INSERT` and transaction documentation | One transaction validates source ownership, digest, replay identity, and lineage before inserting one new job |
| Same-owner source and root existence | PostgreSQL 18 constraints | Composite foreign keys bind source and root to the new row's `principal_scope_hash`, and `ON DELETE RESTRICT` protects retained history |
| Exact lineage transition | PostgreSQL 18 `CREATE TRIGGER` and PL/pgSQL trigger functions | A row-level `BEFORE INSERT OR UPDATE OF` trigger validates terminal source, first root, exact generation successor, initial pending lifecycle, and lineage-column immutability |
| Derivation lineage | W3C PROV-O | New job is derived from the immediate source and preserves an immutable first-root/generation chain |
| Domain separation rationale | NIST SP 800-185 | Replay-key hashing uses a versioned replay-specific domain; the SHA-256 construction does not claim cSHAKE or TupleHash conformance |

## Why constraints and a trigger are both required

The composite owner-scoped foreign keys prove that the named source and root exist in the same tenant namespace. The complete-lineage check proves that lineage fields are either all null or all present, bounds generation, and rejects direct self-reference. Those declarative rules cannot express all cross-row temporal invariants:

- source must already be `FAILED` or `CANCELLED`;
- the root must be the first job, with every lineage field null;
- generation one must use the same row as source and root;
- every later generation must inherit that root and equal the immediate source generation plus one;
- an existing replay row must never be reparented.

PostgreSQL `CREATE TRIGGER` permits a row-level trigger to run before selected insert or update events, and PL/pgSQL trigger functions receive `NEW`, `OLD`, `TG_OP`, and related context. mightyETL uses that database mechanism to reject invalid writes before persistence. The service repeats the inherited-root identity check as defense in depth, but a maintenance script or import path cannot bypass the relational authority merely by omitting Java validation.

The trigger raises the fixed SQLSTATE class `23514` without embedding principal values, job identifiers, payloads, hashes, SQL, or exception causes. Application and operator logs must classify the failure with a finite internal integrity code rather than copying raw database text.

## Security and privacy boundary

The HTTP response and ordinary telemetry exclude raw principals, replay keys, payloads, request digests, internal hashes, source/root identifiers, lease identifiers, SQL, target identities, and exception messages. Foreign-owned and absent source identifiers remain indistinguishable. `SUCCEEDED` is excluded because repeating a committed target effect is not safe merely because the original request bytes are known.

Connector replay is enabled only when target effects participate in the mightyETL transaction or the connector supplies independently tested idempotency or compensation. Payload equality is evidence of replay fidelity, not evidence that an external system will suppress duplicate effects.

## Verification obligations

- real PostgreSQL 18 migration rehearsal for trigger and function presence, composite self-referencing foreign keys, and `ON DELETE RESTRICT`;
- exact-payload acceptance and byte-different rejection;
- owner-safe missing/foreign behavior;
- same-key replay, key reuse conflict, and concurrent admission tests;
- source immutability and source/root/generation lineage tests;
- database rejection of nonterminal sources, generation-one root divergence, derived roots, skipped generations, cross-owner references, and lineage mutation;
- service defense-in-depth rejection when an inherited root is itself a replay row;
- generation-bound rejection;
- ordinary worker claim, lease fencing, cancellation, polling, and ETag compatibility;
- transactional rollback rehearsal that restores trigger, function, columns, and named constraints;
- configured production instruction, line, method, and branch coverage with zero misses;
- direct-base CI, dependency, SBOM, SAST, security, review-thread, and independent-approval gates before merge.

## References — APA 7th edition

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110

National Institute of Standards and Technology. (2016). *SHA-3 derived functions: cSHAKE, KMAC, TupleHash, and ParallelHash* (NIST Special Publication 800-185). U.S. Department of Commerce. https://doi.org/10.6028/NIST.SP.800-185

Nottingham, M., Wilde, E., & Dalal, S. (2023). *Problem details for HTTP APIs* (RFC 9457). RFC Editor. https://www.rfc-editor.org/rfc/rfc9457

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: Constraints*. https://www.postgresql.org/docs/18/ddl-constraints.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: CREATE TRIGGER*. https://www.postgresql.org/docs/18/sql-createtrigger.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: INSERT*. https://www.postgresql.org/docs/18/sql-insert.html

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: PL/pgSQL trigger functions*. https://www.postgresql.org/docs/18/plpgsql-trigger.html

World Wide Web Consortium. (2013). *PROV-O: The PROV ontology*. https://www.w3.org/TR/prov-o/
