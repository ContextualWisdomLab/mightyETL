# Product Requirements Document (PRD)

**Product:** mightyETL  
**Canonical baseline:** protected `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Last reconciled:** 2026-08-09

This PRD is the product-level source of truth for what mightyETL is intended to provide. It deliberately distinguishes protected-branch reality from active pull requests and future work so that a buyer, operator, or maintainer does not infer shipped capability from a design branch.

## 1. Executive Summary

### 1.1 Product overview

mightyETL is a modular enterprise ETL and change-data-capture platform. It can run as individual Spring services or as a composed microservice deployment. The protected baseline provides:

- bounded, prevalidated, transaction-scoped synchronous ETL into PostgreSQL;
- optional principal-scoped durable idempotency for synchronous ETL;
- opt-in durable asynchronous job **intake and owner-scoped status**, without a shipped worker yet;
- PostgreSQL CDC through embedded Debezium with Kafka publication;
- CDC source/target discovery and operator-safe status surfaces;
- target-connector discovery with PostgreSQL as the production load path and warehouse/BI connectors honestly exposed according to their runtime support state;
- Spring Cloud Gateway, Eureka, Config Server, Micrometer, and Zipkin-compatible infrastructure surfaces.

### 1.2 Product vision

Provide a defensible data-movement control plane in which retries are safe, long-running work is durable, CDC progress is honest, connectors are explicit about support level, operational state is observable, and every release can be traced from requirement to code, test, migration, review, and provenance evidence.

### 1.3 Capability status taxonomy

Canonical documents use these exact labels:

- `implemented_on_develop` — present on the protected baseline above.
- `active_pr` — present only on an open pull request; not shipped.
- `planned` — accepted issue/design direction, not merge-ready code.
- `superseded` — historical design or branch no longer intended for integration.
- `out_of_scope` — intentionally excluded from the current boundary.
- `known_gap` — shipped behavior whose limitation must remain visible.

### 1.4 Target users

- **Data engineers** — submit bounded ETL work, configure source/target connectivity, and operate durable data movement.
- **Platform/SRE teams** — observe CDC/job state, control lifecycle, measure SLOs, and perform rollback/recovery.
- **Application developers** — integrate over versioned HTTP and connector contracts.
- **Security/compliance teams** — review least privilege, provenance, data-retention, audit, and release evidence.
- **Analytics/data-platform owners** — consume CDC and target data without depending on undocumented implementation behavior.

## 2. Problem Statement

Enterprise data movement fails commercially when any of these are true:

- a retry duplicates writes or returns a response that was not committed with the data;
- a large batch partially commits before a later record fails;
- asynchronous work disappears with a process restart or cannot be identified by its owner;
- CDC offsets advance before downstream delivery is acknowledged;
- a stop endpoint reports success before an asynchronous engine has actually terminated;
- connector catalogs imply support that runtime code does not provide;
- authentication documentation claims a real trust boundary while the implementation is still a placeholder;
- CI reports green for a generated merge revision while a governance contract requires literal source-head evidence;
- architecture decisions live only in PR descriptions or chat history.

The product therefore treats correctness, durability, provenance, operational truthfulness, and documentation traceability as product requirements rather than internal engineering preferences.

## 3. Solution Overview

### 3.1 `implemented_on_develop`

#### Bounded atomic synchronous ETL

`POST /api/etl/process` accepts a bounded JSON array. Production processing validates and transforms the complete request before the first JDBC write and then performs all accepted target writes inside one Spring transaction. Only transient Spring data-access failures are retried. A deterministic request failure does not become a retry storm.

When `Idempotency-Key` is absent, existing synchronous behavior is preserved. When the key is present, an authenticated principal is required; the semantic key is normalized, principal-scoped, hashed, protected by a transaction-lifetime PostgreSQL try-lock, and tied to the request digest. Target writes and the durable response ledger commit in one transaction.

#### Durable asynchronous intake

When the disabled-by-default durable-intake feature is explicitly enabled:

- `POST /api/etl/jobs` durably creates or replays one principal-scoped pending job;
- `GET /api/etl/jobs/{job_record_id}` returns only an owner-scoped status representation;
- responses use `Cache-Control: no-store`;
- successful submissions use `202 Accepted`, `Location`, and `Idempotency-Replayed` metadata;
- malformed, missing, and foreign-owned identifiers share one non-enumerating not-found surface.

The protected baseline is intake-only. Worker execution, pagination, polling advice, conditional status, cancellation, and replay are not described as shipped. V2 requires terminal rows to have a null `request_payload`, but protected `develop` has no worker that transitions accepted jobs to terminal state; therefore an enabled intake can retain `PENDING` payloads without a bounded runtime lifetime. This retention boundary is a `known_gap`, and durable intake remains disabled by default until an integrated worker/lifecycle or explicit retention policy proves bounded retention and restart/recovery behavior.

#### CDC

The CDC service embeds Debezium 3.4 for PostgreSQL logical change capture, exposes start/stop/status/source/target surfaces, and publishes raw Debezium JSON to Kafka for compatibility. Optional canonical mapping remains an observational/scaffold path and does not replace the live publication format.

#### Connector truthfulness

`GET /api/etl/connectors` exposes connector capability/runtime state. PostgreSQL remains the primary load path. Databricks, Snowflake, Qlik and other surfaces must never be called production write paths unless their connector implementations, credentials, integration tests, and operational runbooks prove that claim.

### 3.2 `active_pr`

These are product directions with open code, not protected-branch capability:

| PR | Capability | Status boundary |
| --- | --- | --- |
| #121 | literal-head CI/SBOM controls and hourly NVIDIA OpenCode maintenance authority separation | `active_pr`; not deployed until merged |
| #139 | await Kafka acknowledgement before Debezium offset progress, bounded acknowledgement wait | `active_pr` |
| #142 | replace placeholder gateway token handling with Spring Security reactive OAuth 2.0 Resource Server JWT | `active_pr` |
| #143 | lease-fenced durable worker | `active_pr` |
| #144 | owner-scoped keyset pagination | `active_pr` |
| #145 | RFC 9110 `Retry-After` polling advice | `active_pr` |
| #146 | weak ETag / `If-None-Match` conditional status | `active_pr` |
| #147 | owner-scoped lease-fenced cancellation | `active_pr` |
| #148 | immutable-lineage terminal-job replay replacement | `active_pr` |

A downstream active PR may depend on an earlier active PR. Nothing in this table transfers checks, approvals, or security evidence across a future head/base change.

### 3.3 `planned`

- Issue #141: CDC stop must wait for graceful Debezium engine task completion before stopped-state observability is considered truthful.
- dead-letter/replay controls for connector-side failures outside the durable-job replay boundary;
- tenant-aware audit/control-plane UX and stronger enterprise identity lifecycle;
- measured production-like SLO evidence, disaster recovery, and release provenance acceptance.

### 3.4 `known_gap`

Protected develop still contains a placeholder gateway class named `JwtAuthenticationFilter` whose validator accepts only the literal example token `valid_token`. This is **not** a cryptographic JWT validation boundary. PR #142 is the active remediation. Until it integrates, deployments must not advertise protected develop as providing production-grade JWT authentication.

Protected develop CDC `stop()` requests engine close and clears the task reference without waiting for the asynchronous engine task to return. Issue #141 is the accepted reliability remediation path.

Protected develop durable intake persists request payloads for active rows and has no integrated worker/TTL that guarantees an accepted `PENDING` row becomes terminal. The V2 terminal-null constraint prevents payload retention *after* a terminal transition, but does not itself create that transition or a TTL. Keep durable intake disabled by default and restrict production enablement until the worker/retention lifecycle is integrated and validated.

## 4. Functional Requirements

### 4.1 ETL

#### FR-ETL-1: Bounded request admission

- Validate UTF-8 request byte size and record count against configured hard ceilings.
- Reject malformed JSON, non-array roots, duplicate JSON fields, invalid records, unsafe identifiers, and unsupported numeric representations before any target write.

#### FR-ETL-2: Whole-batch prevalidation

- Transform the complete accepted batch before the first JDBC write.
- Preserve input result ordering.
- Never silently discard a row.

#### FR-ETL-3: Transactional atomic load

- Commit all accepted synchronous rows or none.
- Retry only transient data-access failures.
- Preserve a stable RFC 9457 problem taxonomy for deterministic failures.

#### FR-ETL-4: Principal-scoped idempotency

- Support optional `Idempotency-Key` on `POST /api/etl/process`.
- Normalize the quoted RFC 9651 String representation and retained safe legacy raw representation to one semantic key.
- Never persist raw principals or raw idempotency keys.
- Same principal + same key + same payload replays the committed response without another target write.
- Same key with different payload fails closed.
- An in-progress same-key request is rejected without waiting indefinitely.

#### FR-ETL-5: Connector catalog

- Expose connector support/runtime state without secrets.
- Distinguish scaffold/discovery capability from a proven write path.

#### FR-ETL-6: Durable job intake

- `POST /api/etl/jobs` and `GET /api/etl/jobs/{job_record_id}` are available only when the explicit durable-intake feature is enabled.
- Submission requires a principal and bounded idempotency key.
- Status lookup is owner-scoped and no-store.
- Intake-only protected develop must not claim worker execution.
- Protected develop must keep intake disabled by default while active `request_payload` lifetime is not operationally bounded by an integrated worker or retention policy.

### 4.2 CDC

#### FR-CDC-1: PostgreSQL source capture

- Configure Debezium PostgreSQL capture from deployment-owned environment/configuration.
- Persist offsets and schema history according to the embedded-engine deployment contract.

#### FR-CDC-2: Change publication

- Preserve destination topic, key, and raw Debezium JSON compatibility on the protected baseline.
- Never advance source progress on a future acknowledged-delivery implementation before the configured downstream publication boundary succeeds.

#### FR-CDC-3: Lifecycle control

- Expose start and stop controls that are idempotent where documented.
- `known_gap`: protected develop stop completion is not yet equivalent to asynchronous engine termination; issue #141 owns remediation.

#### FR-CDC-4: Operator status

- Expose runtime state, configured source description, registered source/target capability, replication-slot observations, and finite-cardinality counters without secrets.

#### FR-CDC-5: Source/target extension

- New connectors must be discoverable through stable SPI contracts and must identify scaffold-only state honestly.

### 4.3 Authentication, authorization, and data access

#### FR-AUTH-1: Deployment principal boundary

- Keyed ETL and durable-job operations require an authenticated principal supplied by the runtime security context.
- Raw principal values must not be persisted in idempotency or durable-job records.

#### FR-AUTH-2: Gateway fail-closed production identity

- `known_gap`: protected develop does not currently provide a production cryptographic gateway identity implementation.
- `active_pr`: PR #142 provides the intended reactive OAuth 2.0 Resource Server JWT path.
- Production deployment must reject unknown/missing trust configuration rather than invent issuer, key, client secret, or example-token authority.

#### FR-AUTH-3: Superseded local-auth contract

Historical documentation described local username/password registration. That product API is not implemented. The exact strings below are retained only so historical documentation tests and migration readers can identify the superseded contract:

- superseded interface: `POST /auth/signin`
- superseded interface: `POST /auth/signup`

The local compose bootstrap still creates legacy user/role tables; persistence existence does not make these HTTP interfaces shipped.

### 4.4 Operations and governance

#### FR-OPS-1: Exact evidence

- A merge/release decision must bind evidence to the unchanged source head and current live base.
- Synthetic-merge previews may be retained as compatibility evidence but cannot substitute for literal-head evidence where the repository governance contract requires source identity.

#### FR-OPS-2: Durable documentation

- Public API, persisted state, lifecycle, security, deployment, autonomous-authority, and release-gate changes update PRD/TRD/Architecture/ADR/UML/ERD/traceability as applicable in the same pull request.

#### FR-OPS-3: Autonomous maintenance boundaries

- The model-executing agent may not gain review/merge authority by implication.
- Branch publication, PR mutation, Actions authorization, independent review, and merge remain separately permissioned authorities when the scheduler from PR #121 integrates.

## 5. Non-Functional Requirements

### NFR-REL-1: Atomicity

For synchronous ETL, a failure after admission must not commit a successful prefix of the request.

### NFR-REL-2: Idempotency

Repeated committed same-intent requests must converge on the same durable result without duplicate target effects within the documented transaction boundary.

### NFR-REL-3: Restart tolerance

Durable job intake records and idempotency ledger records survive application restart. CDC consumers and targets must tolerate documented at-least-once/replay behavior. Restart persistence alone does not satisfy bounded retention: accepted durable-job payloads require an integrated execution/retention lifecycle before production enablement is considered complete.

### NFR-SEC-1: Least privilege

Workflows, services, and connector credentials use the narrowest practical privilege and fail closed at trust boundaries.

### NFR-SEC-2: Sensitive-data handling

PII and business identifiers are not blanket-masked out of the product. Instead, access is purpose-bound, authorized, encrypted where stored/in transit, retained minimally, and audited. Logs/error responses must not disclose raw principals, idempotency keys, payloads, SQL, exception text, lease identifiers, or credentials. Durable request-payload lifetime must be operationally bounded before the intake path is promoted for production use.

### NFR-QUAL-1: Coverage

Owned production code must maintain 100% configured statement/line/method/branch coverage where the selected tool exposes the metric. Skipped tests never count as passing evidence.

### NFR-QUAL-2: Documentation

Public production APIs require beginner-readable documentation. Canonical architecture documents must be machine-validated against shipped source contracts.

### NFR-OPS-1: Observability

Use finite-cardinality metrics, structured logs, health/status endpoints, correlation identifiers where available, and OpenTelemetry-compatible semantic naming for new cross-service telemetry.

### NFR-OPS-2: Recovery

Migrations, durable state, connector operations, and releases require bounded rollback/recovery instructions. A rollback claim must identify irreversible external side effects explicitly.

### NFR-PERF-1: Resource bounds

No user request may create unbounded per-record thread fan-out, unbounded batch growth, unbounded retry, or unbounded in-memory retained payload without a documented limit.

### NFR-COMP-1: Standalone and MSA interoperability

Each service remains independently operable through documented configuration while composed deployments preserve stable APIs/event/connector contracts.

## 6. Data Model

The authoritative logical and physical overview is `docs/ERD.md`. Protected develop persists or bootstraps the following structures.

### 6.1 Primary ETL target / local compose bootstrap

The local compose schema retains these actual objects. `users`, `roles`, and `user_roles` are legacy bootstrap state, not proof of a shipped authentication API.

```sql
-- legacy compose bootstrap: CREATE TABLE roles
CREATE TABLE roles (...);

