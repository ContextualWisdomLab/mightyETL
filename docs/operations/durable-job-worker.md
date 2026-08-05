# Durable ETL job worker operations

## Purpose and safety boundary

The durable worker moves accepted `etl_job_records` from `PENDING` through `RUNNING` to
`SUCCEEDED` or `FAILED`. PostgreSQL row state is the distribution and fencing authority. Spring's
fixed-delay scheduler only initiates polls; it does not establish exclusivity across replicas.

The worker is fail-closed. Both of the following product switches must be reviewed deliberately:

```text
mightyetl.etl.jobs.intake-enabled=true
mightyetl.etl.jobs.worker.enabled=true
```

The supported legacy aliases are `xtrmetl.etl.jobs.intake-enabled` and
`xtrmetl.etl.jobs.worker.enabled`. Environment variables are `ETL_JOB_INTAKE_ENABLED` and
`ETL_JOB_WORKER_ENABLED`. When both full namespaces are configured, `mightyetl.*` wins.

Enable intake without the worker only for controlled maintenance windows where retained `PENDING`
payloads are acceptable. Enable the worker without intake only to drain already accepted work.

## Configuration

| Preferred property | Environment variable | Default | Constraint |
| --- | --- | ---: | --- |
| `mightyetl.etl.jobs.worker.enabled` | `ETL_JOB_WORKER_ENABLED` | `false` | explicit opt-in |
| `mightyetl.etl.jobs.worker.fixed-delay-milliseconds` | `ETL_JOB_WORKER_FIXED_DELAY_MILLISECONDS` | `5000` | 1 through 86,400,000 |
| `mightyetl.etl.jobs.worker.initial-delay-milliseconds` | `ETL_JOB_WORKER_INITIAL_DELAY_MILLISECONDS` | `5000` | 0 through 86,400,000 |
| `mightyetl.etl.jobs.worker.lease-duration-seconds` | `ETL_JOB_WORKER_LEASE_DURATION_SECONDS` | `300` | 1 through 86,400 |
| `mightyetl.etl.jobs.worker.max-attempts` | `ETL_JOB_WORKER_MAX_ATTEMPTS` | `3` | 1 through 100 |
| `mightyetl.etl.jobs.worker.lease-owner-id` | deployment-specific | generated | 8–128 safe ASCII characters |

Scheduler delays and lease durations have a one-day safety ceiling. Configuration binding and the
lease repository enforce the same limit, so direct repository callers cannot bypass it. Values above
the ceiling fail application binding or claim validation rather than creating an effectively
permanent polling pause, arithmetic overflow, or multi-day stale-work recovery delay.

Set an explicit `lease-owner-id` only when the deployment platform can guarantee one stable,
non-sensitive value per process. Never use a hostname containing customer data, a pod annotation
containing credentials, an email address, a tenant identifier, or a raw infrastructure token.

Choose a lease duration longer than the normal high-percentile execution time plus database and
network variance. The current slice does not renew leases. A lease that expires during execution
causes the final success transition to fail and rolls back target and response-ledger writes. If a
normal execution can exceed one day, do not increase the ceiling silently; implement and validate
lease renewal as a separate fenced capability first.

## Claim, execution, and recovery

Each poll handles at most one job:

1. Eligible rows at or above `max-attempts` become terminal `FAILED`; their payload and lease fields
   are cleared with `etl_worker_attempts_exhausted`.
2. The worker selects the oldest `PENDING` row or expired `RUNNING` row below the attempt limit using
   `FOR UPDATE SKIP LOCKED`.
3. The claim writes a new `lease_claim_id`, the process `lease_owner_id`, database-derived expiry,
   and incremented attempt count.
4. The execution transaction verifies the retained payload digest, acquires the domain-separated
   response-ledger lock, replays or writes `etl_idempotency_records`, writes target rows, and then
   conditionally marks the exact live lease `SUCCEEDED`.
5. A stale, superseded, or expired lease cannot commit target rows, response-ledger rows, or terminal
   state. The whole execution transaction rolls back.

An expired `RUNNING` job is reclaimed with a new claim identifier. The earlier worker may continue
using CPU, but its target and lifecycle writes cannot commit after losing the exact live lease.

## Stable failure codes

| Failure code | Meaning | Operator response |
| --- | --- | --- |
| `etl_worker_attempts_exhausted` | an eligible row had no remaining claim attempt | inspect target availability and payload validity before any future replay feature |
| `etl_target_unavailable` | transient database failures consumed the attempt limit | restore database service and retain evidence for incident review |
| `etl_target_failure` | non-transient database write failure | inspect schema, constraints, permissions, and target compatibility |
| `etl_job_integrity_failure` | retained payload or response-ledger identity conflicted | stop affected workers, preserve database evidence, investigate tampering or inconsistent migration |
| `etl_internal_error` | unexpected non-database runtime failure | inspect sanitized application diagnostics and open a defect |
| existing `etl_*` request codes | retained request failed deterministic ETL validation | correct the producer or migration source; do not blindly retry |

