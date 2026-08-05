# Durable-job retry and success-boundary doctoring evidence

## Decision

One durable worker claim represents exactly one persisted execution attempt. The worker must call
`EtlService.processDataInExistingTransaction` rather than the synchronous `processData` entry point.

The synchronous endpoint keeps `@Retryable` outside `@Transactional` so each request retry can own a
fresh transaction. The durable worker already owns bounded retries through `attempt_count`, claim
renewal by a later poll, and lease-fenced lifecycle transitions. Its target writes, response-ledger
write, and exact-live-lease success transition must remain inside one caller-owned transaction.

`EtlJobLeaseRepository.markSucceeded` therefore rejects direct invocation when no actual Spring
transaction is active. Retry and failure transitions remain independently persistable after an
execution exception, but success can never be published separately from the effects it certifies.

## Failure modes prevented

### Retry advice inside an existing durable transaction

Calling the synchronous `@Retryable` method from an already active durable execution transaction can
place retry advice inside an outer transaction that it did not create. After a transactional database
failure, another in-process invocation can reuse a rollback-only or otherwise failed transaction
instead of starting the fresh transaction assumed by Spring Retry. It also performs retries that are
not represented by the durable job's `attempt_count`.

### False success outside the atomic execution transaction

A public success-transition method that can run in autocommit mode allows accidental callers to mark
a job `SUCCEEDED` without the target rows and response ledger being committed in the same unit of
work. Even an exact lease predicate cannot prove those effects exist. Requiring an actual transaction
before the success SQL executes makes the repository fail closed at its public boundary.

The complete contract is:

```text
one database claim
→ one existing execution transaction
→ one non-retrying ETL invocation
→ target rows + response ledger + exact-live-lease success
→ one atomic commit

or

one escaped failure
→ worker-owned durable retry / terminal-failure decision
```

No in-process retry happens inside the lease transaction. A transient exception escapes to
`EtlJobWorker`, which either returns the exact live lease to `PENDING` while attempts remain or records
a stable terminal failure after the configured maximum.

## Test-first evidence

The regression contracts were added before their production behavior:

- `EtlJobIdempotencyRetryBoundaryTest` required durable execution to call
  `processDataInExistingTransaction` exactly once and never call `processData`;
- `EtlServiceIdempotencyTransactionBoundaryTest` required the durable ETL entry point to reject
  direct use without an actual transaction before JDBC or request-lock access;
- `EtlJobLeaseSuccessTransactionBoundaryTest` required `markSucceeded` to reject use without an
  actual transaction before JDBC access;
- `EtlJobLeaseRepositoryIntegrationTest` executes successful lease fencing inside a real Spring test
  transaction and still verifies expiry and supersession rejection.

Production then added the non-retrying, transaction-requiring ETL entry point, changed
`EtlJobIdempotencyService` to use it, and added the active-transaction guard to the public success
transition. Existing synchronous processing retains its retry behavior.

## Review and operational evidence

Reviewers should confirm all of the following on the exact current head:

1. `processDataInExistingTransaction` has neither `@Retryable` nor `@Transactional`;
2. it fails closed when no actual Spring transaction is active;
3. `EtlJobIdempotencyService` invokes only that entry point for a newly executed job;
4. response replay does not invoke ETL target writes;
5. `EtlJobLeaseRepository.markSucceeded` fails before JDBC without an actual transaction;
6. `EtlJobExecutionService.execute` owns the transaction containing ETL, ledger, and success;
7. transient exceptions escape to `EtlJobWorker` and affect durable attempt accounting once;
8. retry and terminal-failure transitions remain exact-lease fenced;
9. target rows, response ledger, and `SUCCEEDED` roll back together when success fencing fails;
10. statement and branch coverage gates remain at 100% for the configured production scope.

## Rollback

Disable the durable worker before reverting either boundary. Do not restore synchronous retry advice
inside the lease transaction, and do not allow `markSucceeded` to run in autocommit mode. A safe
replacement must preserve one persisted attempt per claim and prove that target effects, the response
ledger, and terminal success commit or roll back together.

## References — APA 7th

Spring Retry Authors. (2026). *EnableRetry.java* [Source code]. GitHub.
https://github.com/spring-projects/spring-retry/blob/main/src/main/java/org/springframework/retry/annotation/EnableRetry.java

Spring Retry Authors. (2026). *RetryOperationsInterceptor.java* [Source code]. GitHub.
https://github.com/spring-projects/spring-retry/blob/main/src/main/java/org/springframework/retry/interceptor/RetryOperationsInterceptor.java

Spring Framework Authors. (2026). *TransactionSynchronizationManager.java* [Source code]. GitHub.
https://github.com/spring-projects/spring-framework/blob/main/spring-tx/src/main/java/org/springframework/transaction/support/TransactionSynchronizationManager.java
