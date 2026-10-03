# ADR-0003: PostgreSQL-Owned Durable Job Authority and Non-Destructive Stack Integration

**Status:** Accepted  
**Date:** 2026-08-09

## Context

Long-running ETL work needs restart-safe intake, execution state, owner isolation, cancellation/replay, and race resolution. The implementation evolved as a stack and several original branches diverged from their required predecessors.

## Decision

PostgreSQL `etl_job_records` is the durable lifecycle authority. Protected develop currently provides feature-gated intake/status. Later worker, pagination, polling, conditional status, cancellation, and replay are integrated only in exact predecessor order.

A state change is authorized by database predicates/constraints, not scheduler timing or HTTP request order. Lease-fenced worker terminal commits must prove the exact live lease. Cancellation/replay add new terminal/lineage semantics only with migration, rollback, old-reader compatibility, and concurrency evidence.

If a stack boundary cannot be repaired without destructive history rewriting, create a replacement branch from the exact predecessor, reapply bounded changes/test history through auditable commits, and preserve the old branch as historical evidence. Old checks/reviews/approvals never transfer.

## Consequences

- replicas converge on database-owned lifecycle truth;
- stale workers cannot overwrite newer state after exact lease fencing integrates;
- downstream stack evidence is invalidated by predecessor movement;
- protected docs must not describe active stack states/columns as shipped.

## Alternatives rejected

- **force-rebase/force-push old branches:** rewrites fail-first/review evidence.
- **scheduler-memory state:** not restart/replica safe.
- **read-then-write lifecycle authority:** race prone without a conditional database predicate.
