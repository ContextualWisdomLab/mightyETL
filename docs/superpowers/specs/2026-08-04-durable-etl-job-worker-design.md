# Durable ETL Job Worker Design

**Status:** Approved bounded continuation of issue #117 after PR #119  
**Date:** 2026-08-04  
**Target:** `etl-service`  
**Base branch:** `develop`

## Purpose

The intake slice accepts authenticated, idempotent ETL jobs but intentionally leaves them in
`PENDING`. This design completes the smallest buyer-visible execution vertical: multiple service
replicas may poll the same PostgreSQL table, exactly one replica claims a job at a time, a stale
worker cannot commit, target effects and terminal success share one transaction, retry attempts are
bounded, and terminal rows no longer retain the source payload.

Cancellation, prioritization, dead-letter replay, multi-stage orchestration, and a management UI are
not included. They require separate state-machine and product designs after this execution contract
is proven.

## Design principles

1. **PostgreSQL owns work distribution.** Scheduler instances are deliberately not coordinated.
   Every replica may poll; row locking and lease fencing determine ownership.
2. **Claim and execution use separate transactions.** A short claim transaction never holds a row
   lock while customer ETL work executes.
3. **The lease token is the fencing credential.** Every state mutation after claim must match both
   `job_record_id` and the opaque current `lease_token`.
4. **Success is atomic with target effects.** The durable synchronous idempotency ledger, target
   rows, and `SUCCEEDED` state commit or roll back together.
5. **Failure handling is bounded and non-sensitive.** Publicly stable snake_case failure codes are
   stored; exception text, SQL, credentials, payload fragments, and stack traces are not.
6. **The feature is fail-closed.** Intake and worker activation are independent, disabled-by-default
   operator choices.
7. **All new database identifiers are descriptive multi-word `snake_case`.**

## Components

### `EtlJobWorkerProperties`

A named Spring configuration-properties bean under `xtrmetl.etl.jobs.worker` exposes bounded values:

| Property | Default | Hard range | Purpose |
|---|---:|---:|---|
| `enabled` | `false` | boolean | Enables scheduled polling |
| `poll-delay-millis` | `5000` | 100–3,600,000 | Delay after one poll completes |
| `lease-duration-millis` | `300000` | 1,000–86,400,000 | Claim lifetime |
| `retry-delay-millis` | `5000` | 1–3,600,000 | Base retry delay |
| `max-attempts` | `3` | 1–20 | Maximum claims before terminal failure |
| `jobs-per-poll` | `1` | 1–32 | Bounded work per scheduled invocation |

Retry delay grows linearly by attempt number and is capped at one hour. The hard bounds make an
accidental configuration change unable to create an unbounded hot loop, day-scale invisible lease,
or one-poll resource spike.

### `EtlJobClaim`

An immutable internal record contains only the values required to execute one claim:

- `job_record_id`
- opaque `lease_token`
- exact stored `request_payload`
- one-based `attempt_count`
- `lease_expires_at`

It never contains raw principal names or submission keys.

### `EtlJobStore`

The store owns short transactional state transitions.

#### Claim

Within one transaction, it selects one eligible row in deterministic order:

```sql
SELECT job_record_id, request_payload, attempt_count
FROM etl_job_records
WHERE attempt_count < ?
  AND (
      (job_status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
      OR
      (job_status = 'RUNNING' AND lease_expires_at <= CURRENT_TIMESTAMP)
  )
ORDER BY
  CASE WHEN job_status = 'RUNNING' THEN 0 ELSE 1 END,
  COALESCE(lease_expires_at, next_attempt_at),
  created_at,
  job_record_id
FOR UPDATE SKIP LOCKED
LIMIT 1
```

The store then writes a new UUID token, a new expiry, `RUNNING`, and `attempt_count + 1`. The same
transaction returns the new claim. `SKIP LOCKED` is appropriate here because this is a queue-like
consumer pattern; it is not used for general-purpose consistent reads.

#### Failure transition

After an execution transaction rolls back, a separate store transaction updates only the row whose
`job_record_id`, `lease_token`, and `RUNNING` state still match. A transient failure with remaining
attempts returns the job to `PENDING`, clears lease fields, and writes `next_attempt_at`. Any
non-retryable failure, or exhaustion of `max_attempts`, sets `FAILED`, clears the request payload and
lease fields, and stores one stable failure code.

A zero-row update means the lease is stale or the state already changed. It is not retried and does
not overwrite the new owner.

### `EtlJobExecutionService`

This bean owns the long execution transaction. It first locks and revalidates the claimed row by
`job_record_id + lease_token`, requires `RUNNING`, a non-null payload, and an unexpired lease, then
calls the existing `EtlService.processDataIdempotently` through a separate Spring bean.

The durable idempotency namespace is fixed to `durable_etl_job_worker`; the semantic key is the
job UUID. Therefore a crash after target/ledger commit but before the client observes completion can
safely replay without another target effect.

Before committing, the service conditionally updates the same leased row to:

- `SUCCEEDED`
- `request_payload = NULL`
- `lease_token = NULL`
- `lease_expires_at = NULL`
- `failure_code = NULL`
- `processed_record_count = <count>`

The update again requires the current token and an unexpired lease. A zero-row update throws
`EtlJobLeaseLostException`, rolling back target rows, ledger insert, and terminal state together.

### `EtlJobWorker`

The worker is a thin orchestrator. `runOnce()` claims and executes at most one job. `poll()` invokes
`runOnce()` no more than `jobs-per-poll` times and stops when the queue is empty. It classifies
failures into stable codes and delegates fenced retry/terminal mutation to the store.

