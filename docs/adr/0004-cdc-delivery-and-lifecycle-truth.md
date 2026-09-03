# ADR-0004: CDC Delivery and Lifecycle Truthfulness

**Status:** Accepted with known gaps  
**Date:** 2026-08-09

## Context

A CDC system can lose operator trust if source progress is marked before downstream Kafka publication is acknowledged or if `stop()` reports stopped before the asynchronous Debezium engine task actually finishes flushing/returning.

## Decision

mightyETL's target CDC contract is replay-tolerant at-least-once delivery with broker acknowledgement before Debezium record progress, plus bounded truthful graceful-stop completion. Broker/network failure or acknowledgement timeout must not advance the affected source record. Ordinary stop must distinguish requested shutdown from proven engine-task completion and fail closed on a bounded timeout/interruption.

Protected develop does not yet meet both parts:

- PR #139 is `active_pr` for acknowledgement-before-progress and finite acknowledgement waiting.
- Issue #141 is `planned` for capturing/waiting on the engine Future after `close()`.

## Consequences

- no end-to-end exactly-once marketing claim;
- downstream consumers/connectors must tolerate replay;
- operator status cannot be derived solely from clearing Java references;
- interruption/timeouts require explicit recovery semantics.

## Alternatives rejected

- **fire-and-forget Kafka send:** cannot prove delivery before source progress.
- **interrupt/cancel as normal stop:** Debezium graceful shutdown is the safer correctness path.
- **rename current stop as successful without waiting:** preserves false observability.

## Reference

Debezium. (2026). *Debezium Engine 3.4*. https://debezium.io/documentation/reference/3.4/development/engine.html
