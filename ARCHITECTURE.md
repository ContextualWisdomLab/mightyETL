# mightyETL System Architecture

**Canonical protected baseline:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Last reconciled:** 2026-08-09

This document describes the architecture that actually exists on protected `develop`, then overlays open work with an explicit `active_pr` label. A diagram containing an active PR is not a statement that the feature is deployed.

## 1. Architecture Status Vocabulary

- `implemented_on_develop` — protected baseline reality.
- `active_pr` — open PR only.
- `planned` — issue/design, no protected implementation.
- `superseded` — historical path, not an integration target.
- `out_of_scope` — intentionally excluded.
- `known_gap` — protected behavior with a material limitation.

## 2. High-Level Component Architecture

```mermaid
flowchart TB
    Client[External client / operator]
    Gateway[Spring Cloud Gateway\nport 8080\nknown_gap identity on develop]
    ETL[ETL Service\nport 8000]
    CDC[CDC Service\nport 8001]
    Eureka[Eureka Server\nport 8761]
    Config[Config Server\nport 8888]
    Zipkin[Zipkin / tracing\nport 9412 when enabled]
    Target[(PostgreSQL target)]
    Source[(PostgreSQL CDC source)]
    Kafka[(Apache Kafka)]
    Consumers[Downstream consumers]

    Client --> Gateway
    Gateway --> ETL
    Gateway --> CDC
    ETL --> Target
    Source -->|WAL / pgoutput| CDC
    CDC -->|raw Debezium JSON| Kafka
    Kafka --> Consumers
    Gateway -. discovery .-> Eureka
    ETL -. discovery .-> Eureka
    CDC -. discovery .-> Eureka
    Gateway -. optional config .-> Config
    ETL -. telemetry .-> Zipkin
    CDC -. telemetry .-> Zipkin
```

The service decomposition is compatible with independent operation: an ETL-only deployment does not need a CDC engine, and a CDC deployment does not require an unused warehouse connector. Composition adds routing/discovery/observability; it does not erase service boundaries.

## 3. ETL Service Architecture — `implemented_on_develop`

### 3.1 ETL Processing Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant EC as EtlController
    participant ES as EtlService
    participant DB as PostgreSQL target

    C->>EC: POST /api/etl/process + JSON array
    EC->>ES: processData(payload)
    ES->>ES: enforce byte/record limits
    ES->>ES: strict parse + validate all records
    ES->>ES: transform all records
    Note over ES,DB: No JDBC target write before whole-batch preparation succeeds
    loop prepared records in input order
        ES->>DB: parameterized INSERT processed_data
    end
    DB-->>ES: transaction commit
    ES-->>EC: deterministic result body
    EC-->>C: 200 text/plain
```

The earlier per-record `CompletableFuture`/`Parallel Proc` architecture is retired. The live path is synchronous inside one Spring transaction so a later failure rolls back the batch rather than leaving committed prefix records.

### 3.2 Principal-scoped idempotency Flow

```mermaid
sequenceDiagram
    participant C as Authenticated client
    participant EC as EtlController
    participant ES as EtlService
    participant L as PostgreSQL advisory lock
    participant DB as Target + etl_idempotency_records

    C->>EC: POST /api/etl/process + Idempotency-Key
    EC->>ES: payload, key, principal
    ES->>ES: validate key/principal + exact request digest
    ES->>L: try transaction-scoped lock(hash(principal,key))
    alt lock unavailable
        ES-->>C: RFC 9457 in-progress conflict
    else existing same digest
        DB-->>ES: committed response_body
        ES-->>C: replay response + Idempotency-Replayed: true
    else existing different digest
        ES-->>C: RFC 9457 key-reused conflict
    else first request
        ES->>ES: bounded whole-batch preparation
        ES->>DB: target writes
        ES->>DB: insert response ledger
        DB-->>ES: one transaction commits both
        ES-->>C: response + Idempotency-Replayed: false
    end
```

Raw principals and raw idempotency keys are not stored in `etl_idempotency_records`.

### 3.3 Durable job intake Flow

`EtlJobController` is `implemented_on_develop` but disabled by default. It is deliberately an intake/status boundary, not a claim of background execution.

```mermaid
sequenceDiagram
    participant C as Authenticated client
    participant JC as EtlJobController
    participant JS as EtlJobService
    participant DB as etl_job_records

    C->>JC: POST /api/etl/jobs + Idempotency-Key
    JC->>JS: submit(payload,key,principal)
    JS->>DB: create or replay owner-scoped durable record
    DB-->>JS: PENDING snapshot
    JS-->>JC: submission metadata
    JC-->>C: 202 + Location + Idempotency-Replayed
    C->>JC: GET /api/etl/jobs/{job_record_id}
    JC->>JS: owner-scoped lookup
    JS->>DB: select by job id + principal scope
    DB-->>JC: safe status snapshot
    JC-->>C: 200 + Cache-Control: no-store
