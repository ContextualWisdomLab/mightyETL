# Requirement, Decision, Implementation, and Evidence Traceability

**Protected baseline:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Last reconciled:** 2026-08-09

This matrix prevents chat history, issue bodies, or active PR descriptions from silently becoming product truth.

## 1. Status taxonomy

- `implemented_on_develop`
- `active_pr`
- `planned`
- `superseded`
- `out_of_scope`
- `known_gap`

A capability changes status only after its authoritative source/persistence/API boundary changes and the canonical docs are updated on the same integration path.

## 2. Core product traceability

| Capability / requirement | Status | Source / persistence | Tests / evidence | Decision / docs |
| --- | --- | --- | --- | --- |
| bounded whole-batch ETL admission | `implemented_on_develop` | `EtlService`, `EtlBatchProperties` | batch safety, controller/service tests | ADR-0002, `docs/etl/bounded-atomic-batches.md` |
| atomic synchronous target transaction | `implemented_on_develop` | `EtlService.processData` | transaction integration/rollback tests | ADR-0002 |
| RFC 9457 ETL error taxonomy | `implemented_on_develop` | `EtlApiProblemHandler`, `EtlRequestError` | problem handler/docs tests | `docs/api/problem-details.md`, API contract |
| principal-scoped Idempotency-Key | `implemented_on_develop` | `EtlService.processDataIdempotently`, V1 `etl_idempotency_records` | idempotency + concurrency + rollback | ADR-0002, `docs/etl/idempotent-retries.md` |
| durable asynchronous intake/status | `implemented_on_develop` | `EtlJobController`, `EtlJobService`, V2 `etl_job_records` | job service/controller/migration tests | ADR-0003, `docs/etl/durable-job-intake.md` |
| lease-fenced worker | `active_pr` #143 | repaired worker branch/migrations | exact-head PR evidence only | ADR-0003, UML active overlay |
| owner-scoped keyset pagination | `active_pr` #144 | repaired pagination branch | PR-local exact-head evidence | ADR-0003 |
| Retry-After polling advice | `active_pr` #145 | `EtlJobPollingAdvice` on branch | PR-local exact-head evidence | API/UML active overlay |
| conditional weak ETag status | `active_pr` #146 | controller branch | PR-local exact-head evidence | API/UML active overlay |
| owner cancellation / CANCELLED | `active_pr` #147 | V6 + cancellation service/controller on branch | migration/concurrency/controller/doc tests | ADR-0003, ERD/UML active overlay |
| terminal replay with lineage | `active_pr` #148 | replacement replay branch | must be regenerated on replacement | ADR-0003, ERD active overlay |
| Kafka acknowledgement before Debezium progress | `active_pr` #139 | CDC branch | acknowledgement/timeout tests | ADR-0004 |
| graceful CDC stop completion | `planned` issue #141 | protected `CdcService.stop()` remains early-clear | required deterministic Future/latch RED not integrated | ADR-0004, OPERABILITY |
| production JWT Resource Server | `active_pr` #142 | gateway replacement branch | registered-chain runtime tests | ADR-0005 |
| protected gateway current state | `known_gap` | `JwtAuthenticationFilter` literal `valid_token` | placeholder tests only | ADR-0005, THREAT_MODEL |
| target connector lifecycle/catalog | `implemented_on_develop` | `TargetConnectorDispatcher`, `GET /api/etl/connectors` | connector lifecycle/catalog tests | ADR-0007, connector docs |
| any-to-any canonical CDC | `planned` / partial scaffold | registry/mapper scaffold; live path remains raw PostgreSQL→Kafka | mapper/SPI tests | `docs/cdc/any-to-any-cdc.md` |

## 3. CI, security, and automation traceability

| Control | Status | Source / owner | Evidence contract |
| --- | --- | --- | --- |
| protected-develop default PR checkout | `implemented_on_develop` | `.github/workflows/ci.yml` | generated merge-ref source is possible; do not label literal-head |
| literal-head CI/SBOM | `active_pr` #121 | mightyETL branch | explicit head checkout + SHA assertion |
| literal-head hard central scanner | `read_only_dependency` represented as `planned` from mightyETL perspective | ContextualWisdomLab/.github dedicated loop | no central mutation from this writer |
| hourly OpenCode development | `active_pr` #121 | mightyETL | model read-only; deterministic writers separated |
| NVIDIA model credential | `active_pr` #121 | `NVIDIA_NIM_API_KEY` | never substitute `COPILOT_GITHUB_TOKEN` |
| independent review | governance gate | repository/organization policy | formal non-author APPROVED only where required |
| branch-wide writer CAS | operating contract | scheduler/publisher | exact live parent + prepared descendant + `force=false` ref update |

## 4. Conversation-to-repository reconciliation

| Durable conversation decision | Current status |
| --- | --- |
| reviews/check waits do not block unrelated work | scheduler automation prompt updated; #121 runtime implementation remains `active_pr` |
| RCA must lead to feasible remedy execution, not blocker narration | scheduler automation prompt updated; #121 contains runtime feasibility loop |
| writer conflicts are branch-local, not repository-wide | scheduler automation prompt updated; canonical ADR-0006 |
| central `.github`, naruon, contextual-orchestrator dedicated loops are read-only dependencies | scheduler automation prompt + ADR-0006 |
| branch-wide exact-parent source publication | canonical ADR-0006; use Git Data + non-forced ref update |
| no destructive stack rewriting | durable stack replacement PRs #143–#148 + ADR-0003 |
| durable jobs progress worker→pagination→polling→ETag→cancellation→replay | `active_pr` stack, never relabel shipped early |
| Kafka acknowledgement before offset progress | `active_pr` #139 |
| CDC stop must await actual task completion | `planned` issue #141 |
| gateway example token must be replaced by real Resource Server JWT | `active_pr` #142 |
| standalone and MSA both matter | ADR-0007 + Architecture |
| PII masking cannot destroy operational utility | ADR-0008 + Security/Threat Model |
| canonical docs must carry ADR/PRD/TRD/UML/ERD truth | this PR #149 |

## 5. Superseded / out-of-scope claims

- `superseded`: local `/auth/signup` and `/auth/signin` product design. Legacy compose `users`/`roles` persistence remains but does not expose those APIs.
- `superseded`: per-record CompletableFuture fan-out for synchronous ETL.
- `superseded`: old durable branches replaced by non-destructive repaired stack branches; old checks/reviews do not transfer.
- `out_of_scope` for protected baseline: claiming end-to-end exactly-once across remote warehouses/APIs/brokers without connector-specific proof.
- `out_of_scope`: using GitHub Copilot/COPILOT_GITHUB_TOKEN as the autonomous development agent credential.

## 6. Update rule

A PR that changes any row's implementation/status must update this matrix and the relevant canonical PRD/TRD/Architecture/ADR/UML/ERD/API/Security/Operability documents before protected merge. A status-only edit that contradicts source or migration evidence is a documentation defect.