Terminal states clear `request_payload` in the same state transition. The status API exposes only the
stable failure code, attempt count, lifecycle state, and timestamps to the authenticated owner.

## Observability and SLO evidence

The worker publishes finite-cardinality metrics only:

- `etl.jobs.worker.outcomes{outcome=idle|claimed|succeeded|retried|failed|stale}`;
- `etl.jobs.execution.duration{outcome=idle|succeeded|retried|failed|stale}`.

Do not add payloads, raw principals, raw idempotency keys, hashes, job identifiers, lease identifiers,
SQL text, exception messages, or unbounded exception classes as metric tags or log fields.

Recommended initial service-level indicators are:

- accepted-to-terminal latency by status;
- oldest eligible `PENDING` age;
- expired `RUNNING` count;
- terminal success ratio;
- retry and stale outcome rates;
- exhausted-attempt and integrity-failure counts;
- database connection-pool saturation and transaction latency.

A production SLO must be calibrated from representative load and recovery tests. Do not claim a
numerical availability or latency SLO until monitoring, alert thresholds, and retained evidence have
been validated in the buyer's deployment topology.

For OpenTelemetry database telemetry, use the stable SQL/PostgreSQL semantic conventions where the
instrumentation supports them. Prefer low-cardinality `db.query.summary`; treat raw `db.query.text`
and query parameters as opt-in sensitive telemetry requiring a separate privacy assessment.

## Incident procedures

### Backlog growth

1. Confirm intake and worker switches independently.
2. Check database connectivity, pool saturation, lock waits, and worker failure outcomes.
3. Compare oldest eligible `PENDING` age with execution duration.
4. Add replicas only after confirming the database can support the additional claim and target-write
   concurrency.
5. Do not update lifecycle fields manually while workers are active.

### Repeated stale outcomes

1. Compare the configured lease duration with high-percentile transaction duration.
2. Check clock-independent database latency and long-running statements; lease decisions use database
   time.
3. Verify every process has a safe, distinct lease owner identifier.
4. Increase the lease duration only within the one-day ceiling and only after confirming that crash
   recovery delay remains acceptable; implement lease renewal instead of exceeding the ceiling.

### Integrity failure

1. Disable the worker while preserving intake only if continued payload retention is acceptable.
2. Snapshot the affected database under incident-response controls.
3. Compare the job's stored request digest with a digest of the retained payload and compare the
   domain-separated response-ledger row.
4. Review migration, restore, replication, and unauthorized-write evidence.
5. Do not disclose hashes or payloads in tickets, chat, dashboards, or ordinary logs.

## Deployment and rollback

Before enabling the worker:

1. Apply and validate Flyway migration `V3__add_etl_job_lease_fencing.sql`.
2. Confirm the application principal has only the required table and advisory-lock permissions.
3. Run migration, claim-contention, stale-lease rollback, response-replay, and target compatibility
   tests against a production-equivalent PostgreSQL environment.
4. Deploy with the worker disabled, inspect health and schema evidence, then enable a canary replica.
5. Verify target, response-ledger, and terminal state atomicity before widening rollout.

Rollback order is fail-closed:

1. Disable intake when new accepted work must stop.
2. Disable all workers and wait for active transactions to complete or roll back.
3. Confirm no `RUNNING` rows remain; allow leases to expire if necessary.
4. Decide whether `PENDING` payloads will be drained by the current version or retained under an
   approved data-retention exception.
5. Roll back application binaries before any schema compensation.
6. Never edit or delete an applied Flyway versioned migration. Use a new forward compensating
   migration only after every deployed binary no longer reads the lease columns.

## Standards and primary documentation

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP semantics* (RFC 9110). RFC Editor.
https://www.rfc-editor.org/rfc/rfc9110

OpenTelemetry Authors. (2026). *OpenTelemetry semantic conventions 1.43.0: Semantic conventions for
SQL databases client operations*. Cloud Native Computing Foundation.
https://opentelemetry.io/docs/specs/semconv/db/sql/

PostgreSQL Global Development Group. (2026). *PostgreSQL 18 documentation: SELECT*.
https://www.postgresql.org/docs/18/sql-select.html

Spring Authors. (2026). *Task execution and scheduling*. Broadcom.
https://docs.spring.io/spring-framework/reference/integration/scheduling.html