```

On protected develop the job status domain is `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`. The request payload remains retained for active states because the worker/terminal clearing behavior is not yet integrated.

## 4. Durable Job Active Stack — `active_pr`

```mermaid
flowchart LR
    P121[#121 exact-source CI + scheduler]
    P143[#143 lease-fenced worker]
    P144[#144 owner pagination]
    P145[#145 Retry-After]
    P146[#146 conditional ETag]
    P147[#147 cancellation]
    P148[#148 replay replacement]

    P121 --> P143 --> P144 --> P145 --> P146 --> P147 --> P148
```

The arrow is a dependency/ancestry contract, not a release promise. Every predecessor integration can invalidate downstream base/evidence and requires fresh direct-base validation. These capabilities remain `active_pr` until protected merge.

## 5. CDC Event Capture Flow

### 5.1 `implemented_on_develop`

```mermaid
sequenceDiagram
    participant PG as PostgreSQL source
    participant D as Debezium Engine 3.4
    participant CS as CdcService
    participant K as KafkaTemplate / Kafka
    participant DC as Downstream consumer

    PG-->>D: logical replication events
    D->>CS: ChangeEvent(key,value,destination)
    CS->>CS: optional canonical-map observation
    CS->>K: send raw Debezium JSON
    K-->>DC: event stream
```

`known_gap`: protected develop does not wait for Kafka broker acknowledgement in `handleChangeEvent`. PR #139 is the `active_pr` acknowledged-delivery path and adds a finite acknowledgement wait/retry boundary before Debezium record progress.

### 5.2 CDC lifecycle

```mermaid
stateDiagram-v2
    [*] --> STOPPED
    STOPPED --> RUNNING: start()
    RUNNING --> STOP_REQUESTED: stop() / engine.close()
    STOP_REQUESTED --> STOPPED: current develop clears references
    RUNNING --> SHUTTING_DOWN: application shutdown
    SHUTTING_DOWN --> STOPPED: executor termination

    note right of STOP_REQUESTED
      known_gap: current stop() does not prove
      the asynchronous engine Future has returned.
      Issue #141 owns the planned repair.
    end note
```

Debezium documents `close()` as a graceful stop request and `run()` as returning only after remaining events and offset flushing complete. Therefore future operator state must distinguish request-to-stop from proven task completion.

## 6. Connector Architecture

### 6.1 ETL target connectors

`TargetConnectorDispatcher` owns target connector lifecycle/catalog behavior. The protected product's primary load path remains PostgreSQL. Warehouse/BI connector surfaces are useful discovery/configuration scaffolds, but support claims must follow runtime capability rather than documentation aspiration.

### 6.2 CDC source/target SPI

`CdcSourceRegistry`, `CdcTargetRegistry`, `CdcSourceFactory`, and the canonical record mapping surface allow future source/target evolution. The live capture path remains PostgreSQL Debezium → Kafka. `getStatus()` explicitly reports `anyToAny=false` on the protected baseline.

## 7. Persistence Architecture

Detailed relationships are in `docs/ERD.md`.

### 7.1 `implemented_on_develop`

- `processed_data` — local compose primary ETL target.
- `etl_idempotency_records` — principal/key-hash replay ledger.
- `etl_job_records` — durable asynchronous intake/status state.
- legacy local compose `users`, `roles`, `user_roles` — persisted bootstrap compatibility objects, not a shipped registration/login service.

### 7.2 `active_pr`

The durable-job stack adds lease, pagination-index, cancellation, and replay-lineage persistence in later PRs. These objects belong in the active-PR overlay of `docs/ERD.md` until integrated.

## 8. Security Architecture

### 8.1 Gateway identity

Protected develop has a class named `JwtAuthenticationFilter`, but its `validateToken` implementation accepts the literal example value `valid_token`. That is a `known_gap`, not production JWT validation.

PR #142 is `active_pr` and replaces this with Spring Security reactive OAuth 2.0 Resource Server JWT configuration. Until protected integration, the architecture makes no issuer/JWK/audience/algorithm claim.

Historical architecture described local auth and password hashing. These identifiers are retained only as superseded traceability:

- superseded interface: `POST /auth/signin`
- superseded interface: `POST /auth/signup`
- superseded security claim: `BCrypt` password authentication

The local compose `password` column is legacy data shape and does not turn the superseded HTTP/authentication design into a shipped capability.

### 8.2 ETL owner/idempotency boundary

Authenticated `Principal` values are used to scope keyed requests and durable job lookup. Stored identities are one-way domain-separated hashes; client responses and ordinary telemetry exclude raw principal/key/payload/internal diagnostics.

### 8.3 PII policy

mightyETL must remain usable for legitimate enterprise data movement, so it does not require blanket PII masking. Controls are purpose-bound authorization, encryption, least privilege, minimal retention, auditable privileged access, and non-leaking logs/error/metric metadata.

## 9. Automation Authority Architecture — `active_pr` #121

Protected develop does **not** yet run this scheduler. The intended separation is documented so its security properties are reviewable before integration.

```mermaid
flowchart TB
    Timer[Hourly schedule / manual trigger]
    Model[maintain-repository\nOpenCode + NVIDIA_NIM_API_KEY\nGitHub read authority]
    Bundle[validated local commit bundle]
    BranchWriter[publish-agent-branch\ncontents: write only\nno model credential]
    PRWriter[publish-agent-pull-request\npull-requests: write only]
    RunAuthorizer[authorize-exact-head-checks\nactions: write only]
    Review[Independent review authority]
    Merge[Protected expected-head merge authority]

    Timer --> Model --> Bundle --> BranchWriter --> PRWriter --> RunAuthorizer --> Review --> Merge
```

Core invariants:

- the model job does not get repository write, review, or merge authority;
- deterministic publishers get no model credential;
- branch publication verifies exact predecessor/base, policy paths, commit/file bounds, ancestry, and post-write SHA;
- branch-wide expected-parent publication prefers Git Data commit construction plus non-forced `force=false` ref update;
- a branch-local writer conflict freezes only that branch for the invocation;
- review and merge remain independent.

## 10. CI / Evidence Architecture

### 10.1 Protected develop today

The current `CI` workflow uses ordinary `actions/checkout` with no explicit pull-request head ref. GitHub documents that `pull_request` workflows use `GITHUB_REF=refs/pull/<n>/merge`, and checkout uses that ref by default. Therefore a green source-executing job on protected develop can describe the generated merge preview rather than the literal PR head.

This is useful compatibility evidence, but it is not accepted as literal-head proof where the repository's exact-source governance requires that identity.

### 10.2 `active_pr` #121

#121 adds explicit head checkout plus exact-SHA verification for source-executing CI/SBOM and carries a separate central-scanner dependency for literal-head hard scanning. `synthetic-merge` evidence remains non-substitutable.

## 11. Monitoring and Observability

- Micrometer observations decorate key ETL/job/CDC control surfaces.
- CDC status exposes configured/runtime state and replication-slot information without secrets.
- Zipkin is the currently documented tracing backend when enabled.
- New cross-service telemetry should use OpenTelemetry semantic conventions where suitable.
- Metric dimensions must remain finite; resource/job/principal/secret identifiers do not become uncontrolled labels.

## 12. Deployment Architecture

```mermaid
flowchart LR
    subgraph Standalone_ETL[Standalone ETL deployment]
        EC[ETL Service :8000] --> EPG[(PostgreSQL)]
    end

    subgraph Standalone_CDC[Standalone CDC deployment]
        CP[(PostgreSQL source)] --> CC[CDC Service :8001] --> CK[(Kafka)]
    end

    subgraph Composed_MSA[Composed MSA]
        CG[Gateway :8080]
        CE[ETL :8000]
        CD[CDC :8001]
        ER[Eureka :8761]
        CF[Config :8888]
        Z[Zipkin :9412]
        CG --> CE
        CG --> CD
        CE -.-> ER
        CD -.-> ER
        CG -.-> ER
        CE -.-> Z
        CD -.-> Z
        CG -.-> CF
    end
```

No composed topology may make an independently useful service impossible to run without an unrelated component unless an explicit ADR changes that product principle.

## 13. Architecture Decision Index

The canonical decision records are indexed in `docs/adr/README.md`. Architecture changes that alter API, persisted state, trust, lifecycle, deployment, autonomous authority, or evidence semantics require an ADR status update in the same PR.

## 14. References

Debezium. (2026). *Debezium Engine 3.4*. Debezium Documentation. https://debezium.io/documentation/reference/3.4/development/engine.html

GitHub. (2026). *Events that trigger workflows*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows

OpenTelemetry Authors. (2025). *Semantic conventions*. OpenTelemetry. https://opentelemetry.io/docs/concepts/semantic-conventions/
