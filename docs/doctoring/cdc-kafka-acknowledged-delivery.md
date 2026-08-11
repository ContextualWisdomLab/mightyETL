# PostgreSQL-to-Kafka acknowledged-delivery evidence

Reviewed on: **2026-08-08**

## Buyer-visible reliability gap

The embedded PostgreSQL CDC path previously registered a one-record Debezium consumer and returned immediately after `KafkaTemplate.send(...)`. Spring Kafka returns a `CompletableFuture<SendResult<...>>` from the send operation, so returning from the handler proved only that a send was submitted to the producer API; the repository had no executable contract that Kafka had acknowledged that record before Debezium was allowed to advance processing state.

For an ETL/CDC product, that boundary is commercially material. A source position that advances independently of target acknowledgement can turn an ordinary broker outage or producer failure into an avoidable delivery ambiguity. The repair must therefore bind the source-processing decision to observable target completion without claiming a stronger end-to-end exactly-once guarantee than the architecture actually provides.

A second reliability boundary became visible after acknowledgement waiting was implemented: the application originally used an unbounded `CompletableFuture.get()`. Kafka producer delivery and blocking limits are important lower-layer controls, but a provider defect or future that never reaches a terminal state could still leave the application-level Debezium batch waiting indefinitely. The application now enforces its own finite acknowledgement wait for every publication attempt.

## Root-cause analysis

The original defect was a mismatch between two asynchronous contracts:

1. Spring Kafka publishes through a future-returning `KafkaTemplate.send(...)` API. The caller must observe future completion when downstream acknowledgement is part of the caller's correctness condition.
2. Debezium Engine exposes the advanced `ChangeConsumer` / `RecordCommitter` contract so an embedded consumer can mark individual records processed and mark a batch finished only after application processing has completed.

The old path used neither completion signal together. It submitted the Kafka operation from a simple consumer and then returned. Retrying the whole workflow, increasing broker timeouts, or merely setting stronger producer acknowledgement options would not repair that application-level ordering defect by themselves.

The follow-up timeout defect was at the application/future boundary: `awaitAcknowledgement(...)` called the no-timeout `CompletableFuture.get()`. Producer `delivery.timeout.ms` bounds Kafka's delivery reporting after a record has been accepted by the producer and `max.block.ms` bounds specific blocking producer operations, but neither substitutes for an explicit bound on this application's future wait. The smallest direct repair is therefore a timed future wait that remains interruptible and feeds the existing retry/fail-closed path.

## Feasibility analysis

The following remediation classes were evaluated against the current repository boundary.

### Executable now

Use Debezium's batch `ChangeConsumer` contract, await each Spring Kafka send future, and call `RecordCommitter.markProcessed(...)` only after successful completion. Mark the batch finished only after every record has reached its permitted terminal state. Keep the behavior in the existing `cdc-service` so no new service, credential, database, or separately leased repository is required.

Bound each application-level future wait to 65 seconds. This deliberately exceeds the repository's default Kafka `delivery.timeout.ms` of 60 seconds so normal producer terminal completion can surface first, while still giving the application a finite fail-closed boundary if a future itself never completes. A timeout is treated as a failed publication attempt and therefore follows the existing one-retry policy. No new secret, provider, endpoint, or cross-repository authority is needed.

These are the selected remediations because they address the causes directly and can be tested deterministically with controlled `CompletableFuture` completion and timeout behavior.

### Complementary producer controls

Require `acks=all`, explicit producer idempotence, `max.in.flight.requests.per.connection=5`, a 60-second default `delivery.timeout.ms`, and a 30-second default `max.block.ms`. These controls strengthen Kafka producer delivery behavior and bound lower-layer producer stalls, but they do not replace either the application-level acknowledgement-before-offset contract or the application's finite future-wait boundary.

### Not selected as this bounded slice

Migrating the product to Kafka Connect distributed mode, adding a transactional outbox, introducing an external offset/acknowledgement ledger, or adding a new timeout configuration surface could provide different durability or tuning properties, but each is a materially larger architecture/configuration change. None is necessary to correct these specific embedded-engine ordering and unbounded-wait defects, and none should be introduced merely to make this pull request appear stronger.

## Implemented delivery state machine

For each Debezium event in a batch:

