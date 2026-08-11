# Durable ETL Job Cancellation Implementation Plan

**Goal:** Deliver owner-safe, idempotent, lease-fenced cancellation for pending and running durable ETL jobs on the repaired stack rooted at the exact conditional-status predecessor.

**Architecture:** Keep PostgreSQL as terminal-state authority. A single conditional cancellation update clears payload and lease state, records only bounded pseudonymous replay identity, and lets exact-lease worker predicates reject stale commits. The HTTP layer exposes only the existing operator-safe status model.

## Global constraints

- Preserve standalone operation and modular MSA integration.
- Do not weaken branch protection, security, independent review, or exact-head evidence.
- Do not use `COPILOT_GITHUB_TOKEN` or invent credentials.
- Database objects use descriptive multi-word `snake_case` names.
- Raw principals, cancellation keys, payloads, hashes, lease identifiers, SQL, and exception messages do not enter client responses, logs, or metric labels.
- Added production statement and branch coverage remains 100%.
- Every public production API has beginner-readable Javadoc.
- No project test may be skipped.
- Update authoritative operations, doctoring, and `CHANGELOG.md` evidence before merge.

## Task 1 — lifecycle and migration

1. Write fail-first lifecycle assertions for `CANCELLED`, cancellation identity, payload clearing, and lease clearing.
2. Add `V6__add_etl_job_cancellation.sql` with immutable Flyway history and descriptive constraints.
3. Verify migration documentation and clean-install/upgrade invariants.

Acceptance: `CANCELLED` is terminal; only active states retain payload; only `RUNNING` owns lease fields; only `CANCELLED` owns cancellation fields.

## Task 2 — service cancellation authority

1. Write fail-first service tests for pending/running cancellation, same-key replay, different-key rejection, owner isolation, completed-state conflicts, and invalid input before JDBC access.
2. Implement `EtlJobCancellation`, `EtlJobStatus.CANCELLED`, stable RFC 9457 errors, and `EtlJobService.cancelOwned`.
3. Keep one conditional update as authority; classify a zero-row result through an owner-scoped read.
4. Verify replay identity is domain-separated by principal and job.

Acceptance: one committed cancellation or one competing terminal transition wins; raw identity never persists.

## Task 3 — authenticated HTTP action

1. Add failing controller tests before the route.
2. Expose `POST /api/etl/jobs/{jobRecordId}/cancellation`.
3. Require authentication and a bounded `Idempotency-Key` before service access.
4. Return `200`, `Cache-Control: no-store`, a weak `ETag`, `Idempotency-Replayed`, and only operator-safe status fields.
5. Cover malformed identifiers, typed conflicts, data-access failures, and unexpected failures without leaking internal messages.

Acceptance: exact controller tests and strict controller coverage pass.

## Task 4 — worker and representation compatibility

1. Prove cancellation clears the worker lease and prevents former exact-lease success from committing.
2. Prove transactional target and response-ledger effects roll back when cancellation wins first.
3. Prove `CANCELLED` never retains `Retry-After`.
4. Prove cancellation invalidates the prior conditional status validator.
5. Prove success-first and cancellation-first races each leave exactly one terminal state.

Acceptance: concurrency tests demonstrate stale-lease rejection rather than duplicate terminal commit.

## Task 5 — operator evidence and rollback

1. Add documentation contract tests before missing docs are restored.
2. Document endpoint semantics, replay, database authority, race outcomes, observability, migration rehearsal, rollback, and external connector limitation.
3. Record domain-separated replay identity in doctoring with current NIST publication/revision evidence.
4. Update `CHANGELOG.md` under Unreleased with the buyer-visible cancellation slice.
5. Preserve old PR #133 and its fail-first history until this replacement proves equivalent or stronger exact-head behavior; old checks and approvals do not transfer.

## Run all verification

Run all verification after the final source/documentation change:

```bash
./mvnw -B test
git diff --check
```

Then refetch the exact head, live base tip, CI, Dependency Review, SBOM, commit statuses, formal reviews, unresolved threads, and mergeability. SAST or security evidence absent because this PR is stacked on a non-default base remains not passing and must not be inferred from a predecessor or synthetic merge.

## Merge and rollout acceptance

- Exact ancestry from the immediate predecessor remains intact.
- Exact-head CI, dependency, SBOM, security, coverage, and commit-status gates pass where required.
- Zero actionable unresolved review thread remains.
- A qualifying independent non-author formal `APPROVED` review is anchored to the exact unchanged head.
- Migration/rollback and compatibility evidence are complete.
- Protected merge uses expected-head semantics without bypass.
- Post-merge operational proof confirms the protected branch serves the cancellation contract before the next dependent slice deepens the stack.

## References — APA 7th

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/rfc/rfc9110

Nottingham, M., Wilde, E., & Dalal, S. (2023). *Problem details for HTTP APIs* (RFC 9457). RFC Editor. https://www.rfc-editor.org/rfc/rfc9457

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: UPDATE*. https://www.postgresql.org/docs/18/sql-update.html
