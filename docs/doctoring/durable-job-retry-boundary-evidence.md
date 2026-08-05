# Durable-job retry-boundary doctoring evidence

## Decision

One durable worker claim represents exactly one persisted execution attempt. The worker must call
`EtlService.processDataInExistingTransaction` rather than the synchronous `processData` entry point.

The synchronous endpoint keeps `@Retryable` outside `@Transactional` so each request retry can own a
fresh transaction. The durable worker already owns bounded retries through `attempt_count`, claim
renewal by a later poll, and lease-fenced lifecycle transitions. Its target writes, response-ledger
write, and exact-live-lease success transition must remain inside one caller-owned transaction.

## Failure mode prevented

Calling the synchronous `@Retryable` method from an already active durable execution transaction can
place retry advice inside an outer transaction that it did not create. After a transactional database
failure, another in-process invocation can reuse a rollback-only or otherwise failed transaction
instead of starting the fresh transaction assumed by Spring Retry. It also performs retries that are
not represented by the durable job's `attempt_count`.

The fail-closed contract is therefore:

```text
one database claim
→ one existing execution transaction
→ one ETL invocation
→ one success transition or one escaped failure
→ worker-owned durable retry decision
```

No in-process retry happens inside the lease transaction. A transient exception escapes to
`EtlJobWorker`, which either returns the exact live lease to `PENDING` while attempts remain or records
a stable terminal failure after the configured maximum.

## Test-first evidence

The regression contract was added before the production entry point existed:

- `EtlJobIdempotencyRetryBoundaryTest` required durable execution to call
  `processDataInExistingTransaction` exactly once and never call `processData`;
- `EtlServiceIdempotencyTransactionBoundaryTest` required the new entry point to reject direct use
  without an actual transaction before JDBC or request-lock access.

Production then added the non-retrying, transaction-requiring entry point and changed
`EtlJobIdempotencyService` to use it. Existing synchronous processing retains its retry behavior.

## Review and operational evidence

Reviewers should confirm all of the following on the exact current head:

1. `processDataInExistingTransaction` has neither `@Retryable` nor `@Transactional`;
2. it fails closed when no actual Spring transaction is active;
3. `EtlJobIdempotencyService` invokes only that entry point for a newly executed job;
4. response replay does not invoke ETL target writes;
5. transient exceptions escape to `EtlJobWorker` and affect durable attempt accounting once;
6. target rows, response ledger, and `SUCCEEDED` remain atomic with the lease-fenced transition;
7. statement and branch coverage gates remain at 100% for the configured production scope.

## Rollback

If this change must be reverted, disable the durable worker first. Do not revert to calling the
synchronous retryable entry point while the worker is enabled. A safe replacement must preserve one
persisted attempt per claim and prove fresh-transaction semantics independently before deployment.

## References — APA 7th

Spring Retry Authors. (2026). *EnableRetry.java* [Source code]. GitHub.
https://github.com/spring-projects/spring-retry/blob/main/src/main/java/org/springframework/retry/annotation/EnableRetry.java

Spring Retry Authors. (2026). *RetryOperationsInterceptor.java* [Source code]. GitHub.
https://github.com/spring-projects/spring-retry/blob/main/src/main/java/org/springframework/retry/interceptor/RetryOperationsInterceptor.java
