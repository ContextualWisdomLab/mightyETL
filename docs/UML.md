# UML and Behavioral Diagrams

**Baseline:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Notation:** Mermaid diagram-as-code  
**Status rule:** every view distinguishes `implemented_on_develop`, `active_pr`, `planned`, and `known_gap` where ambiguity would otherwise arise.

These views complement `ARCHITECTURE.md`. They show current calls, state transitions, deployment, data/control authority, and unshipped overlays without promoting active work to protected truth.

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
    class CdcService
    class CdcSourceRegistry
    class CdcTargetRegistry

    EtlController --> EtlService
    EtlController --> TargetConnectorDispatcher
    EtlJobController --> EtlJobService
    CdcController --> CdcService
    CdcController --> CdcSourceRegistry
    CdcController --> CdcTargetRegistry
```

`EtlJobController` exists on protected develop but is disabled by default and provides intake/status only.

## 2. Synchronous ETL Sequence — `implemented_on_develop`

```mermaid
sequenceDiagram
    actor User
    participant Controller as EtlController
    participant Service as EtlService
    participant Target as PostgreSQL processed_data

    User->>Controller: POST /api/etl/process
    Controller->>Service: processData(payload)
    Service->>Service: bound, parse, validate, transform whole batch
    alt validation fails before writes
        Service-->>Controller: EtlRequestException
        Controller-->>User: RFC 9457 problem
    else whole batch prepared
        loop records in input order
            Service->>Target: parameterized INSERT
        end
        Target-->>Service: one transaction commits
        Service-->>User: deterministic result
    end
```

## 3. Idempotent Synchronous ETL — `implemented_on_develop`

```mermaid
sequenceDiagram
    actor User
    participant Controller as EtlController
    participant Service as EtlService
    participant Lock as PostgresEtlRequestLock
    participant Ledger as etl_idempotency_records
    participant Target as processed_data

    User->>Controller: payload + Idempotency-Key + Principal
    Controller->>Service: exact semantic intent
    Service->>Lock: pg_try_advisory_xact_lock(scope,key)
    alt competing request
        Service-->>User: 409 in progress
    else same committed digest
        Ledger-->>Service: response_body
        Service-->>User: replay
    else key reused with different digest
        Service-->>User: 409 key conflict
    else first request
        Service->>Target: all writes
        Service->>Ledger: response record
        Note over Target,Ledger: same transaction
        Service-->>User: success
    end
```

## 4. Durable Job Intake State — `implemented_on_develop`

```mermaid
stateDiagram-v2
    [*] --> PENDING: accepted intake
    PENDING --> RUNNING: schema permits; no protected worker
    RUNNING --> SUCCEEDED: schema-permitted terminal state
    RUNNING --> FAILED: schema-permitted terminal state
    PENDING --> FAILED: schema-permitted terminal state
    SUCCEEDED --> [*]
    FAILED --> [*]

    note right of PENDING
      Protected develop is intake/status only.
      Worker transitions are active_pr #143.
    end note
```

## 5. Durable Job Active-PR Evolution — `active_pr`

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> RUNNING: #143 lease claim
    RUNNING --> SUCCEEDED: #143 fenced success
    RUNNING --> FAILED: #143 terminal failure
    PENDING --> CANCELLED: #147 owner cancellation
    RUNNING --> CANCELLED: #147 owner cancellation
    FAILED --> REPLAY_REQUEST: #148 eligible source
    CANCELLED --> REPLAY_REQUEST: #148 eligible source
    REPLAY_REQUEST --> PENDING: new derived job
    SUCCEEDED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]
```

Every state or field beyond protected V2 remains `active_pr`; predecessor evidence does not transfer.

## 6. Durable Polling and Conditional Status — `active_pr`

```mermaid
sequenceDiagram
    actor Operator
    participant API as EtlJobController
    participant Store as etl_job_records

    Operator->>API: GET /api/etl/jobs/{job_record_id}
    API->>Store: owner-scoped lookup
    Store-->>API: safe status snapshot
    alt active and worker enabled (#145)
        API-->>Operator: 200 + Retry-After + no-store
    else terminal/current
        API-->>Operator: 200 + no-store
    end
    opt If-None-Match (#146)
        Operator->>API: conditional GET
        API->>Store: authorize owner before comparison
        API-->>Operator: 304 only for same representation
    end
```

