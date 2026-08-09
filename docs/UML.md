# UML and Behavioral Diagrams

**Baseline:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Notation:** Mermaid diagram-as-code  
**Status rule:** every diagram is labeled `implemented_on_develop`, `active_pr`, `planned`, or `known_gap` where ambiguity could otherwise arise.

These diagrams complement `ARCHITECTURE.md`: architecture explains why boundaries exist; UML focuses on component relationships, calls, state transitions, deployment, and authority flow.

## 1. Component View — `implemented_on_develop`

```mermaid
classDiagram
    class EtlController {
      +processData(jsonInput, idempotencyKey, principal)
      +connectors()
    }
    class EtlService {
      +processData(data)
      +processDataIdempotently(data, key, principal)
    }
    class EtlJobController {
      +submit(payload, idempotencyKey, principal)
      +status(jobRecordId, principal)
    }
    class EtlJobService
    class TargetConnectorDispatcher
    class CdcController {
      +startCdc()
      +stopCdc()
      +status()
      +sources()
      +targets()
    }
    class CdcService {
      +start()
      +stop()
      +isRunning()
      +getStatus()
    }
    class CdcSourceRegistry
    class CdcTargetRegistry

    EtlController --> EtlService
    EtlController --> TargetConnectorDispatcher
    EtlJobController --> EtlJobService
    CdcController --> CdcService
    CdcController --> CdcSourceRegistry
    CdcController --> CdcTargetRegistry
```

`EtlJobController` exists on protected develop but its entire controller is feature-gated and disabled by default.

## 2. Synchronous ETL Sequence — `implemented_on_develop`

```mermaid
sequenceDiagram
    actor User
    participant Controller as EtlController
    participant Service as EtlService
    participant Target as PostgreSQL processed_data

    User->>Controller: POST /api/etl/process
    Controller->>Service: processData(payload)
    Service->>Service: bounded parse + validate all rows
    Service->>Service: deterministic transform all rows
    alt any validation fails
        Service-->>Controller: EtlRequestException
        Controller-->>User: RFC 9457 problem
    else all rows prepared
        loop rows in input order
            Service->>Target: parameterized INSERT
        end
        Target-->>Service: transaction commit
        Service-->>Controller: result lines
        Controller-->>User: 200 text/plain
    end
```

## 3. Idempotent Synchronous ETL Sequence — `implemented_on_develop`

```mermaid
sequenceDiagram
    actor User
    participant Controller as EtlController
    participant Service as EtlService
    participant Lock as PostgresEtlRequestLock
    participant Ledger as etl_idempotency_records
    participant Target as processed_data

    User->>Controller: POST /api/etl/process + Idempotency-Key
    Controller->>Service: payload + key + Principal
    Service->>Lock: pg_try_advisory_xact_lock(hash(scope,key))
    alt competing request
        Service-->>User: 409 etl_idempotency_request_in_progress
    else committed record exists and digest matches
        Ledger-->>Service: response_body
        Service-->>User: replay + Idempotency-Replayed=true
    else key exists with different digest
        Service-->>User: 409 key reuse conflict
    else new semantic request
        Service->>Service: whole-batch preparation
        Service->>Target: all target writes
        Service->>Ledger: response record
        Note over Target,Ledger: same transaction
        Service-->>User: success + Idempotency-Replayed=false
    end
```

## 4. Durable Job Intake State — `implemented_on_develop`

```mermaid
stateDiagram-v2
    [*] --> PENDING: accepted intake
    PENDING --> RUNNING: schema permits state, but no protected worker currently drives it
    RUNNING --> SUCCEEDED: schema-permitted terminal state
    RUNNING --> FAILED: schema-permitted terminal state
    PENDING --> FAILED: schema-permitted terminal state
    SUCCEEDED --> [*]
    FAILED --> [*]

    note right of PENDING
      Protected develop is intake/status only.
      Actual lease-fenced worker transitions are active_pr #143.
    end note
```

The diagram distinguishes a persisted allowed state machine from a shipped background execution engine. A schema-permitted transition is not proof that protected develop currently performs it.

## 5. Durable Job Active-PR Evolution — `active_pr`

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> RUNNING: #143 exact lease claim
    RUNNING --> SUCCEEDED: #143 fenced success
    RUNNING --> FAILED: #143 bounded terminal failure
    PENDING --> CANCELLED: #147 owner cancellation
    RUNNING --> CANCELLED: #147 owner cancellation
    FAILED --> REPLAY_REQUEST: #148 replay source
    CANCELLED --> REPLAY_REQUEST: #148 replay source
    REPLAY_REQUEST --> PENDING: #148 creates new derived job
    SUCCEEDED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]

    note right of CANCELLED
      active_pr only; not protected-develop state.
    end note
