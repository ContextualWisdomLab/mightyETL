# Durable ETL Job Replay Implementation Plan

> **Execution rule:** implement every state, lineage, and admission boundary test-first and preserve the terminal source as immutable evidence.

**Goal:** Create a new owner-scoped durable job from a failed or cancelled source only after the client resupplies the exact original payload.

**Architecture:** Store replay lineage on the new job row, bind immediate-source and root references to the same principal through composite owner-scoped foreign keys, use a versioned principal-scoped replay-key hash in the existing submission identity column, serialize creation with the existing transaction-lock boundary, verify payload digest against the terminal source, and return the existing accepted-job wire model.

**Tech Stack:** Java 25, Spring MVC, Spring transactions, JdbcTemplate, PostgreSQL 18, Flyway, H2 integration tests, JUnit 5, Mockito, JaCoCo, Maven.

## Global constraints

- Never update a terminal source back to `PENDING`.
- Allow only `FAILED` and `CANCELLED` sources.
- Validate identifier, replay key, principal, and complete payload before lock or table access.
- Persist no raw principal or raw replay key.
- Require PostgreSQL to reject source or root lineage whose `principal_scope_hash` differs from the new row.
- Preserve zero-missed configured production instruction, line, method, and branch coverage.
- Preserve no-skipped project tests and beginner-readable public Javadoc.
- Use descriptive multi-word `snake_case` database objects.
- Keep all existing review-agent credentials and workflows unchanged.

## Task 1 — Lock the V7 lineage schema first

**Files**
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobMigrationDocumentationTest.java`
- Create: `etl-service/src/main/resources/db/migration/V7__add_etl_job_replay_lineage.sql`

- [ ] Require all three lineage columns, bounded generation, complete-null-or-complete-non-null lifecycle, self-reference rejection, named composite owner-scoped foreign keys, their named `(job_record_id, principal_scope_hash)` unique support constraint, and `ON DELETE RESTRICT`.
- [ ] Reject legacy one-column source or root foreign keys because they permit cross-owner lineage at the database layer.
- [ ] Run the focused migration test and observe failure because V7 or the tenant-integrity constraints are absent.
- [ ] Implement the additive transactional migration.
- [ ] Rerun the focused test and commit.

## Task 2 — Define immutable replay models and errors

**Files**
- Create: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobReplay.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/service/EtlRequestError.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobReplayTest.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/service/EtlRequestExceptionTest.java`

- [ ] Add fail-first model validation and stable RFC 9457 metadata tests.
- [ ] Add the immutable replay result with new job ID, `PENDING`, and replay flag only.
- [ ] Add required, mismatch, reused, in-progress, active, succeeded, and generation-exhausted errors.
- [ ] Run focused tests and commit.

## Task 3 — Implement the service transaction test-first

**Files**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobReplayServiceIntegrationTest.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobReplayServiceBoundaryTest.java`
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/job/EtlJobService.java`

**Public interface**

```java
EtlJobReplay replayOwned(
    UUID sourceJobRecordId,
    String requestPayload,
    String replayKey,
    String principalScope
)
```

- [ ] Add failing tests for failed source, cancelled source, source immutability, payload mismatch, key replay/reuse, owner isolation, active/succeeded rejection, lineage root/generation, generation exhaustion, and validation before JDBC.
- [ ] Run focused tests and observe compile/assertion failure.
- [ ] Add the versioned replay domain and transaction-lock identity.
- [ ] Add existing-replay lookup and source-lineage lookup.
- [ ] Insert one ordinary `PENDING` job with verified payload and lineage.
- [ ] Run focused tests and commit.

## Task 4 — Add the HTTP resource

**Files**
- Modify: `etl-service/src/main/java/com/xtrmetl/etl/controller/EtlJobController.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobControllerTest.java`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobControllerFailureTest.java`

- [ ] Add fail-first tests for first acceptance, replay, authentication, missing key, malformed source, typed error, database failure, and unexpected failure.
- [ ] Implement `POST /api/etl/jobs/{sourceJobRecordId}/replays` with `202`, `Location`, no-store, and replay header.
- [ ] Run focused tests and commit.

## Task 5 — Prove lifecycle and concurrency compatibility

**Files**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobReplayClaimIntegrationTest.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobReplayConcurrencyIntegrationTest.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/job/EtlJobReplayLineageIntegrationTest.java`

- [ ] Prove a new replay row can be claimed by the ordinary worker.
- [ ] Prove an unavailable transaction lock returns replay-in-progress without insertion.
- [ ] Prove retry after a committed first replay returns the same new row.
- [ ] Prove replay-of-replay preserves the root and increments generation.
- [ ] Prove generation 100 fails before insertion.
- [ ] Prove PostgreSQL rejects a replay source or root from another `principal_scope_hash`, independent of application owner predicates.
- [ ] Run focused and full tests and commit.

## Task 6 — Finish operations, provenance, and exact-head verification

**Files**
- Create: `docs/operations/durable-job-replay.md`
- Create: `docs/doctoring/durable-job-replay-key-domain-separation.md`
- Modify: `docs/etl/durable-job-intake.md`
- Modify: `CHANGELOG.md`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/DurableJobReplayDocumentationTest.java`

- [ ] Require source immutability, payload digest proof, composite owner-scoped foreign keys, cross-owner lineage rejection, concurrency, generation limit, connector limitation, rollout, and rollback documentation first.
- [ ] Document W3C PROV mapping as an export contract, not a database-authority substitute.
- [ ] Record APA 7th primary references and the versioned replay-key compatibility boundary.
- [ ] Rehearse the migration on PostgreSQL 18 and verify the owner-scoped source/root foreign-key failures before production rollout.
- [ ] Run all verification through exact-head CI: `./mvnw -B test`, configured coverage gates, and `git diff --check`.
- [ ] Keep the PR draft until every stacked-target gate succeeds.

## Plan self-review

- Every issue #134 acceptance requirement maps to a task.
- The source is never mutated.
- Replay-key and payload conflicts are distinguished without disclosing source existence across principals.
- New jobs enter the existing worker lifecycle rather than creating a second execution engine.
- PostgreSQL and application owner predicates independently reject cross-owner lineage.
- No placeholder, ambiguous public signature, or unbounded database object name remains.
- Verification requires that no project test is skipped.