## 7. CDC Capture — `implemented_on_develop` + `known_gap`

```mermaid
sequenceDiagram
    participant PG as PostgreSQL source
    participant DBZ as DebeziumEngine
    participant CDC as CdcService
    participant Kafka as KafkaTemplate

    PG-->>DBZ: WAL change
    DBZ->>CDC: ChangeEvent
    CDC->>CDC: optional canonical observation
    CDC->>Kafka: send(destination,key,value)
    Note over CDC,Kafka: protected develop does not await broker acknowledgement
```

PR #139 is the `active_pr` acknowledgement-before-progress repair.

## 8. CDC Stop State — `known_gap` / `planned`

```mermaid
stateDiagram-v2
    [*] --> RUNNING
    RUNNING --> CLOSE_REQUESTED: stop calls engine.close
    CLOSE_REQUESTED --> REFERENCES_CLEARED: protected finally block
    REFERENCES_CLEARED --> [*]: isRunning becomes false
    CLOSE_REQUESTED --> ENGINE_COMPLETED: required Future completion
    ENGINE_COMPLETED --> [*]

    note right of REFERENCES_CLEARED
      Reference clearing is not proof that Debezium run returned.
      Issue #141 owns bounded truthful completion.
    end note
```

## 9. Gateway Trust State — `known_gap` / `active_pr`

```mermaid
stateDiagram-v2
    [*] --> DEVELOP_PLACEHOLDER
    DEVELOP_PLACEHOLDER --> DENY_MODE: #142 explicit deny
    DEVELOP_PLACEHOLDER --> JWT_RESOURCE_SERVER: #142 configured JWT
    DENY_MODE --> [*]
    JWT_RESOURCE_SERVER --> [*]

    note right of DEVELOP_PLACEHOLDER
      Protected develop accepts only literal example token valid_token.
    end note
```

## 10. Deployment View — `implemented_on_develop`

```mermaid
flowchart TB
    subgraph ClientZone
        Client[Client / operator]
    end
    subgraph ServiceZone
        Gateway[Gateway :8080]
        ETL[ETL :8000]
        CDC[CDC :8001]
        Eureka[Eureka :8761]
        Config[Config Server :8888]
    end
    subgraph DataZone
        Target[(PostgreSQL target)]
        Source[(PostgreSQL source)]
        Kafka[(Kafka)]
    end
    subgraph ObservabilityZone
        Zipkin[Zipkin host :9412]
    end

    Client --> Gateway
    Client -. direct deployment .-> ETL
    Gateway --> ETL
    Gateway --> CDC
    ETL --> Target
    Source --> CDC
    CDC --> Kafka
    Gateway -. discovery .-> Eureka
    ETL -. discovery .-> Eureka
    CDC -. discovery .-> Eureka
    Gateway -. configuration .-> Config
    ETL -. traces .-> Zipkin
    CDC -. traces .-> Zipkin
```

Standalone ETL and standalone CDC remain valid deployment shapes.

## 11. Autonomous Development Authority — `active_pr` #121

```mermaid
sequenceDiagram
    participant Scheduler as Hourly/manual trigger
    participant Model as OpenCode model job
    participant Branch as Deterministic branch publisher
    participant Pull as Deterministic PR publisher
    participant Actions as Exact-head run authorizer
    participant Reviewer as Independent reviewer
    participant Merge as Protected merge authority

    Scheduler->>Model: inspect/test/create bounded local commits
    Model-->>Branch: digest-bound bundle; no GitHub write
    Branch->>Branch: verify parent, paths, commits, ancestry
    Branch-->>Pull: non-forced feature ref
    Pull-->>Actions: one validated Draft PR
    Actions-->>Reviewer: unchanged exact-head evidence
    Reviewer-->>Merge: formal non-author decision
    Merge->>Merge: rulesets, gates, expected head
```

`NVIDIA_NIM_API_KEY` belongs only to model execution. Review and merge are not model capabilities.

## 12. Branch-Writer Compare-and-Swap — operating design

