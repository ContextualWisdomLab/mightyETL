# Durable ETL Job Lease Worker Design

## Status

Accepted implementation design for issue #120. This is a bounded follow-on to the durable asynchronous intake merged in PR #119 and is stacked on PR #121 until that workflow-security prerequisite reaches `develop`.

## Product outcome

Accepted asynchronous ETL jobs must progress from `PENDING` to a terminal state without depending on the client connection or on one service replica. The worker must distribute work across replicas through PostgreSQL row locking, fence stale owners, bound retry attempts, atomically couple target effects with terminal success, clear retained payloads at terminal state, and expose only stable non-sensitive status metadata through the existing owner-scoped API.

## Scope

This slice adds:

- a PostgreSQL-owned claim operation using deterministic ordering and `FOR UPDATE SKIP LOCKED`;
- process-lifetime `lease_owner_id` and per-claim `lease_claim_id` fencing;
- lease expiry and reclaim;
- bounded attempts with deterministic terminal failure codes;
- fixed-delay polling that is disabled by default;
- atomic ETL target writes plus conditional `SUCCEEDED` transition;
- retry and failure transitions that require the exact live lease;
- finite-cardinality execution metrics;
- migration, rollback, privacy, operations, and failure-recovery documentation.

Cancellation, priorities, recurring schedules, manual replay, result-body persistence, and a dead-letter user interface remain out of scope.

## Data model

Flyway migration `V3__add_etl_job_lease_fencing.sql` adds the following descriptive `snake_case` columns to `etl_job_records`:

- `lease_claim_id UUID` — unique token generated for every claim or reclaim;
- `lease_owner_id VARCHAR(128)` — stable non-sensitive identifier for one worker process;
- `lease_expires_at TIMESTAMPTZ` — database-time expiry boundary.

A lifecycle constraint requires all three lease columns for `RUNNING` rows and requires all three to be null for every other state. A failure lifecycle constraint requires `failure_code` only for `FAILED` rows. The existing terminal-payload constraint remains authoritative. An eligibility index covers `job_status`, `lease_expires_at`, `created_at`, and `job_record_id`.

## Claim protocol

`EtlJobLeaseRepository.claimNext` runs in one transaction:

1. Terminalize eligible rows whose `attempt_count` has reached the configured maximum. Clear `request_payload` and all lease columns and assign `etl_worker_attempts_exhausted`.
2. Select one `PENDING` row or one expired `RUNNING` row with `attempt_count < max_attempts`, ordered by `created_at, job_record_id`, using `FETCH FIRST 1 ROW ONLY FOR UPDATE SKIP LOCKED`.
3. Read `CURRENT_TIMESTAMP` from the database in the same statement and derive the next expiry from that database time.
4. Generate a new `lease_claim_id`, increment `attempt_count`, set `RUNNING`, set the owner and expiry, clear any prior failure code, and commit.

The scheduler does not provide uniqueness. The database row lock and state predicate are the cross-replica authority. PostgreSQL documents `SKIP LOCKED` as suitable for avoiding contention among multiple consumers of a queue-like table, while warning that it is not a general-purpose consistent view; that limitation is appropriate here because each worker needs one exclusive claim rather than a complete snapshot.

## Execution and fencing

`EtlJobExecutionService.execute` starts a new transaction, calls the existing `EtlService.processData` through a separate Spring bean, then conditionally transitions the job to `SUCCEEDED` only when all of the following still match:

- `job_record_id`;
- `job_status = 'RUNNING'`;
- exact `lease_claim_id`;
- exact `lease_owner_id`;
- `lease_expires_at > CURRENT_TIMESTAMP`.

If the conditional update affects no row, `StaleEtlJobLeaseException` is thrown. The exception rolls back the same transaction, including all target writes, so an expired or superseded worker cannot commit target effects.

## Failure policy

The polling coordinator catches execution failures after the execution transaction rolls back and performs a separate exact-lease transition:

- `TransientDataAccessException`: return to `PENDING` when attempts remain; otherwise terminal `FAILED` with `etl_target_unavailable`;
- `EtlRequestException`: terminal `FAILED` with the existing stable request `errorCode`;
- other `DataAccessException`: terminal `FAILED` with `etl_target_failure`;
- other `RuntimeException`: terminal `FAILED` with `etl_internal_error`;
- `StaleEtlJobLeaseException`: make no state change because another owner or expiry boundary is authoritative.

Every retry or failure update repeats the exact-live-lease predicate. A zero-row update is treated as stale evidence, not as success.

## Scheduling and activation

Spring fixed-delay scheduling is used because the next delay is measured after completion of the previous invocation. `xtrmetl.etl.jobs.worker.enabled` defaults to `false`; operators must explicitly enable both intake and worker execution. Configurable values are bounded and validated:

- `fixed-delay-milliseconds` > 0;
- `initial-delay-milliseconds` >= 0;
- `lease-duration-seconds` > 0;
- `max-attempts` between 1 and 100;
- `lease-owner-id` is 8–128 safe ASCII characters and defaults to a process-lifetime generated identifier.

One polling invocation claims at most one job. Horizontal throughput is achieved by replicas and repeated fixed-delay invocations rather than unbounded in-process fan-out.

## Observability and privacy

The worker emits a duration timer and a finite outcome counter for `claimed`, `succeeded`, `retried`, `failed`, and `stale`. Metric tags never include payloads, principals, idempotency keys, job identifiers, SQL, lease identifiers, or exception messages. Logs follow the same rule. Database client instrumentation should retain the stable OpenTelemetry SQL semantic conventions and avoid opting raw query text into telemetry unless the deployment has separately assessed that exposure.

## Testing strategy

- Migration tests enforce descriptive names, lifecycle constraints, index shape, and rollback instructions.
- Repository integration tests use H2's supported `FOR UPDATE SKIP LOCKED` syntax to prove one live claim, deterministic ordering, expiry reclaim, attempt increment, and exhaustion terminalization.
- Execution integration tests prove target rows and `SUCCEEDED` commit together and prove a stale claim rolls target writes back.
- Coordinator tests cover every failure classification, retry bound, zero-work poll, metrics outcome, and stale transition.
- Property tests cover every validation boundary and generated owner identifier.
- Documentation and coverage policy tests require complete public Javadoc and zero missed instruction, line, method, and branch coverage for the durable-job package.

## Rollback

Before application rollback, stop all workers and disable intake. Allow active leases to expire, confirm no `RUNNING` rows remain, and decide whether pending payloads will be drained or explicitly failed. Roll back the application first. The three lease columns and eligibility index may be removed only after all rows are non-running and no deployed binary reads them. Flyway versioned migrations are not edited or deleted after publication; a forward compensating migration must perform any production schema reversal.

## Standards and primary documentation

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). Internet Engineering Task Force. https://www.rfc-editor.org/rfc/rfc9110.html

OpenTelemetry Authors. (2026). *Semantic conventions for database calls and systems*. Cloud Native Computing Foundation. https://opentelemetry.io/docs/specs/semconv/db/

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: SELECT*. https://www.postgresql.org/docs/18/sql-select.html

Spring Authors. (2026). *Task execution and scheduling*. Broadcom. https://docs.spring.io/spring-framework/reference/integration/scheduling.html