```text
receive ordered change event
→ if destination exists, run the optional canonical mapping observation
→ submit raw Debezium JSON to Kafka
→ wait interruptibly for acknowledgement for at most 65 seconds
→ on acknowledgement: increment kafkaPublishSuccess
→ mark this Debezium record processed
→ continue to the next event
→ after every record is processed: mark the Debezium batch finished
```

A destination-less engine event is treated as non-publishable metadata and is marked processed without touching Kafka or the publish counters.

A failed or timed-out Kafka attempt increments `kafkaPublishFailure` and receives one bounded application-level retry. A second terminal failure or timeout raises `KafkaException`; the current record is not marked processed and the batch is not marked finished. A synchronous exception raised while initiating the send follows the same bounded retry policy. An `InterruptedException` while awaiting acknowledgement propagates immediately and does not advance the current record or batch.

The live Debezium engine is wired to this batch handler. The older one-record `handleChangeEvent(...)` method remains as a compatibility surface for direct callers and tests, but it is no longer the live source-offset progression path.

## Producer durability controls

`cdc-service/src/main/resources/application.yml` now requires:

- `acks: all`;
- `enable.idempotence=true`;
- `max.in.flight.requests.per.connection=5`;
- `delivery.timeout.ms=${CDC_KAFKA_DELIVERY_TIMEOUT_MS:60000}`;
- `max.block.ms=${CDC_KAFKA_MAX_BLOCK_MS:30000}`.

Apache Kafka documents that producer idempotence requires `acks=all`, retries greater than zero, and no more than five in-flight requests per connection. Kafka also defines `delivery.timeout.ms` as the upper bound for reporting success or failure after a record is accepted by the producer. The application leaves Kafka's retry machinery intact, adds one bounded retry after a terminal send-future failure, and independently caps each future acknowledgement wait at 65 seconds.

The 65-second application wait is not a claim that a complete attempt can consume only 65 seconds: initiating `KafkaTemplate.send(...)` can itself be subject to producer blocking behavior such as `max.block.ms`. The important invariant is narrower and testable: after a send future has been returned, this application will not wait on that future forever.

## Operator evidence

`GET /api/cdc/status` now includes cumulative process-lifetime counters:

- `kafkaPublishSuccess`: records whose Kafka send completed successfully through the acknowledged live path;
- `kafkaPublishFailure`: failed application send attempts, including timeout failures and failures that were subsequently recovered by the one bounded retry.

These counters intentionally contain no payload, source key, principal, topic contents, credential, or exception text. They are diagnostic evidence, not a durable billing or audit ledger.

## TDD evidence

### RED — missing acknowledgement boundary

Commit `f7ca5f149df959d03ff330ea0e374bc8fcb031e4` introduced the initial contract tests before production implementation. CI run `31259345232` failed because `CdcService` did not provide `handleChangeBatch(...)`. That failure demonstrates that the requested acknowledgement/committer contract did not already exist.

### Strengthened RED — terminal and synchronous failure behavior

Commit `b378c98c1ef50f26e7d5d321dc924ef854010b81` added bounded terminal-failure and synchronous-send-failure cases before implementation. CI run `31261230985`, including macOS job `93112224276`, failed at test compilation because all new batch-contract calls still targeted the missing production method. The existing production suite was not rewritten to manufacture a passing result.

### GREEN implementation

Commit `15153d038edb757189e56de6c748193a5d03242f` added the acknowledged batch handler, counters, bounded retry, interrupt propagation, and live Debezium `ChangeConsumer` wiring. Commit `254ea3fb241528392a8196db8b82f85ffea91f6d` added the Kafka producer controls. A subsequent test-compilation failure revealed only that one Mockito verification method needed to declare the checked `InterruptedException` from Debezium's `RecordCommitter`; commit `1ae9d757fd9f35859f51b1f3ce750d6b7bac443c` corrected that test signature without weakening an assertion.

On that implementation head, macOS CI job `93112811576` ran the full Maven reactor successfully. `CdcKafkaPublishAcknowledgementTest` ran **8 tests with 0 failures, 0 errors, and 0 skips**, and the complete `cdc-service` suite ran **114 tests with 0 failures, 0 errors, and 0 skips**. This is development evidence only; every later documentation commit invalidates it as exact-current-head merge evidence and must receive its own checks.

### RED — unbounded future wait

