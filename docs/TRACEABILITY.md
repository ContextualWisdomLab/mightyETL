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
| terminal replay with lineage | `active_pr` #148 | replacement replay branch | exact-head evidence must be regenerated after each head change | ADR-0003, ERD active overlay |
| Kafka acknowledgement before Debezium progress | `active_pr` #139 | CDC branch | acknowledgement/timeout tests | ADR-0004 |
| graceful CDC stop completion | `planned` issue #141 | protected `CdcService.stop()` remains early-clear | deterministic Future/latch RED required before source repair | ADR-0004, OPERABILITY |
| production JWT Resource Server | `active_pr` #142 | gateway replacement branch | registered-chain runtime tests | ADR-0005 |
| protected gateway current state | `known_gap` | `JwtAuthenticationFilter` literal `valid_token` | placeholder tests only | ADR-0005, THREAT_MODEL |
| target connector lifecycle/catalog | `implemented_on_develop` | `TargetConnectorDispatcher`, `GET /api/etl/connectors` | connector lifecycle/catalog tests | ADR-0007, connector docs |
| any-to-any canonical CDC | `planned` | partial registry/mapper scaffold; live path remains raw PostgreSQL→Kafka | mapper/SPI tests prove scaffold only | `docs/cdc/any-to-any-cdc.md` |
| legacy local-auth bootstrap retirement | `active_pr` #155 | default Docker PostgreSQL init plus explicit compatibility artifact | `LegacyAuthBootstrapRetirementTest` and exact-head PR evidence | issue #150, ERD/data-governance follow-through |
| Qlik row-write scaffold removal from production discovery | `active_pr` #156 | target registry and production configuration binding are removed on branch | registry/catalog/config retirement tests | issue #153, ADR-0007 |
| machine-readable OpenAPI and AsyncAPI contracts | `active_pr` #157 | checked-in HTTP/Kafka contract artifacts on branch | `MachineReadableApiContractTest`; validator/schema proof still required before merge | issue #152, API contract |
| MySQL CDC scaffold removal from production discovery | `active_pr` #158 | MySQL reference scaffold loses Spring production discovery | `CdcSourceRegistryTest`; shared Jackson security failure tracked separately | issue #153, ADR-0007 |
| shared Jackson security baseline | `active_pr` #160 | direct-develop Maven dependency management imports Jackson 2.21.5 BOM before Spring Boot | `JacksonSecurityBaselineTest`; CVE-2026-54515, CVE-2026-59889, GHSA-mhm7-754m-9p8w must disappear from accepted security evidence | `docs/doctoring/jackson-2.21.5-security-baseline.md` |
| non-vacuous durable-job coverage gate | `known_gap` issue #162 | protected JaCoCo plugin-level dotted include patterns are reused where report/check expect class-file filters | hosted CI logged `Analyzed bundle with 0 classes` and still passed JaCoCo checks; repair is sequenced after overlapping #157 POM work stabilizes | issue #162, `docs/TEST_STRATEGY.md` |
| canonical documentation spine | `active_pr` #149 | PRD/TRD/Architecture/ADR/UML/ERD/API/Security/Test/Operability/Traceability branch | canonical + live commercial documentation contract tests | ADR-0001 |
| live documentation coverage and traceability closure | `planned` issue #159 | no protected implementation; follow-through tracker for post-#149 drift | source-backed documentation consistency acceptance | `docs/DOCUMENTATION_ASSESSMENT.md` |
| explicit repository licensing/copyright policy | `planned` issue #151 | no authorized root license decision on protected baseline | owner/legal/product decision plus packaging/SBOM evidence required | acquisition-diligence boundary |

## 3. CI, security, and automation traceability