-- legacy compose bootstrap: CREATE TABLE users
CREATE TABLE users (...);

CREATE TABLE user_roles (...);
CREATE TABLE processed_data (...);
```

### 6.2 Idempotency ledger — `implemented_on_develop`

```sql
CREATE TABLE etl_idempotency_records (
    idempotency_key_hash CHAR(64) PRIMARY KEY,
    request_digest CHAR(64) NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
```

### 6.3 Durable job intake — `implemented_on_develop` schema, `known_gap` runtime retention

```sql
CREATE TABLE etl_job_records (
    job_record_id UUID PRIMARY KEY,
    principal_scope_hash CHAR(64) NOT NULL,
    submission_key_hash CHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    request_payload TEXT,
    job_status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL,
    failure_code VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
```

Protected-develop lifecycle values are `PENDING`, `RUNNING`, `SUCCEEDED`, and `FAILED`. V2 enforces payload presence for active rows and null payload for terminal rows, but protected `develop` does not integrate the worker transition that realizes terminal clearing. Thus bounded runtime retention remains a `known_gap` and durable intake remains disabled by default. Lease, pagination, cancellation, and replay fields live only on active stack PRs and are not part of this baseline DDL.

## 7. API Specifications

`docs/API_CONTRACT.md` is the canonical detailed contract.

### 7.1 Synchronous ETL

`POST /api/etl/process`

```json
[
  {"id":"record_001","name":"Example","email":"USER@EXAMPLE.COM","amount":"12.50"}
]
```

Optional request field: `Idempotency-Key`. Keyed requests require an authenticated principal. Success remains the legacy newline-delimited text representation plus `Idempotency-Replayed` when keyed. Errors use the stable RFC 9457 problem contract.

### 7.2 Connector catalog

`GET /api/etl/connectors`

Returns product name, primary load path, connector support/runtime metadata, and a documentation pointer without credentials.

### 7.3 Durable job intake — feature-gated

`POST /api/etl/jobs`

```json
[
  {"id":"record_001","name":"Example"}
]
```

Returns `202 Accepted` with a job identifier, status, status URL, `Location`, and replay metadata.

`GET /api/etl/jobs/{job_record_id}`

Returns the owner-scoped status representation. The path notation in this document uses `job_record_id`; the current Spring route variable is `jobRecordId` and is treated as the same opaque resource identifier.

### 7.4 CDC

- `POST /api/cdc/start`
- `POST /api/cdc/stop`
- `GET /api/cdc/status`
- `GET /api/cdc/sources`
- `GET /api/cdc/targets`

The current stop response does not prove asynchronous Debezium task termination; see `known_gap` issue #141.

## 8. Deployment Architecture

The services remain independently runnable and compose into a microservice deployment:

| Service | Default port | Responsibility |
| --- | ---: | --- |
| Zuul Gateway / Spring Cloud Gateway | 8080 | routing and security boundary |
| ETL Service | 8000 | bounded ETL, idempotency, durable intake, connector catalog |
| CDC Service | 8001 | Debezium capture, Kafka publication, CDC control/status |
| Eureka Server | 8761 | service discovery |
| Config Server | 8888 | optional configuration service |
| Zipkin | 9412 | tracing backend when enabled |

PostgreSQL and Kafka are external runtime dependencies for the relevant paths. Standalone operation must remain possible without forcing unused services into a deployment.

## 9. Success Metrics and Acceptance KPIs

The following are release/operations targets, not claims of already measured production attainment:

- **100%** configured owned-production statement/branch coverage before protected merge.
- **100%** public owned-production API documentation coverage.
- **0** accepted releases with unresolved critical/high actionable security findings.
- **0** target-row delta for successful bounded atomic synchronous requests (`expected rows == committed rows`).
- **0** duplicate target effects for committed same-principal/same-key/same-payload idempotent retries within the transactional boundary.
- **100%** release artifacts with SBOM/provenance evidence required by repository policy.
- CDC acknowledged-delivery and graceful-stop SLOs remain **not yet claimed** until PR #139 / issue #141 integrate and production-like measurements exist.
- Durable asynchronous intake has no production-retention SLO claim until pending payload lifetime is operationally bounded and recovery-tested.

## 10. Risk Assessment and Mitigation

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Placeholder gateway token logic mistaken for production auth | unauthorized access / diligence failure | explicit `known_gap`; fail-closed deployment; PR #142; threat-model/test gates |
| Batch partial commit | data corruption | whole-batch prevalidation + transaction + rollback tests |
| Duplicate idempotent retry | duplicate target effects | principal/key hash, request digest, try-lock, atomic ledger/target transaction |
| Durable pending payload retained without worker/TTL | privacy/retention and storage growth risk | keep intake disabled by default; mark `known_gap`; integrate worker/retention lifecycle with restart/recovery tests before production promotion |
| CDC publish before broker acknowledgement | offset/data-loss ambiguity | PR #139 acknowledged-delivery path; retain at-least-once/replay-tolerant claim until integrated |
| CDC stop reports early | false operator state | issue #141 bounded completion wait contract |
| Active PR documented as shipped | procurement/operations error | status taxonomy + machine-checked traceability |
| Synthetic-merge CI mistaken for literal-head proof | stale/wrong-source acceptance | PR #121 exact-source controls; exact-head gate language in TRD/test strategy |
| Autonomous agent gains excessive authority | supply-chain compromise | separate model/read and deterministic writer/review/merge authorities; writer lease; non-forced CAS publication |
| PII removed by blanket masking | product unusability | purpose-bound authorization/encryption/retention/audit instead of blanket removal |

## 11. Roadmap Boundary

The immediate integration order for durable jobs remains governed by stack ancestry and protected merge evidence, not this document's narrative order. After the active durable stack integrates, canonical PRD/TRD/UML/ERD/ADRs must be updated in the same integration sequence before any capability is relabeled `implemented_on_develop`.

## 12. References

Fielding, R., Nottingham, M., & Reschke, J. (2022). *HTTP Semantics* (RFC 9110). RFC Editor. https://www.rfc-editor.org/info/rfc9110

Nottingham, M., & Kamp, P.-H. (2024). *Structured Field Values for HTTP* (RFC 9651). RFC Editor. https://www.rfc-editor.org/info/rfc9651

Nottingham, M., Wilde, E., & Dalal, S. (2023). *Problem Details for HTTP APIs* (RFC 9457). RFC Editor. https://www.rfc-editor.org/info/rfc9457
