# Durable job replay standards evidence

## Decision

mightyETL models replay as creation of a new durable job derived from an immutable terminal source. The source is never returned to `PENDING`. Only owner-scoped `FAILED` and `CANCELLED` sources are eligible, and the operator must resupply byte-identical bounded JSON whose SHA-256 digest equals the source `request_digest`.

The accepted replay response uses `202 Accepted` with a monitor URI because durable admission does not assert completion. Deterministic failures use RFC 9457 problem details. PostgreSQL owns the replay transaction, uniqueness, lineage constraints, and source-row serialization. Replay lineage is compatible with PROV-O derivation semantics, while the relational database remains authoritative.

## Normative mapping

| Product contract | Primary authority | Application |
|---|---|---|
| Noncommittal durable admission | RFC 9110, section 15.3.3 | `202 Accepted`, `Location`, and no claim of execution completion |
| Stable machine-readable failures | RFC 9457 | Fixed problem type, title, status, detail, and `error_code` without exception text |
| Atomic new-row creation and conflict handling | PostgreSQL 18 `INSERT` and transaction documentation | One transaction validates source ownership, digest, replay identity, and lineage before inserting one new job |
| Derivation lineage | W3C PROV-O | New job is derived from the immediate source and preserves an immutable root/generation chain |
| Domain separation rationale | NIST SP 800-185 | Replay-key hashing uses a versioned replay-specific domain; the SHA-256 construction does not claim cSHAKE or TupleHash conformance |

## Security and privacy boundary

The HTTP response and ordinary telemetry exclude raw principals, replay keys, payloads, request digests, internal hashes, source/root identifiers, lease identifiers, SQL, target identities, and exception messages. Foreign-owned and absent source identifiers remain indistinguishable. `SUCCEEDED` is excluded because repeating a committed target effect is not safe merely because the original request bytes are known.

Connector replay is enabled only when target effects participate in the mightyETL transaction or the connector supplies independently tested idempotency or compensation. Payload equality is evidence of replay fidelity, not evidence that an external system will suppress duplicate effects.

## Verification obligations

- real PostgreSQL migration rehearsal for self-referencing foreign keys and `ON DELETE RESTRICT`;
- exact-payload acceptance and byte-different rejection;
- owner-safe missing/foreign behavior;
- same-key replay, key reuse conflict, and concurrent admission tests;
- source immutability and source/root/generation lineage tests;
- generation-bound rejection;
- ordinary worker claim, lease fencing, cancellation, polling, and ETag compatibility;
- configured production instruction, line, method, and branch coverage with zero misses;
- direct-base CI, dependency, SBOM, SAST, security, review-thread, and independent-approval gates before merge.

## References — APA 7th edition

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110

National Institute of Standards and Technology. (2016). *SHA-3 derived functions: cSHAKE, KMAC, TupleHash, and ParallelHash* (NIST Special Publication 800-185). U.S. Department of Commerce. https://doi.org/10.6028/NIST.SP.800-185

Nottingham, M., Wilde, E., & Dalal, S. (2023). *Problem details for HTTP APIs* (RFC 9457). RFC Editor. https://www.rfc-editor.org/rfc/rfc9457

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: INSERT*. https://www.postgresql.org/docs/18/sql-insert.html

World Wide Web Consortium. (2013). *PROV-O: The PROV ontology*. https://www.w3.org/TR/prov-o/