```mermaid
sequenceDiagram
    participant Agent
    participant GitData as GitHub Git Data API
    participant Ref as feature branch ref

    Agent->>Ref: read exact live parent
    Agent->>GitData: create blobs/tree/commit(parent=live parent)
    Agent->>Ref: re-read exact parent
    alt parent unchanged
        Agent->>Ref: update ref force=false
        Ref-->>Agent: exact new head
    else parent moved
        Agent-->>Agent: discard prepared publication and freeze branch
    end
```

File-level blob checks do not by themselves establish branch-wide parent CAS.

## 13. Service and configuration identity authority

```mermaid
flowchart LR
    User[External principal]
    Gateway[Gateway identity boundary\nactive_pr #142]
    Direct[Direct ETL boundary\nknown_gap #161]
    CDCControl[CDC control identity\nplanned #187]
    Registry[Eureka identity\nplanned #185]
    ConfigRepo[Config repository authority\nactive_pr #189]
    ETL[ETL Service]
    CDC[CDC Service]

    User -->|issuer audience purpose| Gateway
    User -. independently authenticated .-> Direct
    Gateway -->|service identity or token exchange required| ETL
    Gateway -->|service identity required| CDC
    User -. operator identity .-> CDCControl
    Registry -. routing metadata, not authorization .-> ETL
    ConfigRepo -. explicit approved source .-> Gateway
```

No arrow inherits authentication from another arrow. Principal, workload, operator, and tenant authority remain separate.

## 14. Schema and recovery authority

```mermaid
sequenceDiagram
    participant Source as Exact protected source
    participant Flyway as Flyway migration authority
    participant DB as PostgreSQL
    participant Backup as backup_bundle + manifest
    participant Restore as restore rehearsal
    participant App as mightyETL readiness/invariants
    participant External as Kafka / Debezium / DLT / targets

    Source->>Flyway: immutable ordered migrations
    Flyway->>DB: single schema-mutation authority
    DB->>Backup: logical archive + exact provenance + digest
    Backup->>Restore: validate manifest and archive before write
    Restore->>DB: clean-target restore, no uncontrolled owner/privileges
    DB->>App: verify migration level, startup, readiness, durable invariants
    App->>External: classify and reconcile separate side-effect domains
    Note over Backup,External: backup existence is not DR, RPO, or RTO attainment
```

PR #184 and PR #208 are `active_pr`; this diagram is governing target architecture, not shipped behavior.

## 15. Dead-letter lifecycle authority

```mermaid
stateDiagram-v2
    [*] --> FAILED_EVENT: production apply/publish failure
    FAILED_EVENT --> QUARANTINED: authorized DLT publication
    QUARANTINED --> RETAINED: encrypted bounded retention
    RETAINED --> REDRIVE_REVIEW: authenticated request
    REDRIVE_REVIEW --> REJECTED: invalid authority/schema/policy
    REDRIVE_REVIEW --> REDRIVEN: new idempotent lineage
    REDRIVEN --> QUARANTINED: redrive failure creates new bounded evidence
    RETAINED --> DELETED: retention/deletion policy
    REJECTED --> RETAINED

    note right of QUARANTINED
      Terminal by default.
      Never re-enters the normal consumer automatically.
    end note
```

Payload/header access, encryption, retention, export, deletion, residency, and redrive are explicit data-governance controls.

## 16. Evidence and release authority

```mermaid
flowchart LR
    Head[source_head_sha]
    Base[pr_base_snapshot_sha]
    Live[live_base_tip_sha]
    MergeTree[synthetic merge revision]
    Checkout[actual workflow checkout]
    Coverage[non-empty selected + repository-wide coverage]
    Dependencies[complete dependency graph]
    Security[scanner + Dependency Review + SBOM]
    Review[formal non-author exact-head review]
    Protected[protected integrated head]
    Runtime[protected operational acceptance]
    Artifact[package/image + digest]
    Provenance[SBOM/provenance/reproducibility/licensing]
    Publish[publication + independent verification]

    Head --> MergeTree
    Base --> MergeTree
    Live --> MergeTree
    MergeTree --> Checkout
    Head -. literal-source workflow .-> Checkout
    Checkout --> Coverage
    Checkout --> Dependencies --> Security
    Coverage --> Review
    Security --> Review
    Review --> Protected --> Runtime
    Protected --> Artifact --> Provenance --> Publish
```

No single green node substitutes for another. Synthetic integration, literal source, formal review, protected runtime, artifact, legal/licensing, and publication evidence remain separate authorities.