| Control | Status | Source / owner | Evidence contract |
| --- | --- | --- | --- |
| protected-develop default PR checkout | `implemented_on_develop` | `.github/workflows/ci.yml` | generated merge-ref source is possible; do not label literal-head |
| literal-head CI/SBOM | `active_pr` #121 | mightyETL branch | explicit head checkout + SHA assertion |
| literal-head hard central scanner | `planned` | read-only dependency owned by ContextualWisdomLab/.github dedicated loop | no central mutation from this writer; synthetic filesystem scanning is not literal-head proof |
| hourly OpenCode development | `active_pr` #121 | mightyETL | model read-only; deterministic writers separated |
| NVIDIA model credential | `active_pr` #121 | `NVIDIA_NIM_API_KEY` | never substitute `COPILOT_GITHUB_TOKEN` |
| independent counted review route | `known_gap` | repository/CWL governance plus read-only central reviewer routing | formal non-author APPROVED only where required; current autonomous route must be proven operational |
| branch-wide writer CAS | `active_pr` #121 | deterministic publisher / scheduler operating contract | exact live parent + prepared descendant + `force=false` ref update; file-CAS fallback requires final ancestry proof |
| non-vacuous owned-production coverage | `known_gap` issue #162 | `etl-service` JaCoCo configuration | report/check must select the intended compiled class-file set and fail when that set is empty before 100% may be claimed |

## 4. Conversation-to-repository reconciliation

| Durable conversation decision | Current status |
| --- | --- |
| reviews/check waits do not block unrelated work | external scheduler contract updated; #121 runtime implementation remains `active_pr` |
| RCA must lead to feasible remedy execution, not blocker narration | external scheduler contract updated; #121 contains runtime feasibility loop |
| every action is intermediate while safe work remains | external scheduler uses live queue, mid-run expansion and double exit sweep; embedded #121 follow-through is `planned` issue #154 |
| writer conflicts are branch-local, not repository-wide | scheduler contract + canonical ADR-0006 |
| central `.github`, naruon, contextual-orchestrator dedicated loops are read-only dependencies | scheduler contract + ADR-0006 |
| branch-wide exact-parent source publication | canonical ADR-0006; prefer Git Data + non-forced ref update and prove ancestry after any file-CAS fallback |
| no destructive stack rewriting | durable stack replacement PRs #143–#148 + ADR-0003 |
| durable jobs progress worker→pagination→polling→ETag→cancellation→replay | `active_pr` stack, never relabel shipped early |
| Kafka acknowledgement before offset progress | `active_pr` #139 |
| CDC stop must await actual task completion | `planned` issue #141 |
| gateway example token must be replaced by real Resource Server JWT | `active_pr` #142 |
| default clean installs must stop recreating abandoned local-auth persistence | `active_pr` #155; existing-volume compatibility remains explicit and non-destructive |
| scaffold connectors must be productionized or removed from production discovery | `active_pr` #156/#158 plus issue #153 for remaining connectors |
| public HTTP/event contracts need machine-readable artifacts | `active_pr` #157; active-PR routes must not be promoted to protected truth |
| inherited Jackson findings must be fixed at the shared dependency boundary | `active_pr` #160 uses Jackson 2.21.5 LTS BOM; no CVE suppression or feature-branch duplication |
| 100% coverage claims must fail closed on an empty production target set | `known_gap` issue #162; current JaCoCo class-file selection can yield a vacuous pass and must be repaired after overlapping POM work stabilizes |
| licensing/copyright must be explicit before acquisition/release claims | `planned` issue #151; automation must not invent a license |
| standalone and MSA both matter | ADR-0007 + Architecture |
| PII masking cannot destroy operational utility | ADR-0008 + Security/Threat Model |
| canonical docs must carry ADR/PRD/TRD/UML/ERD truth and stay live after creation | `active_pr` #149 plus `planned` issue #159 |

## 5. Superseded / out-of-scope claims

- `superseded`: local `/auth/signup` and `/auth/signin` product design. Legacy compose `users`/`roles` persistence remains on protected develop but does not expose those APIs.
- `superseded`: per-record CompletableFuture fan-out for synchronous ETL.
- `superseded`: old durable branches replaced by non-destructive repaired stack branches; old checks/reviews do not transfer.
- `out_of_scope` for protected baseline: claiming end-to-end exactly-once across remote warehouses/APIs/brokers without connector-specific proof.
- `out_of_scope`: using GitHub Copilot/COPILOT_GITHUB_TOKEN as the autonomous development agent credential.

## 6. Update rule

A PR that changes any row's implementation/status must update this matrix and the relevant canonical PRD/TRD/Architecture/ADR/UML/ERD/API/Security/Operability documents before protected merge. A status-only edit that contradicts source or migration evidence is a documentation defect. Newly opened material PRs/issues must be reconciled during the next stable documentation update rather than silently omitted, and exact SHAs/run IDs belong in dated evidence rather than timeless architecture claims.
