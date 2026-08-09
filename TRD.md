# Technical Requirements Document (TRD)

**Product:** mightyETL  
**Canonical protected baseline:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Last reconciled:** 2026-08-09

This document defines engineering constraints that must hold across mightyETL service, persistence, connector, workflow, review, and release boundaries. `PRD.md` defines product intent; `ARCHITECTURE.md` defines topology; `docs/TRACEABILITY.md` binds requirements to live evidence.

## 1. Status and Evidence Vocabulary

- `implemented_on_develop`: present on the protected baseline.
- `active_pr`: open PR only; not shipped.
- `planned`: accepted design/issue without protected implementation.
- `superseded`: historical path that must not receive new downstream work.
- `out_of_scope`: intentionally excluded.
- `known_gap`: protected behavior with a documented limitation.

Evidence is always bound to the revision that produced it. `queued`, `pending`, `skipped-required`, `neutral-required`, `absent`, `cancelled`, `failed`, stale-head, predecessor-head, old-base, status-only, or synthetic-merge-only evidence is not equivalent to an accepted literal-head gate when literal-head evidence is required.

## 2. Runtime and Build Requirements

### 2.1 Baseline

- Java runtime/compiler: 25.
- Build: Maven Wrapper from the repository; root reactor is authoritative.
- Spring Boot baseline: 3.5.9.
- Spring Cloud baseline: 2025.0.1.
- Debezium API/Embedded/PostgreSQL connector baseline: 3.4.0.Final.
- PostgreSQL is the production ETL target and PostgreSQL logical replication is the shipped CDC source type.
- Kafka is the current live CDC publication transport.

Dependency versions are source-controlled in Maven metadata. Documentation never overrides the effective build.

### 2.2 Standalone/MSA contract

Each service must remain runnable with its own documented environment/configuration. A composed deployment may add Gateway, Eureka, Config Server, Kafka, PostgreSQL, and tracing, but no feature may silently require an unrelated service merely because the full compose topology contains it.

## 3. Service Contracts

### 3.1 ETL Service — `implemented_on_develop`

#### Synchronous endpoint

`POST /api/etl/process` is a **bounded atomic** request boundary:

1. enforce UTF-8 byte and batch-record limits;
2. parse with duplicate-field detection;
3. validate every record and identifier;
4. deterministically transform the entire batch;
5. only after successful prevalidation issue JDBC writes;
6. commit all accepted rows in one Spring transaction or roll back the batch.

The implementation must not use one-task-per-record fan-out or the JVM common pool for this path. Only `TransientDataAccessException`-class failures are eligible for the configured bounded retry.

#### Principal-scoped idempotency

Optional `Idempotency-Key` processing must:

- require an authenticated principal;
- accept the current bounded safe raw representation and RFC 9651 quoted String normalization used by production code;
- derive the stored key from principal scope + normalized key rather than store either raw value;
- hash the exact payload for same-intent detection;
- acquire a transaction-lifetime nonblocking request lock;
- replay a committed same-digest response without target writes;
- reject in-progress, changed-payload, malformed-key, or missing-principal cases with stable typed errors;
- commit target writes and `etl_idempotency_records` response evidence in one transaction.

#### Durable intake

When `xtrmetl.etl.jobs.intake-enabled=true` (or the supported preferred alias if configured by the environment layer), `EtlJobController` exposes:

- `POST /api/etl/jobs`;
- `GET /api/etl/jobs/{job_record_id}`.

The protected baseline is intake/status only. `etl_job_records` lifecycle is `PENDING|RUNNING|SUCCEEDED|FAILED`; there is no protected-develop lease-fenced worker, pagination, polling advice, conditional ETag, cancellation, or replay implementation.

### 3.2 CDC Service — `implemented_on_develop` with known gaps

The CDC service must:

- configure embedded Debezium from validated deployment input;
- use a dedicated engine executor;
- expose start/stop/status/source/target HTTP surfaces;
- preserve raw Debezium key/value JSON compatibility on the live Kafka path;
- persist offsets/schema history through configured Debezium storage;
- expose operator-safe, finite-cardinality status without secrets.

`known_gap`: `handleChangeEvent` currently submits Kafka work without waiting for acknowledgement; PR #139 is the `active_pr` remediation.

`known_gap`: `stop()` currently requests close and clears engine/task references before proving asynchronous task completion; issue #141 is `planned` remediation.

### 3.3 Gateway — current known gap