```

## 6. Durable Job Polling Sequence — `active_pr` stack overlay

```mermaid
sequenceDiagram
    actor Operator
    participant API as EtlJobController
    participant Store as etl_job_records

    Operator->>API: GET /api/etl/jobs/{job_record_id}
    API->>Store: owner-scoped lookup
    Store-->>API: status snapshot
    alt active state and worker enabled (#145)
        API-->>Operator: 200 + Retry-After + no-store
    else terminal/current status
        API-->>Operator: 200 + no-store
    end
    opt If-None-Match on #146
        Operator->>API: GET + validator
        API->>Store: owner-safe lookup first
        API-->>Operator: 304 when representation matches
    end
```

## 7. CDC Capture Sequence — `implemented_on_develop` + `known_gap`

```mermaid
sequenceDiagram
    participant PG as PostgreSQL source
    participant DBZ as DebeziumEngine
    participant CDC as CdcService
    participant Kafka as KafkaTemplate

    PG-->>DBZ: WAL change
    DBZ->>CDC: ChangeEvent
    CDC->>CDC: optional canonical-map observation
    CDC->>Kafka: send(destination,key?,value)
    Note over CDC,Kafka: known_gap: protected develop does not await broker acknowledgement
```

PR #139 is the `active_pr` path that waits for acknowledgement and retries/fails closed before Debezium progress.

## 8. CDC Stop State — `known_gap` and `planned`

```mermaid
stateDiagram-v2
    [*] --> RUNNING
    RUNNING --> CLOSE_REQUESTED: stop() -> DebeziumEngine.close()
    CLOSE_REQUESTED --> REFERENCE_CLEARED: protected develop finally block
    REFERENCE_CLEARED --> [*]: isRunning() becomes false
    CLOSE_REQUESTED --> ENGINE_COMPLETED: desired Future completion
    ENGINE_COMPLETED --> [*]

    note right of REFERENCE_CLEARED
      known_gap: reference clearing can precede asynchronous run() completion.
      planned issue #141 requires bounded truthful completion semantics.
    end note
```

## 9. Gateway Trust State — `known_gap` / `active_pr`

```mermaid
stateDiagram-v2
    [*] --> DEVELOP_PLACEHOLDER
    DEVELOP_PLACEHOLDER --> DENY_MODE: #142 configuration mode
    DEVELOP_PLACEHOLDER --> JWT_RESOURCE_SERVER: #142 configured JWT mode
    DENY_MODE --> [*]
    JWT_RESOURCE_SERVER --> [*]

    note right of DEVELOP_PLACEHOLDER
      protected develop accepts only literal example token valid_token.
      Do not classify this as production JWT validation.
    end note
```

## 10. Deployment UML — `implemented_on_develop`

```mermaid
flowchart TB
    subgraph ClientZone[Client zone]
        Client[Client / operator]
    end

    subgraph ServiceZone[mightyETL service zone]
        Gateway[Gateway :8080]
        ETL[ETL :8000]
        CDC[CDC :8001]
        Eureka[Eureka :8761]
        Config[Config :8888]
    end

    subgraph DataZone[Data / messaging zone]
        Target[(PostgreSQL target)]
        Source[(PostgreSQL source)]
        Kafka[(Kafka)]
    end

    subgraph ObservabilityZone[Observability]
        Zipkin[Zipkin :9412]
    end

    Client --> Gateway
    Gateway --> ETL
    Gateway --> CDC
    ETL --> Target
    Source --> CDC
    CDC --> Kafka
    Gateway -. registry .-> Eureka
    ETL -. registry .-> Eureka
    CDC -. registry .-> Eureka
    Gateway -. config .-> Config
    ETL -. traces .-> Zipkin
    CDC -. traces .-> Zipkin
```

Standalone ETL and standalone CDC deployment remain supported architecture shapes; the full graph is not a mandatory all-or-nothing bundle.

## 11. Autonomous Development Authority — `active_pr` #121

```mermaid
sequenceDiagram
    participant Scheduler as Hourly trigger
    participant Model as OpenCode model job (read-only GitHub)
    participant Branch as Deterministic branch publisher
    participant PR as Deterministic PR publisher
    participant Actions as Exact-head run authorizer
    participant Reviewer as Independent reviewer
    participant Merge as Protected merge authority

    Scheduler->>Model: inspect / test / produce local commits
    Model-->>Branch: checksum-bound candidate bundle
    Branch->>Branch: verify exact predecessor + paths + ancestry
    Branch-->>PR: publish one non-forced feature ref
    PR->>PR: verify head + bounded paths
    PR-->>Actions: create/update one Draft PR
    Actions->>Actions: authorize only unchanged pull_request head runs
    Actions-->>Reviewer: exact-head evidence
    Reviewer-->>Merge: formal non-author review
    Merge->>Merge: rulesets + gates + expected-head check
```

`NVIDIA_NIM_API_KEY` belongs only to model execution. Review and merge are not model capabilities.

## 12. Branch-Writer Compare-and-Swap — operating design

```mermaid
sequenceDiagram
    participant Agent
    participant API as GitHub Git Data API
    participant Ref as feature branch ref

    Agent->>Ref: read exact live parent
    Agent->>API: create blobs/tree/commit(parent=live parent)
    Agent->>Ref: re-read exact live parent
    alt unchanged
        Agent->>Ref: update ref force=false to prepared descendant
        Ref-->>Agent: new exact head
    else moved
        Agent-->>Agent: discard stale publication and freeze this branch
    end
```

File-level Contents API blob checks remain useful for file identity, but they do not by themselves establish a branch-wide expected-parent compare-and-swap.
