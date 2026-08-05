# Durable ETL Job Lease Worker Design

## Status

Accepted implementation design for issue #120. This is a bounded follow-on to the durable
asynchronous intake merged in PR #119 and is stacked on PR #121 until that workflow-security
prerequisite reaches `develop`.

## Product outcome

Accepted asynchronous ETL jobs must progress from `PENDING` to a terminal state without depending on
the client connection or one service replica. The worker must distribute work across replicas through
PostgreSQL row locking, fence stale owners, bound retry attempts, atomically couple response-ledger
and target effects with terminal success, clear retained payloads at terminal state, and expose only
stable non-sensitive status metadata through the existing owner-scoped API.

## Scope

This slice adds:

- a PostgreSQL-owned claim operation using deterministic ordering and `FOR UPDATE SKIP LOCKED`;
- process-lifetime `lease_owner_id` and per-claim `lease_claim_id` fencing;
- lease expiry and reclaim;
- bounded attempts with deterministic terminal failure codes;
- fixed-delay polling that is disabled by default;
- hashed durable execution identity copied from the accepted job;
- response-ledger replay or creation, target writes, and conditional `SUCCEEDED` in one transaction;
- retry and failure transitions that require the exact live lease;
- finite-cardinality execution metrics;
- migration, rollback, privacy, operations, and failure-recovery documentation.

Cancellation, priorities, recurring schedules, manual replay, result-body exposure, and a dead-letter
user interface remain out of scope.

## Data model

Flyway migration `V3__add_etl_job_lease_fencing.sql` adds the following descriptive `snake_case`
columns to `etl_job_records`:

- `lease_claim_id UUID` — unique token generated for every claim or reclaim;
- `lease_owner_id VARCHAR(128)` — stable non-sensitive identifier for one worker process;
- `lease_expires_at TIMESTAMPTZ` — database-time expiry boundary.

A lifecycle constraint requires all three lease columns for `RUNNING` rows and requires all three to
be null for every other state. A failure lifecycle constraint requires `failure_code` only for
`FAILED` rows. The existing terminal-payload constraint remains authoritative. An eligibility index
covers `job_status`, `lease_expires_at`, `created_at`, and `job_record_id`.

The claim also carries the accepted row's `principal_scope_hash`, `submission_key_hash`, and
`request_digest`. These independent lowercase SHA-256 values support durable execution without
persisting or reconstructing raw authenticated principals or raw client idempotency keys.

## Claim protocol

`EtlJobLeaseRepository.claimNext` runs in one transaction:

1. Terminalize eligible rows whose `attempt_count` has reached the configured maximum. Clear
   `request_payload` and all lease columns and assign `etl_worker_attempts_exhausted`.
2. Select one `PENDING` row or one expired `RUNNING` row with `attempt_count < max_attempts`, ordered
   by `created_at, job_record_id`, using `FETCH FIRST 1 ROW ONLY FOR UPDATE SKIP LOCKED`.
3. Read `CURRENT_TIMESTAMP` from the database in the same statement and derive the next expiry from
   that database time.
4. Generate a new `lease_claim_id`, increment `attempt_count`, set `RUNNING`, set the owner and
   expiry, clear any prior failure code, and commit.

The scheduler does not provide uniqueness. The database row lock and state predicate are the
cross-replica authority. PostgreSQL documents `SKIP LOCKED` as suitable for avoiding contention among
multiple consumers of a queue-like table while warning that it is not a general-purpose consistent
view. That limitation is appropriate because each worker needs one exclusive claim rather than a
complete snapshot.

## Durable idempotent execution and fencing

`EtlJobExecutionService.execute` starts one transaction and delegates to
`EtlJobIdempotencyService` before attempting terminal success.

The idempotency service:

1. requires an actual Spring transaction;
2. recomputes the SHA-256 digest of `request_payload` and compares it with the stored
   `request_digest` before lock or table access;
3. domain-separates and hashes `principal_scope_hash` plus `submission_key_hash` into a response
   ledger key without recovering raw identity values;
4. acquires the existing transaction-lifetime `EtlRequestLock` for that key;
5. replays a matching `etl_idempotency_records` response or calls the existing validated
   `EtlService.processData` target writer and inserts the response ledger row.

The execution service then conditionally transitions the job to `SUCCEEDED` only when all of the
following still match:

- `job_record_id`;
- `job_status = 'RUNNING'`;
- exact `lease_claim_id`;
- exact `lease_owner_id`;
- `lease_expires_at > CURRENT_TIMESTAMP`.

If the conditional update affects no row, `StaleEtlJobLeaseException` is thrown. The exception rolls
back the same transaction, including target and response-ledger writes. An expired or superseded
worker therefore cannot commit duplicate target effects, create a misleading response ledger, or
terminalize a newer owner's job.

## Failure policy

The polling coordinator catches execution failures after the execution transaction rolls back and
performs a separate exact-lease transition:

- `TransientDataAccessException`: return to `PENDING` when attempts remain; otherwise terminal
  `FAILED` with `etl_target_unavailable`;
- `EtlJobIntegrityException`: terminal `FAILED` with `etl_job_integrity_failure`;
- `EtlRequestException`: terminal `FAILED` with the existing stable request `errorCode`;
- other `DataAccessException`: terminal `FAILED` with `etl_target_failure`;
- other `RuntimeException`: terminal `FAILED` with `etl_internal_error`;
- `StaleEtlJobLeaseException`: make no state change because another owner or expiry boundary is
  authoritative.

Every retry or failure update repeats the exact-live-lease predicate. A zero-row update is treated as
stale evidence, not as success.

## Scheduling and activation

Spring fixed-delay scheduling is used because the next delay is measured after completion of the
previous invocation. `mightyetl.etl.jobs.worker.enabled` and its supported `xtrmetl.*` alias default
to `false`. Configurable values are bounded and validated:

- `fixed-delay-milliseconds` > 0;
- `initial-delay-milliseconds` >= 0;
- `lease-duration-seconds` > 0;
- `max-attempts` between 1 and 100;
- `lease-owner-id` is 8–128 safe ASCII characters and defaults to a process-lifetime generated
  identifier.

One polling invocation claims at most one job. Horizontal throughput is achieved by replicas and
repeated fixed-delay invocations rather than unbounded in-process fan-out.

## Observability and privacy

The worker emits a duration timer and a finite outcome counter for `idle`, `claimed`, `succeeded`,
`retried`, `failed`, and `stale`. Metric tags never include payloads, principals, idempotency keys,
hashes, job identifiers, SQL, lease identifiers, exception classes, or exception messages. Logs
follow the same rule. Database client instrumentation should retain stable OpenTelemetry
SQL/PostgreSQL semantic conventions and avoid opting raw query text or parameters into telemetry
unless the deployment has separately assessed that exposure.

## Testing strategy

- Migration tests enforce descriptive names, lifecycle constraints, index shape, and rollback
  instructions.
- Repository integration tests use H2's supported `FOR UPDATE SKIP LOCKED` syntax to prove one live
  claim, deterministic ordering, expiry reclaim, attempt increment, execution identity, and
  exhaustion terminalization.
- Idempotency integration tests prove first execution, response replay without duplicate target
  writes, payload digest rejection before locking, ledger conflict rejection, transient lock
  contention, and fail-closed transaction requirements.
- Execution integration tests prove target rows, response ledger, and `SUCCEEDED` commit together and
  prove a stale claim rolls all three effects back.
- Coordinator tests cover every failure classification, retry bound, zero-work poll, metrics outcome,
  and stale transition.
- Property tests cover every validation boundary and generated owner identifier.
- Documentation and coverage policy tests require complete public Javadoc and zero missed
  instruction, line, method, and branch coverage for the durable-job package.

## Rollback

Before application rollback, stop all workers and disable intake. Allow active leases to expire,
confirm no `RUNNING` rows remain, and decide whether pending payloads will be drained or retained
under an approved exception. Roll back the application first. The three lease columns and eligibility
index may be removed only after all rows are non-running and no deployed binary reads them. Flyway
versioned migrations are not edited or deleted after publication; a forward compensating migration
must perform any production schema reversal.

## Standards and primary documentation

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor.
https://www.rfc-editor.org/rfc/rfc9110.html

OpenTelemetry Authors. (2026). *OpenTelemetry semantic conventions 1.43.0: Semantic conventions for
SQL databases client operations*. Cloud Native Computing Foundation.
https://opentelemetry.io/docs/specs/semconv/db/sql/

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: SELECT*.
https://www.postgresql.org/docs/18/sql-select.html

Spring Authors. (2026). *Task execution and scheduling*. Broadcom.
https://docs.spring.io/spring-framework/reference/integration/scheduling.html