Protected develop has a class named `JwtAuthenticationFilter`, but its validator accepts a literal example token. This is not a valid production resource-server trust boundary. PR #142 is `active_pr` and must integrate before documentation changes the capability to shipped OAuth 2.0 Resource Server JWT validation.

### 3.4 Infrastructure services

- Gateway default port: 8080.
- ETL Service default port: 8000.
- CDC Service default port: 8001.
- Eureka default port: 8761.
- Config Server default port: 8888.
- Zipkin default port: 9412 when enabled.

## 4. Persistence Requirements

### 4.1 Naming

Owned database objects use descriptive names of at least two words and snake_case by default. Legacy objects that violate the current policy require an explicit compatibility/migration plan rather than silent rename.

### 4.2 `processed_data`

The local compose target bootstrap creates `processed_data`; synchronous ETL currently inserts transformed payload text through parameterized JDBC.

### 4.3 `etl_idempotency_records`

Required protected-develop columns:

- `idempotency_key_hash` — SHA-256 hex primary key;
- `request_digest` — SHA-256 exact payload digest;
- `response_body` — committed replay representation;
- `created_at` — durable creation timestamp.

Raw principal names, raw idempotency keys, and request payloads must not be stored here.

### 4.4 `etl_job_records`

Required protected-develop columns:

- `job_record_id`;
- `principal_scope_hash`;
- `submission_key_hash`;
- `request_digest`;
- `request_payload` while active;
- `job_status`;
- `attempt_count`;
- `failure_code`;
- `created_at` / `updated_at`.

Lifecycle constraints must keep active-state payload presence consistent and reject unsupported status values.

### 4.5 Active-PR migrations

Lease, owner-pagination index, cancellation, replay-lineage, and replay-index migrations belong to the durable-stack active PRs. They are not merged persistence until their predecessor order and direct-base evidence are satisfied.

## 5. HTTP and Error Requirements

- HTTP semantics follow RFC 9110 where the product defines status/header behavior.
- Typed ETL API failures follow RFC 9457 and must not include raw internal exception, SQL, secret, payload, or principal data.
- Structured `Idempotency-Key` normalization is aligned to RFC 9651 String syntax while retaining the documented compatibility representation.
- `202 Accepted` durable submission is intentionally noncommittal about worker completion.
- Owner-scoped durable status is `Cache-Control: no-store`.
- A current or future `ETag`/`If-None-Match` implementation remains `active_pr` until PR #146 integrates.

## 6. Security and Privacy Requirements

- Default deny at production trust boundaries.
- No hard-coded production bearer token, issuer, key, or client credential.
- No raw credential, token, key, SQL, exception text, or secret in ordinary logs/metrics/client problems.
- PII required for legitimate ETL/CDC use is not blanket-masked. Use purpose-bound authorization, data minimization, encryption, retention limits, and auditable privileged access.
- Agent/model execution never receives a broader GitHub authority simply because deterministic publication is needed.
- Workflow dependencies and installation artifacts are immutable/checksum-bound where the repository policy requires it.
- Use NIST SSDF 1.1 as the finalized secure-development reference; draft successors may inform future changes but do not replace the final baseline without an explicit ADR.

## 7. Observability and Reliability Requirements

- Micrometer/Actuator/Zipkin or OpenTelemetry-compatible telemetry must use finite-cardinality dimensions for resource state.
- New cross-service telemetry should adopt OpenTelemetry semantic conventions where applicable.
- No job ID, principal, raw key, payload, lease token, SQL, exception message, or unbounded connector identifier may become an uncontrolled metric label.
- `isRunning`, health, status, and stop responses must represent the lifecycle state they actually prove.
- Crash/restart boundaries must document at-least-once or replay tolerance rather than claim end-to-end exactly once without proof.

## 8. CI, Test, and Coverage Requirements

### 8.1 TDD

Every production defect or new behavior begins with a deterministic RED that reaches the intended production boundary. Import/setup/fixture failures are test defects, not valid RED evidence.

### 8.2 Coverage

Owned production code requires zero missed configured statements/lines/methods/branches. Public production APIs require complete beginner-readable documentation. A coverage report is accepted only from the same source revision under review.

### 8.3 Platform matrix

The Maven reactor must pass on the supported GitHub-hosted OS matrix (Ubuntu, macOS, Windows) for a merge-eligible source head. Conditional self-hosted runs are useful only when actually executed; a skipped conditional job is not positive evidence.

