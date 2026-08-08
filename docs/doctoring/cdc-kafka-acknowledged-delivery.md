# PostgreSQL-to-Kafka acknowledged-delivery evidence

Reviewed on: **2026-08-08**

## Buyer-visible reliability gap

The embedded PostgreSQL CDC path previously registered a one-record Debezium consumer and returned immediately after `KafkaTemplate.send(...)`. Spring Kafka returns a `CompletableFuture<SendResult<...>>` from the send operation, so returning from the handler proved only that a send was submitted to the producer API; the repository had no executable contract that Kafka had acknowledged that record before Debezium was allowed to advance processing state.

For an ETL/CDC product, that boundary is commercially material. A source position that advances independently of target acknowledgement can turn an ordinary broker outage or producer failure into an avoidable delivery ambiguity. The repair must therefore bind the source-processing decision to observable target completion without claiming a stronger end-to-end exactly-once guarantee than the architecture actually provides.

## Root-cause analysis

The defect was a mismatch between two asynchronous contracts:

1. Spring Kafka publishes through a future-returning `KafkaTemplate.send(...)` API. The caller must observe future completion when downstream acknowledgement is part of the caller's correctness condition.
2. Debezium Engine exposes the advanced `ChangeConsumer` / `RecordCommitter` contract so an embedded consumer can mark individual records processed and mark a batch finished only after application processing has completed.

The old path used neither completion signal together. It submitted the Kafka operation from a simple consumer and then returned. Retrying the whole workflow, increasing broker timeouts, or merely setting stronger producer acknowledgement options would not repair that application-level ordering defect by themselves.

## Feasibility analysis

The following remediation classes were evaluated against the current repository boundary.

### Executable now

Use Debezium's batch `ChangeConsumer` contract, await each Spring Kafka send future, and call `RecordCommitter.markProcessed(...)` only after successful completion. Mark the batch finished only after every record has reached its permitted terminal state. Keep the behavior in the existing `cdc-service` so no new service, credential, database, or separately leased repository is required.

This is the selected remediation because it addresses the root cause directly and can be tested deterministically with controlled `CompletableFuture` completion.

### Complementary producer controls

Require `acks=all`, explicit producer idempotence, `max.in.flight.requests.per.connection=5`, a 60-second default `delivery.timeout.ms`, and a 30-second default `max.block.ms`. These controls strengthen Kafka producer delivery behavior and bound operator-visible stalls, but they do not replace the application-level acknowledgement-before-offset contract.

### Not selected as this bounded slice

Migrating the product to Kafka Connect distributed mode, adding a transactional outbox, or introducing an external offset/acknowledgement ledger could provide different durability properties, but each is a materially larger architecture change. None is necessary to correct this specific embedded-engine ordering defect, and none should be introduced merely to make this pull request appear stronger.

## Implemented delivery state machine

For each Debezium event in a batch:

```text
receive ordered change event
→ if destination exists, run the optional canonical mapping observation
→ submit raw Debezium JSON to Kafka
→ await the returned CompletableFuture
→ on acknowledgement: increment kafkaPublishSuccess
→ mark this Debezium record processed
→ continue to the next event
→ after every record is processed: mark the Debezium batch finished
```

A destination-less engine event is treated as non-publishable metadata and is marked processed without touching Kafka or the publish counters.

A failed Kafka attempt increments `kafkaPublishFailure` and receives one bounded application-level retry. A second terminal failure raises `KafkaException`; the current record is not marked processed and the batch is not marked finished. A synchronous exception raised while initiating the send follows the same bounded retry policy. An `InterruptedException` while awaiting acknowledgement propagates immediately and does not advance the current record or batch.

The live Debezium engine is wired to this batch handler. The older one-record `handleChangeEvent(...)` method remains as a compatibility surface for direct callers and tests, but it is no longer the live source-offset progression path.

## Producer durability controls

`cdc-service/src/main/resources/application.yml` now requires:

- `acks: all`;
- `enable.idempotence=true`;
- `max.in.flight.requests.per.connection=5`;
- `delivery.timeout.ms=${CDC_KAFKA_DELIVERY_TIMEOUT_MS:60000}`;
- `max.block.ms=${CDC_KAFKA_MAX_BLOCK_MS:30000}`.

Apache Kafka documents that producer idempotence requires `acks=all`, retries greater than zero, and no more than five in-flight requests per connection. Kafka also defines `delivery.timeout.ms` as the upper bound for reporting success or failure after a record is accepted by the producer. The application leaves Kafka's retry machinery intact and adds only one bounded retry after a terminal send future failure.

## Operator evidence

`GET /api/cdc/status` now includes cumulative process-lifetime counters:

- `kafkaPublishSuccess`: records whose Kafka send completed successfully through the acknowledged live path;
- `kafkaPublishFailure`: failed application send attempts, including failures that were subsequently recovered by the one bounded retry.

These counters intentionally contain no payload, source key, principal, topic contents, credential, or exception text. They are diagnostic evidence, not a durable billing or audit ledger.

## TDD evidence

### RED — missing acknowledgement boundary

Commit `f7ca5f149df959d03ff330ea0e374bc8fcb031e4` introduced the initial contract tests before production implementation. CI run `31259345232` failed because `CdcService` did not provide `handleChangeBatch(...)`. That failure demonstrates that the requested acknowledgement/committer contract did not already exist.

### Strengthened RED — terminal and synchronous failure behavior

Commit `b378c98c1ef50f26e7d5d321dc924ef854010b81` added bounded terminal-failure and synchronous-send-failure cases before implementation. CI run `31261230985`, including macOS job `93112224276`, failed at test compilation because all new batch-contract calls still targeted the missing production method. The existing production suite was not rewritten to manufacture a passing result.

### GREEN implementation

Commit `15153d038edb757189e56de6c748193a5d03242f` added the acknowledged batch handler, counters, bounded retry, interrupt propagation, and live Debezium `ChangeConsumer` wiring. Commit `254ea3fb241528392a8196db8b82f85ffea91f6d` added the Kafka producer controls. A subsequent test-compilation failure revealed only that one Mockito verification method needed to declare the checked `InterruptedException` from Debezium's `RecordCommitter`; commit `1ae9d757fd9f35859f51b1f3ce750d6b7bac443c` corrected that test signature without weakening an assertion.

On that implementation head, macOS CI job `93112811576` ran the full Maven reactor successfully. `CdcKafkaPublishAcknowledgementTest` ran **8 tests with 0 failures, 0 errors, and 0 skips**, and the complete `cdc-service` suite ran **114 tests with 0 failures, 0 errors, and 0 skips**. This is development evidence only; every later documentation commit invalidates it as exact-current-head merge evidence and must receive its own checks.

## Failure and recovery behavior

If Kafka does not acknowledge a destination-bearing change after both application attempts, the handler fails closed for source progress. Debezium may later replay source records according to its offset-storage and restart semantics. Operators should therefore investigate broker availability, topic authorization, producer configuration, and network reachability before restarting or repeatedly retrying the service.

`CDC_KAFKA_DELIVERY_TIMEOUT_MS` and `CDC_KAFKA_MAX_BLOCK_MS` are operational bounds, not success guarantees. Lowering them aggressively can increase terminal producer failures during transient pressure. Raising them increases the time a CDC worker can remain blocked before failure becomes visible.

## Exactly-once limitation

This change **does not claim end-to-end exactly-once delivery**.

Kafka producer idempotence protects producer retry behavior within Kafka's documented producer semantics. It does not make the external PostgreSQL source position, this process's file-backed Debezium offset store, Kafka acknowledgement, and every downstream consumer one distributed transaction. A process crash can occur after Kafka acknowledgement but before a durable Debezium offset flush, allowing the source event to be replayed after restart. Application-level retry after a terminal send failure can also encounter an ambiguous prior outcome.

Downstream consumers must therefore remain replay-tolerant and use stable event identity or idempotent application semantics where duplicate effects matter. A future transactional-outbox or externally coordinated delivery design would require its own failure model and evidence.

## Security and privacy properties

- No new credential or secret is introduced.
- No payload, record key, principal, or exception message is added to the operator status response.
- No protected branch, repository policy, or separately leased repository is modified by this slice.
- The Kafka producer remains configured through existing Spring Boot configuration and existing runtime credentials/network boundaries.
- Failed publication prevents source progress rather than converting an unavailable sink into a successful offset transition.

## Rollback

A rollback must remove the batch `RecordCommitter` implementation, producer configuration, status counters, tests, operations guidance, architecture description, and this evidence together only after an equivalent or stronger acknowledgement-before-source-progress mechanism is available. Do not roll back by restoring fire-and-forget Kafka publication while retaining documentation that claims acknowledgement-bound source progress.

## References

Apache Software Foundation. (2025). *Producer configs*. Apache Kafka 3.9 documentation. https://kafka.apache.org/39/configuration/producer-configs/

Debezium. (2026). *Debezium Engine*. Debezium documentation. https://debezium.io/documentation/reference/development/engine.html

Spring. (n.d.). *Sending messages*. Spring for Apache Kafka reference documentation. Retrieved August 8, 2026, from https://docs.spring.io/spring-kafka/reference/kafka/sending-messages.html