Commit `85e9f8a3369e39634b57295c24f52c9e89bf5917` added `CdcKafkaPublishTimeoutTest` before changing production. PR-triggered CI run `31276261918`, Ubuntu job `93150176837`, compiled the production CDC service and all tests successfully and then ran the new boundary test. The CDC module ran **116 tests with exactly 1 failure and 0 errors**: `timesOutHungKafkaAcknowledgementsWithoutAdvancingOffsets` expected the two mocked futures' timed `get(65000, MILLISECONDS)` calls to fail closed, but production still used the unbounded no-argument `get()` and therefore returned without throwing. Existing `CdcKafkaPublishAcknowledgementTest` remained **8/8 green**. The failure reached the intended production/future boundary and was not a fixture, import, compilation, or environment defect.

### GREEN — finite future wait

Commit `c56dce4586ca330e7b199a2651010eb50283b5e5` changed only the production acknowledgement boundary: each returned send future is now awaited interruptibly for at most 65 seconds; `TimeoutException` is wrapped into the existing `ExecutionException` failure path so retry counters, terminal `KafkaException`, and offset fail-closed behavior remain unchanged. On PR-triggered CI run `31276397082`, Ubuntu job `93150509726` ran the full Maven reactor successfully. `CdcKafkaPublishTimeoutTest` passed **1/1**, `CdcKafkaPublishAcknowledgementTest` passed **8/8**, and the complete CDC module passed **116 tests with 0 failures, 0 errors, and 0 skips**. Because the protected `develop` workflow still checks out a synthetic PR merge, this is valid development/TDD proof but is not being misrepresented as literal-head merge evidence.

## Failure and recovery behavior

If Kafka does not acknowledge a destination-bearing change after both application attempts, including a future that fails to complete within 65 seconds on an attempt, the handler fails closed for source progress. Debezium may later replay source records according to its offset-storage and restart semantics. Operators should therefore investigate broker availability, topic authorization, producer configuration, network reachability, and future/provider health before restarting or repeatedly retrying the service.

`CDC_KAFKA_DELIVERY_TIMEOUT_MS` and `CDC_KAFKA_MAX_BLOCK_MS` are lower-layer operational bounds, not success guarantees. Lowering them aggressively can increase terminal producer failures during transient pressure. Raising them increases lower-layer wait time. Independently, the application will wait at most 65 seconds on each returned send future before treating that attempt as failed; this prevents a non-terminating future from stalling the CDC batch indefinitely.

## Exactly-once limitation

This change **does not claim end-to-end exactly-once delivery**.

Kafka producer idempotence protects producer retry behavior within Kafka's documented producer semantics. It does not make the external PostgreSQL source position, this process's file-backed Debezium offset store, Kafka acknowledgement, and every downstream consumer one distributed transaction. A process crash can occur after Kafka acknowledgement but before a durable Debezium offset flush, allowing the source event to be replayed after restart. Application-level retry after a terminal send failure or timeout can also encounter an ambiguous prior outcome.

Downstream consumers must therefore remain replay-tolerant and use stable event identity or idempotent application semantics where duplicate effects matter. A future transactional-outbox or externally coordinated delivery design would require its own failure model and evidence.

## Security and privacy properties

- No new credential or secret is introduced.
- No payload, record key, principal, or exception message is added to the operator status response.
- No protected branch, repository policy, or separately leased repository is modified by this slice.
- The Kafka producer remains configured through existing Spring Boot configuration and existing runtime credentials/network boundaries.
- Failed or timed-out publication prevents source progress rather than converting an unavailable sink into a successful offset transition.

## Rollback

A rollback must remove the batch `RecordCommitter` implementation, producer configuration, finite application acknowledgement wait, status counters, tests, operations guidance, architecture description, and this evidence together only after an equivalent or stronger acknowledgement-before-source-progress mechanism is available. Do not roll back by restoring fire-and-forget or unbounded Kafka publication while retaining documentation that claims acknowledgement-bound, bounded source progress.

## References

Apache Software Foundation. (2025). *Producer configs*. Apache Kafka 3.9 documentation. https://kafka.apache.org/39/configuration/producer-configs/

Debezium. (2026). *Debezium Engine*. Debezium documentation. https://debezium.io/documentation/reference/development/engine.html

Spring. (n.d.). *Sending messages*. Spring for Apache Kafka reference documentation. Retrieved August 8, 2026, from https://docs.spring.io/spring-kafka/reference/kafka/sending-messages.html
