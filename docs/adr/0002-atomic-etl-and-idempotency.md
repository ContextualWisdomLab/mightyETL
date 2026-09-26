# ADR-0002: Atomic Synchronous ETL and Principal-Scoped Idempotency

**Status:** Accepted  
**Date:** 2026-08-09 (reconciles protected implementation)

## Context

Per-record asynchronous fan-out can commit a prefix before a later failure and complicates deterministic replay. Retried network/API requests can duplicate target writes.

## Decision

For synchronous `POST /api/etl/process`:

1. bound payload bytes and record count;
2. parse/validate/transform the complete batch before the first target write;
3. write accepted rows synchronously within one Spring transaction;
4. retry only transient data-access failures;
5. optionally support principal-scoped `Idempotency-Key` using a nonblocking PostgreSQL transaction lock, request digest, and durable response ledger;
6. commit target writes and response ledger in the same transaction.

Raw principal/key values are not persisted.

## Consequences

- accepted local target work is all-or-nothing;
- same-intent committed retries replay without duplicate local effects;
- throughput comes from bounded request/service scaling rather than unbounded per-record futures;
- remote connector effects require independent transactional/idempotency/compensation proof.

## Alternatives rejected

- **Partial per-record success:** ambiguous recovery and inconsistent replay.
- **In-memory idempotency:** lost on restart and unsafe across replicas.
- **Store raw client keys/principals:** unnecessary sensitive-data retention.