### 8.4 exact-head source identity

Protected develop currently uses default checkout on `pull_request`, so GitHub may run source-executing jobs on the generated merge ref. GitHub documents that `actions/checkout` defaults to `GITHUB_REF`, which is the pull-request merge branch for this event. Therefore aggregate green on current protected workflows may be compatibility-preview evidence but is **synthetic-merge** evidence, not literal source-head evidence.

PR #121 is `active_pr` and carries explicit source-head checkout/verification for CI/SBOM plus scheduler authority separation. Until integrated, its controls must not be described as protected-develop behavior.

### 8.5 Required evidence inventory

Before protected merge, evaluate as applicable:

- exact source CI/test/coverage;
- Dependency Review;
- SBOM (CycloneDX);
- SAST/Semgrep and other configured code scanning;
- hard security scanner source identity;
- unresolved security/review threads;
- commit statuses;
- migration/rollback and compatibility evidence;
- independent non-author formal approval where explicit CWL/mightyETL governance requires it.

Queued, pending, missing, skipped-required, stale or predecessor evidence is non-passing.

## 9. Autonomous Maintenance Technical Contract

The hourly NVIDIA OpenCode scheduler is `active_pr` in #121 and is not yet protected-develop runtime. Its intended authority topology is:

- model/source-reading job: read-only GitHub authority;
- deterministic branch publisher: isolated `contents: write` only;
- deterministic PR publisher: isolated `pull-requests: write` only;
- exact-head run authorizer: isolated `actions: write` only;
- independent review and merge: separate authorities.

`NVIDIA_NIM_API_KEY` is the model credential. `COPILOT_GITHUB_TOKEN` is not an autonomous-development credential.

A branch write requiring an exact parent uses branch-wide compare-and-swap semantics. File-level Contents API SHA alone does not prove the branch parent was unchanged. Prefer trusted checkout or Git Data tree/commit publication followed by non-forced (`force=false`) ref movement after an immediate live-ref reread.

A writer conflict freezes only the affected branch for that invocation. It does not justify repository-wide idle time while another safe lane exists.

## 10. Stack and Compatibility Requirements

- A stacked PR head must descend from the exact live head of its immediate predecessor.
- Old checks, approvals, reviews, and base snapshots do not transfer to replacement branches.
- Destructive force push, `-X ours`, `-X theirs`, or rewritten fail-first evidence are prohibited as ancestry repair mechanisms.
- Downstream stack work does not become merge-eligible merely because the local PR is mergeable.
- Public HTTP/connector/persistence changes require compatibility and rollback evidence.

## 11. Packaging, Provenance, and Release

A release is permitted only from the integrated protected head with all required quality, security, coverage, compatibility, migration, SBOM/provenance, review, and release-acceptance gates satisfied. `CHANGELOG.md` must move relevant Unreleased entries to the versioned release. Published artifacts must be verified after publication.

## 12. Documentation Requirements

Canonical families are:

- `PRD.md`;
- `TRD.md`;
- `ARCHITECTURE.md`;
- `SECURITY.md`;
- `docs/adr/README.md` and ADRs;
- `docs/UML.md`;
- `docs/ERD.md`;
- `docs/API_CONTRACT.md`;
- `docs/THREAT_MODEL.md`;
- `docs/TEST_STRATEGY.md`;
- `docs/OPERABILITY.md`;
- `docs/TRACEABILITY.md`.

Changes to public API, persisted state, lifecycle, trust boundary, deployment, scheduler authority, or release gates update the affected canonical families in the same PR.

## 13. References

Debezium. (2026). *Debezium Engine 3.4*. Debezium Documentation. https://debezium.io/documentation/reference/3.4/development/engine.html

GitHub. (2026). *Events that trigger workflows*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows

Nottingham, M., & Kamp, P.-H. (2024). *Structured Field Values for HTTP* (RFC 9651). RFC Editor. https://www.rfc-editor.org/info/rfc9651

Nottingham, M., Wilde, E., & Dalal, S. (2023). *Problem Details for HTTP APIs* (RFC 9457). RFC Editor. https://www.rfc-editor.org/info/rfc9457

Souppaya, M., Scarfone, K., & Dodson, D. (2022). *Secure Software Development Framework (SSDF) Version 1.1: Recommendations for Mitigating the Risk of Software Vulnerabilities* (NIST SP 800-218). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-218
