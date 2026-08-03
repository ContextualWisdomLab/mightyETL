# ETL batch admission and backpressure

## Purpose

`etl-service` accepts JSON arrays at `POST /api/etl/process`. The service protects the JVM and target database from unbounded request fan-out by applying admission control before it schedules any record work.

This mechanism is a resource-safety control. It does **not** provide atomic batch commits or exactly-once delivery; those guarantees require a separate transactional loading design.

## Processing contract

1. The request body must parse as a JSON array.
2. The array must contain no more than `max-batch-records` entries.
3. Every entry must be a JSON object with a non-blank string or integral `id`.
4. The complete array is validated before the first transformation task or database write is created.
5. Accepted records are submitted to the dedicated `etlExecutor`.
6. Results are returned in input order even when individual tasks finish out of order.

A rejected request performs no database writes.

## Bounded executor

The executor uses:

- a fixed worker count;
- a bounded `ArrayBlockingQueue`;
- named `mighty-etl-worker-*` threads;
- caller-runs backpressure when workers and the queue are saturated;
- explicit rejection after shutdown.

Caller-runs backpressure makes the submitting HTTP request thread execute work when the service is saturated. This intentionally slows producers instead of dropping tasks or growing an unbounded queue.

## Configuration

Preferred property keys:

| Property | Default | Supported range | Meaning |
|:---------|--------:|----------------:|:--------|
| `mightyetl.etl.max-batch-records` | `1000` | `1`–`100000` | Maximum records admitted in one request |
| `mightyetl.etl.max-concurrency` | `min(8, available processors)` | `1`–`64` | Dedicated transformation workers |
| `mightyetl.etl.queue-capacity` | `1024` | `1`–`100000` | Waiting tasks before caller-runs applies |

Compatibility keys under `xtrmetl.etl.*` remain supported. When both prefixes are set, `mightyetl.*` wins.

Short environment aliases are also accepted:

```bash
export ETL_MAX_BATCH_RECORDS=1000
export ETL_MAX_CONCURRENCY=8
export ETL_QUEUE_CAPACITY=1024
```

Invalid limits fail service initialization before worker threads are created.

## Capacity guidance

- Set the worker count no higher than the database connection capacity available to this service after reserving connections for health checks and administrative traffic.
- Keep the queue finite. A larger queue absorbs bursts but increases request latency and memory retention.
- Set the request limit based on payload size as well as record count; an HTTP body-size limit remains a separate ingress requirement.
- Monitor request latency, queue saturation, database pool wait time, and rejected oversized requests before increasing limits.

## Known boundary

The current loader writes accepted records independently. A later record failure can therefore leave earlier records committed, and retrying the whole method can duplicate earlier writes unless the target schema or a future idempotency layer prevents it. Production workflows needing atomicity should wait for the transactional batch-loading milestone or use an idempotent target design.