`@Scheduled(fixedDelayString = "#{@etlJobWorkerProperties.pollDelayMillis}")` is used. Spring fixed
delay is measured from completion of one invocation to the start of the next, so a slow batch does
not create overlapping invocations on the same scheduler thread. Correctness nevertheless remains
in PostgreSQL, not in that scheduler behavior.

## Database migration

`V3__add_etl_job_worker_leases.sql` adds:

- `lease_token UUID`
- `lease_expires_at TIMESTAMPTZ`
- `next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`
- `processed_record_count INTEGER`

It adds descriptive check constraints for:

- lease fields present together only in `RUNNING`;
- nonnegative `processed_record_count`;
- `processed_record_count` present only in `SUCCEEDED`;
- `failure_code` present only in `FAILED`;
- terminal payload clearing already required by the V2 invariant.

It replaces the broad status/created index with an eligibility-oriented index named
`etl_job_worker_queue_index`.

## Public status representation

`processedRecordCount` is added as an optional nonnegative terminal summary. It is present only for
successful jobs and omitted otherwise. The response continues to exclude payload, principal,
submission key, request digest, lease token, lease expiry, and internal hashes.

## Failure classification

| Throwable category | Stable stored code | Retry |
|---|---|---|
| `TransientDataAccessException` | `etl_target_unavailable` | Until `max-attempts` |
| Other `DataAccessException` | `etl_target_failure` | No |
| `EtlRequestException` | Its existing stable `errorCode` | No |
| Unexpected `RuntimeException` | `etl_internal_error` | No |
| `EtlJobLeaseLostException` | None | No state overwrite |

Only categories are persisted. Full diagnostic detail remains in server-side telemetry.

## Transaction and crash guarantees

- **Crash before target commit:** the target/ledger/success transaction rolls back. The lease later
  expires and another worker reclaims the job.
- **Crash after commit:** the row is already terminal and cannot be claimed again. If a storage
  failure causes an ambiguous retry, the job UUID idempotency ledger prevents duplicate target
  effects.
- **Lease expires during execution:** the final conditional update fails and the entire target
  transaction rolls back.
- **Old worker returns after reclaim:** its token no longer matches, so neither success nor failure
  can overwrite the current owner.
- **Multiple replicas:** locked rows are skipped and every successful claim receives a unique token.

## Test strategy

Tests are written before production code and must prove the following behaviors without skipped or
ignored cases:

1. property defaults, hard bounds, and capped retry delay;
2. claim-record validation;
3. two concurrent workers never claim the same row;
4. expired `RUNNING` rows are reclaimed with a new token and incremented attempt;
5. exhausted rows are not claimable;
6. stale-token success and failure updates are rejected;
7. target rows, idempotency ledger, and `SUCCEEDED` commit atomically;
8. a forced terminal-update constraint failure rolls the complete target transaction back;
9. transient failure requeues and exhausted transient failure terminates;
10. deterministic, non-transient, and unexpected failures store stable codes only;
11. successful and failed terminal transitions clear `request_payload`;
12. replay of the same completed job produces exactly one target effect;
13. multiple worker instances sharing one database process one queued job once;
14. the scheduled method uses fixed-delay semantics and the worker is disabled by default;
15. migration names, constraints, documentation, status privacy, Javadocs, and CHANGELOG remain
    aligned;
16. JaCoCo reports zero missed instructions, lines, methods, and branches for the complete durable
    job package.

H2-based tests cover transaction composition and deterministic state transitions. PostgreSQL-specific
`SKIP LOCKED` SQL remains separately asserted and is exercised by the repository's PostgreSQL
integration environment when available. No test silently rewrites source or treats an unavailable
required backend as success.

## Security and privacy

- The lease token is internal and never serialized by the API.
- The worker does not log request payloads or raw authentication data.
- Failure rows preserve only a bounded stable code.
- Payloads are cleared in both terminal states and enforced by database constraints.
- All claim, success, retry, and failure SQL is parameterized.
- The scheduler is disabled by default and has bounded throughput.

## MSA and modularity

The worker package depends on the existing ETL service through a narrow method call and on
PostgreSQL through `JdbcTemplate`. HTTP intake remains independently deployable with the worker
disabled. A worker-only service can later import the same package, datasource, and ETL execution
port without importing the controller. No organization-central workflow or naruon-specific runtime
dependency is introduced by this slice.

## Standards and authoritative sources

- PostgreSQL documents `SKIP LOCKED` as an inconsistent general view but an appropriate mechanism
  for avoiding lock contention among multiple consumers of queue-like tables.
- PostgreSQL documents bounded update batching using a CTE and notes that `SKIP LOCKED` prevents
  multiple commands from updating the same row.
- Spring documents fixed delay as the interval between completion of one invocation and start of the
  next.
- Spring's default proxy transaction model applies `@Transactional` to external calls intercepted by
  the proxy; therefore claim, execution, and failure transitions are separate beans rather than
  self-invoked methods.

### References

PostgreSQL Global Development Group. (2026a). *PostgreSQL 18 documentation: SELECT*.
https://www.postgresql.org/docs/18/sql-select.html

PostgreSQL Global Development Group. (2026b). *PostgreSQL 18 documentation: UPDATE*.
https://www.postgresql.org/docs/18/sql-update.html

Spring. (2026a). *Task execution and scheduling*. In *Spring Framework 6.2 reference documentation*.
https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html

Spring. (2026b). *Using `@Transactional`*. In *Spring Framework 6.2 reference documentation*.
https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/declarative/annotations.html
