# mightyETL System Architecture

## System Architecture Overview

This document provides a comprehensive view of the mightyETL platform architecture,
component interactions, and data flow.

> Product name: **mightyETL** (formerly xtrmETL). Runtime packages remain `com.xtrmetl.*` for compatibility — see [docs/rebrand-name-matrix.md](docs/rebrand-name-matrix.md).

## 1. High-Level Architecture

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                            External Clients                              │
│                     (Web Apps, CLI Tools, Services)                      │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │ HTTPS/HTTP
                                 │ JWT Token
                                 v
┌─────────────────────────────────────────────────────────────────────────┐
│                         Zuul API Gateway (8080)                          │
│  ┌───────────────────┐  ┌──────────────────┐  ┌───────────────────┐   │
│  │ Authentication    │  │  Request Routing │  │ Load Balancing    │   │
│  │ Filter (JWT)      │  │  /etl/** /cdc/** │  │                   │   │
│  └───────────────────┘  └──────────────────┘  └───────────────────┘   │
└──────────┬────────────────────────────────────────────┬────────────────┘
           │                                            │
           v                                            v
┌──────────────────────────┐              ┌──────────────────────────────┐
│   ETL Service (8000)     │              │   CDC Service (8001)         │
│  ┌────────────────────┐  │              │  ┌────────────────────────┐ │
│  │ EtlController      │  │              │  │ CdcController          │ │
│  │ /api/etl/process   │  │              │  │ /api/cdc/start | stop    │ │
│  └──────────┬─────────┘  │              │  └────────┬───────────────┘ │
│             v             │              │            v                 │
│  ┌────────────────────┐  │              │  ┌────────────────────────┐ │
│  │ EtlService         │  │              │  │ CdcService (Debezium)  │ │
│  │ - Extract          │  │              │  │ - PostgreSQL Connector │ │
│  │ - Transform        │  │              │  │ - Change Detection     │ │
│  │ - Load             │  │              │  │ - Event Publishing     │ │
│  │ - Parallel Proc    │  │              │  └────────┬───────────────┘ │
│  └──────────┬─────────┘  │              │            │                 │
│             v             │              │            v                 │
│  ┌────────────────────┐  │              │  ┌────────────────────────┐ │
│  │ JDBC Template      │  │              │  │ KafkaTemplate          │ │
│  └──────────┬─────────┘  │              │  └────────┬───────────────┘ │
└─────────────┼────────────┘              └────────────┼─────────────────┘
              │                                        │
              v                                        v
┌──────────────────────────┐              ┌──────────────────────────────┐
│  PostgreSQL (Target DB)  │              │   Apache Kafka               │
│  ┌────────────────────┐  │              │  ┌────────────────────────┐ │
│  │ processed_data     │  │              │  │ Topics:                │ │
│  │ users              │  │              │  │ - xtrmetl-cdc.*.table  │ │
│  │ roles              │  │              │  │                        │ │
│  │ user_roles         │  │              │  └────────────────────────┘ │
│  └────────────────────┘  │              └──────────────────────────────┘
└──────────────────────────┘                           │
                                                       v
                                          ┌──────────────────────────────┐
                                          │   Downstream Consumers       │
                                          │   (Other Services, Analytics)│
                                          └──────────────────────────────┘

┌──────────────────────────┐              ┌──────────────────────────────┐
│ PostgreSQL (Source DB)   │──Monitor────▶│   CDC Service               │
│ (Monitored for Changes)  │   WAL        │   (via Debezium)            │
└──────────────────────────┘              └──────────────────────────────┘

                    Infrastructure Services
┌─────────────────────────────────────────────────────────────────────────┐
│  ┌──────────────────────┐           ┌──────────────────────────┐       │
│  │ Eureka Server (8761) │           │ Config Server (8888)     │       │
│  │ Service Discovery    │           │ Configuration Management │       │
│  └──────────────────────┘           └──────────────────────────┘       │
│                                                                          │
│  ┌──────────────────────┐                                               │
│  │ Zipkin (9412)        │                                               │
│  │ Distributed Tracing  │                                               │
│  └──────────────────────┘                                               │
└─────────────────────────────────────────────────────────────────────────┘
```

## 2. Service Communication Patterns

### 2.1 Synchronous Communication (REST)

```text
Client → Zuul Gateway → Microservice
          (HTTP/REST)     (HTTP/REST)
```

**Flow**:

1. Client sends HTTP request with JWT token
2. Zuul validates token via JWT filter
3. Zuul routes request to appropriate service (Eureka lookup)
4. Service processes request and returns response
5. Response flows back through Zuul to client

### 2.2 Asynchronous Communication (Kafka)

```text
Source DB → CDC Service → Kafka → Consumer Services
            (Debezium)    (Event Stream)
```

**Flow**:

1. Database change occurs (INSERT/UPDATE/DELETE)
2. Debezium captures change from WAL
3. CDC Service submits the raw change event to Kafka
4. CDC Service waits for the Kafka send future to complete successfully
5. Only after acknowledgement, Debezium `RecordCommitter` marks that source record processed
6. The Debezium batch is marked finished only after every record in the batch is processed
7. Consumer services process events at their own pace

The source-to-Kafka boundary is intentionally fail-closed for source progress: two terminal application send failures leave the current Debezium record unprocessed and fail the batch. Kafka producer idempotence and `acks=all` strengthen producer retry semantics, but they do not turn PostgreSQL, the file-backed Debezium offset store, Kafka, and downstream consumers into one exactly-once transaction. A crash after Kafka acknowledgement but before durable source-offset flush can replay an event; downstream consumers must remain replay-tolerant. See `docs/doctoring/cdc-kafka-acknowledged-delivery.md`.

## 3. Data Flow Diagrams

### 3.1 ETL Processing Flow

```text
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ 1. POST /api/etl/process
       │    + JWT Token
       │    + JSON Array
       v
┌─────────────────────────┐
│   Zuul Gateway          │
│   - Validate JWT        │
│   - Route to ETL        │
└──────┬──────────────────┘
       │ 2. Forward request
       v
┌─────────────────────────┐
│   ETL Controller        │
│   - Accept JSON         │
│   - Validate format     │
└──────┬──────────────────┘
       │ 3. Process data
       v
┌─────────────────────────┐
│   ETL Service           │
│   ┌─────────────────┐   │
│   │ For each record │   │
│   │  - Extract      │───┼─┐
│   │  - Transform    │   │ │ 4. Parallel
│   │  - Load         │◀──┼─┘    Processing
│   └─────────────────┘   │      (CompletableFuture)
└──────┬──────────────────┘
       │ 5. INSERT INTO processed_data
       v
┌─────────────────────────┐
│   PostgreSQL            │
│   - Store transformed   │
│     data                │
└──────┬──────────────────┘
       │ 6. Return results
       v
┌─────────────┐
│   Client    │
│   (Success) │
└─────────────┘
```

### 3.2 CDC Event Capture Flow

```text
┌─────────────────────────┐
│  Source Application     │
└──────┬──────────────────┘
       │ 1. UPDATE users SET name='Jane'
       v
┌─────────────────────────┐
│  PostgreSQL (Source)    │
│  - Write to WAL         │
│  - Logical Replication  │
└──────┬──────────────────┘
       │ 2. WAL Stream
       v
┌─────────────────────────┐
│  CDC Service            │
│  ┌──────────────────┐   │
│  │ Debezium Engine  │   │
│  │ - Read WAL       │   │
│  │ - Parse changes  │   │
│  │ - Create events  │   │
│  └────────┬─────────┘   │
│           v             │
│  ┌──────────────────┐   │
│  │ KafkaTemplate    │   │
│  │ - Serialize      │   │
│  │ - Send to topic  │   │
│  └────────┬─────────┘   │
└───────────┼─────────────┘
            │ 3. Publish event
            v
┌─────────────────────────┐
│  Apache Kafka           │
│  Topic:                 │
│  xtrmetl-cdc.public.    │
│  users                  │
└──────┬──────────────────┘
       │ 4. Consume events
       v
┌─────────────────────────┐
│  Consumer Applications  │
│  - Analytics            │
│  - Data Warehouse       │
│  - Cache Updates        │
│  - Audit Logs           │
└─────────────────────────┘
```

The diagram shows the transport topology; the source-progress control loop is stricter than a fire-and-forget arrow. `CdcService` awaits Kafka completion before `RecordCommitter.markProcessed(...)`, and calls `markBatchFinished()` only after the complete batch succeeds. A repeated terminal send failure stops source progress for the affected record. The operator status surface exposes `kafkaPublishSuccess` and `kafkaPublishFailure` attempt counters without payload or credential material.

### 3.3 Authentication Flow

```text
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ 1. POST /auth/signin
       │    {username, password}
       v
┌─────────────────────────┐
│   Zuul Gateway          │
│   (No auth required     │
│    for /auth/**)        │
└──────┬──────────────────┘
       │ 2. Forward
       v
┌─────────────────────────┐
│   ETL Service           │
│   AuthController        │
│   ┌─────────────────┐   │
│   │ 1. Load user    │───┼──┐
│   │ 2. Verify pwd   │   │  │ 3. Query DB
│   │ 3. Generate JWT │◀──┼──┘
│   └─────────────────┘   │
└──────┬──────────────────┘
       │ 4. Return JWT
       v
┌─────────────┐
│   Client    │
│   Store JWT │
└──────┬──────┘
       │ 5. Subsequent requests
       │    Authorization: Bearer <JWT>
       v
┌─────────────────────────┐
│   Zuul Gateway          │
│   JwtAuthenticationFilter│
│   - Extract token       │
│   - Validate signature  │
│   - Check expiration    │
│   - Set SecurityContext │
└──────┬──────────────────┘
       │ 6. Forward (if valid)
       v
┌─────────────────────────┐
│   Protected Service     │
│   (ETL/CDC)             │
└─────────────────────────┘
```

## 4. Service Discovery & Registration

```text
┌────────────────────────────────────────────────────────────┐
│                    Eureka Server (8761)                     │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Service Registry                         │  │
│  │  ┌────────────────┐  ┌────────────────┐             │  │
│  │  │ etl-service    │  │ cdc-service    │             │  │
│  │  │ - 8000         │  │ - 8001         │             │  │
│  │  │ - Status: UP   │  │ - Status: UP   │             │  │
│  │  └────────────────┘  └────────────────┘             │  │
│  │  ┌────────────────┐                                  │  │
│  │  │ zuul-gateway   │                                  │  │
│  │  │ - 8080         │                                  │  │
│  │  │ - Status: UP   │                                  │  │
│  │  └────────────────┘                                  │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
                      ▲         ▲         ▲
                      │         │         │
        ┌─────────────┘         │         └────────────┐
        │ Register              │                      │
        │ (on startup)          │ Heartbeat           │ Lookup
        │                       │ (every 30s)         │ (on request)
        │                       │                     │
┌───────┴────────┐    ┌─────────┴────────┐   ┌───────┴────────┐
│  ETL Service   │    │   CDC Service    │   │  Zuul Gateway  │
└────────────────┘    └──────────────────┘   └────────────────┘
```

**Registration Process**:

1. Service starts up
2. Registers with Eureka (via `@EnableDiscoveryClient`)
3. Sends heartbeat every 30 seconds
4. Eureka marks service as UP
5. Other services discover via Eureka lookup

## 5. Security Architecture

### 5.1 Authentication & Authorization Layer

```text
┌─────────────────────────────────────────────────────────────┐
│                      Security Layer                          │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │          JWT Token Structure                            │ │
│  │  ┌──────────┬──────────────────┬───────────────────┐   │ │
│  │  │ Header   │    Payload        │   Signature       │   │ │
│  │  │ HS512    │ {sub: "user",     │   HMACSHA512      │   │ │
│  │  │          │  iat: 1234567890, │   (JWT_SECRET)    │   │ │
│  │  │          │  exp: 1234571490} │                   │   │ │
│  │  └──────────┴──────────────────┴───────────────────┘   │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │          Security Filter Chain                          │ │
│  │                                                          │ │
│  │  1. JwtAuthenticationFilter                             │ │
│  │     - Extract token from Authorization header           │ │
│  │     - Validate signature                                │ │
│  │     - Check expiration                                  │ │
│  │     - Load user details                                 │ │
│  │     - Set authentication in SecurityContext             │ │
│  │                                                          │ │
│  │  2. Method Security (@PreAuthorize)                     │ │
│  │     - Check user roles                                  │ │
│  │     - hasRole('USER'), hasRole('ADMIN')                 │ │
│  │                                                          │ │
│  │  3. CSRF Protection (Disabled for REST API)             │ │
│  │                                                          │ │
│  │  4. Session Management (Stateless)                      │ │
│  │     - No server-side sessions                           │ │
│  │     - JWT contains all necessary info                   │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 Database Security Schema

```text
┌──────────────────────────────────────┐
│           users                       │
├──────────────────────────────────────┤
│ id (PK)                               │
│ username (UNIQUE)                     │
│ password (BCrypt hashed)              │
└───────┬──────────────────────────────┘
        │
        │ Many-to-Many
        │
        ▼
┌──────────────────────────────────────┐
│        user_roles                     │
├──────────────────────────────────────┤
│ user_id (FK)                          │
│ role_id (FK)                          │
└────┬──────────────────────┬──────────┘
     │                      │
     │                      │
     ▼                      ▼
┌──────────────────────────────────────┐
│           roles                       │
├──────────────────────────────────────┤
│ id (PK)                               │
│ name (ENUM)                           │
│   - ROLE_USER                         │
│   - ROLE_ADMIN                        │
└──────────────────────────────────────┘
```

## 6. Monitoring & Observability

### 6.1 Distributed Tracing

```text
Request Flow with Trace IDs:

Client Request
   │ TraceId: a1b2c3d4
   │ SpanId: span-1
   ▼
┌─────────────────────┐
│  Zuul Gateway       │ TraceId: a1b2c3d4
│  SpanId: span-2     │ ParentSpan: span-1
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  ETL Service        │ TraceId: a1b2c3d4
│  SpanId: span-3     │ ParentSpan: span-2
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  PostgreSQL         │ TraceId: a1b2c3d4
│  SpanId: span-4     │ ParentSpan: span-3
└─────────────────────┘
           │
           │ All spans sent to Zipkin
           ▼
┌─────────────────────┐
│  Zipkin Server      │
│  - Collect spans    │
│  - Visualize trace  │
│  - Analyze latency  │
└─────────────────────┘
```

### 6.2 Observability Stack

```text
┌──────────────────────────────────────────────────────────┐
│                    Observability Layer                    │
├──────────────────────────────────────────────────────────┤
│                                                           │
│  ┌────────────────┐  ┌────────────────┐  ┌───────────┐  │
│  │   Logging      │  │   Metrics      │  │  Tracing  │  │
│  │                │  │                │  │           │  │
│  │ - SLF4J        │  │ - Micrometer   │  │ - Sleuth  │  │
│  │ - Logback      │  │ - Custom       │  │ - Zipkin  │  │
│  │ - JSON format  │  │   counters     │  │           │  │
│  │ - Trace IDs    │  │ - Timers       │  │           │  │
│  └────────────────┘  └────────────────┘  └───────────┘  │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │            Instrumentation Points                    │ │
│  │                                                       │ │
│  │  • Controller methods (@Observed)                    │ │
│  │  • Service methods (@Retryable)                      │ │
│  │  • Database operations (JDBC)                        │ │
│  │  • Kafka publishing                                  │ │
│  │  • HTTP requests (Gateway)                           │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

## 7. Deployment Architecture

### 7.1 Single-Node Deployment (Development)

```text
┌─────────────────────────────────────────────────────────┐
│               Single Host / VM                           │
│                                                          │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐        │
│  │ Eureka     │  │ Zuul       │  │ ETL        │        │
│  │ :8761      │  │ :8080      │  │ :8000      │        │
│  └────────────┘  └────────────┘  └────────────┘        │
│                                                          │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐        │
│  │ CDC        │  │ PostgreSQL │  │ Kafka      │        │
│  │ :8001      │  │ :5432      │  │ :9092      │        │
│  └────────────┘  └────────────┘  └────────────┘        │
│                                                          │
│  ┌────────────┐                                         │
│  │ Zipkin     │                                         │
│  │ :9412      │                                         │
│  └────────────┘                                         │
└─────────────────────────────────────────────────────────┘
```

### 7.2 Multi-Node Deployment (Production)

```text
┌──────────────────────┐  ┌──────────────────────┐
│   Load Balancer      │  │   Service Mesh       │
│   (nginx/HAProxy)    │  │   (Optional)         │
└──────────┬───────────┘  └──────────────────────┘
           │
    ┌──────┴──────┐
    │             │
    ▼             ▼
┌─────────┐   ┌─────────┐
│ Zuul-1  │   │ Zuul-2  │
│ :8080   │   │ :8080   │
└────┬────┘   └────┬────┘
     │             │
     └──────┬──────┘
            │
    ┌───────┼───────┐
    │       │       │
    ▼       ▼       ▼
┌────────┐┌────────┐┌────────┐
│ ETL-1  ││ ETL-2  ││ CDC-1  │
│ :8000  ││ :8000  ││ :8001  │
└────────┘└────────┘└────────┘
    │       │       │
    └───────┼───────┘
            │
            ▼
┌──────────────────────┐
│  PostgreSQL Cluster  │
│  (Primary + Replica) │
└──────────────────────┘

┌──────────────────────┐  ┌──────────────────────┐
│   Kafka Cluster      │  │   Eureka Cluster     │
│   (3+ brokers)       │  │   (2+ instances)     │
└──────────────────────┘  └──────────────────────┘
```

## 8. Technology Integration Points

### 8.1 Debezium Architecture

```text
┌──────────────────────────────────────────────────────────┐
│                  Debezium Embedded Engine                 │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  PostgreSQL Connector                                │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────┐  │ │
│  │  │ Snapshot     │  │ Streaming    │  │ Schema   │  │ │
│  │  │ Reader       │  │ Reader       │  │ History  │  │ │
│  │  └──────────────┘  └──────────────┘  └──────────┘  │ │
│  └─────────────────────────────────────────────────────┘ │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │  Change Event Pipeline                               │ │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │ │
│  │  │ Parse    │→ │ Filter   │→ │ Transform        │  │ │
│  │  │ WAL      │  │ Tables   │  │ to SourceRecord  │  │ │
│  │  └──────────┘  └──────────┘  └──────────────────┘  │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

#### 8.1.1 Acknowledgement and source-offset boundary

The embedded engine is wired to Debezium's batch `ChangeConsumer` rather than using the one-record callback as the live progress path. For a destination-bearing record, `CdcService` sends to Kafka, waits for the `CompletableFuture<SendResult<...>>`, and only then invokes `RecordCommitter.markProcessed(...)`. The batch is finished only after all records are processed. Two terminal application attempts are permitted; repeated failure aborts the batch without marking the current record. Producer-side `acks=all`, explicit idempotence, and `max.in.flight.requests.per.connection=5` complement this ordering boundary.

This is an at-least-once/replay-tolerant design, not a distributed exactly-once transaction. Debezium source offsets are stored separately from Kafka acknowledgement, so a crash window can replay an already acknowledged event. The full failure model and operator controls are documented in `docs/doctoring/cdc-kafka-acknowledged-delivery.md` and `docs/cdc/ops-and-reliability.md`.

### 8.2 Spring Retry Mechanism

```text
ETL Processing with Retry:

┌────────────────────────────────────────┐
│  @Retryable(                           │
│    maxAttempts = 3,                    │
│    backoff = @Backoff(delay = 1000)    │
│  )                                     │
│  public String processData(String data)│
└─────────────────┬──────────────────────┘
                  │
        ┌─────────┴─────────┐
        │  Attempt 1        │
        │  (immediate)      │
        └────┬──────────┬───┘
             │          │
          Success    Failure
             │          │
             ▼          ▼
         Return    ┌─────────────────┐
         Result    │  Wait 1 second  │
                   └────┬────────────┘
                        │
                   ┌────┴──────┐
                   │ Attempt 2 │
                   └────┬──┬───┘
                        │  │
                    Success Failure
                        │  │
                        ▼  ▼
                    Return ┌──────────────────┐
                    Result │  Wait 1 second   │
                           └────┬─────────────┘
                                │
                           ┌────┴──────┐
                           │ Attempt 3 │
                           │ (final)   │
                           └────┬──┬───┘
                                │  │
                            Success Failure
                                │  │
                                ▼  ▼
                            Return Throw
                            Result Exception
```

## 9. Network & Port Configuration

| Service | Port | Protocol | Access Level |
| --------- | ------ | ---------- | -------------- |
| Zuul Gateway | 8080 | HTTP | Public |
| ETL Service | 8000 | HTTP | Internal |
| CDC Service | 8001 | HTTP | Internal |
| Eureka Server | 8761 | HTTP | Internal |
| Config Server | 8888 | HTTP | Internal |
| PostgreSQL | 5432 | TCP | Internal |
| Kafka | 9092 | TCP | Internal |
| Zipkin | 9412 | HTTP | Internal |

## 10. Scalability Considerations

### 10.1 Horizontal Scaling

```text
Load Distribution:

             ┌─────────────┐
             │ Load        │
             │ Balancer    │
             └──────┬──────┘
                    │
        ┌───────────┼───────────┐
        │           │           │
        ▼           ▼           ▼
    ┌───────┐   ┌───────┐   ┌───────┐
    │ ETL-1 │   │ ETL-2 │   │ ETL-3 │
    │ 8000  │   │ 8000  │   │ 8000  │
    └───┬───┘   └───┬───┘   └───┬───┘
        │           │           │
        └───────────┼───────────┘
                    │
                    ▼
            ┌───────────────┐
            │  PostgreSQL   │
            │  Connection   │
            │  Pool         │
            └───────────────┘
```

### 10.2 CDC Service Limitations

**Important**: Only ONE CDC service instance should monitor a given database table to avoid duplicate events.

Options for high availability:

1. Active-Passive: One active, one standby
2. Table partitioning: Different instances monitor different tables
3. Leader election: Use ZooKeeper/Consul for leader election

### 10.3 Replica Replication Ordering

When `xtrmetl.replica.enabled=true`, the CDC service can also consume CDC topics and apply them to a replica DB.

**Important**: Kafka ordering is guaranteed only within a single partition of a single topic. Schema-change events
(`*.schema-changes`) and data events (e.g. `*.public.processed_data`) can arrive and be processed out-of-order.

Mitigations in this codebase:

- Single listener container (default `concurrency=1`) and `AckMode.RECORD` (commit only after replica apply succeeds)
- Retry + dead-letter routing via `DefaultErrorHandler`/`.DLT` to tolerate transient schema/data race windows

Tuning knobs (replica application, DDL handling, and CDC schema changes):

- `xtrmetl.replica.kafka.retry-backoff-ms` (default: `1000`): backoff (ms) between retry attempts when replica apply fails
- `xtrmetl.replica.kafka.retry-max-attempts` (default: `30`): maximum retry attempts before routing to the dead-letter topic (≈30s with defaults)
- `xtrmetl.replica.kafka.concurrency` (default: `1`): number of concurrent listener threads for replica consumption
- `xtrmetl.replica.ddl-enabled` (default: `false`): enable/disable applying DDL events (`*.schema-changes`) on the replica
- `xtrmetl.replica.ddl-validation-mode` (default: `none`): DDL validation strategy; `none` (no validation/blocking), `whitelist`, or `blocklist` (alias: `blacklist`)
- `xtrmetl.replica.ddl-allowed-prefixes`: comma-separated DDL prefixes allowed when `ddl-validation-mode=whitelist`
- `xtrmetl.replica.ddl-blocked-prefixes` (effective only when `ddl-validation-mode=blocklist`; default blocklist includes `DROP TABLE`, `DROP SCHEMA`, `DROP DATABASE`, `TRUNCATE`): DDL prefixes blocked in blocklist mode
- `CDC_INCLUDE_SCHEMA_CHANGES` (default: `true`): controls whether Debezium emits schema change events (`*.schema-changes`)
- **PostgreSQL requirement**: schema-change DDL idempotency rewrites assume PostgreSQL `>= 9.6` (notably `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`)

---

**Document Version**: 1.1  
**Last Updated**: 2026-08-08  
**Author**: Technical Architecture Team
