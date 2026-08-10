# Requirement, Decision, Implementation, and Evidence Traceability

**Protected baseline:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Last reconciled:** 2026-08-10

This matrix prevents chat history, issue bodies, or active PR descriptions from silently becoming product truth.

## 1. Status taxonomy

- `implemented_on_develop`
- `active_pr`
- `planned`
- `superseded`
- `out_of_scope`
- `known_gap`

A capability changes status only after its authoritative source, persistence, API, operational, or release boundary changes and the canonical documents are updated on the same integration path.

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
| direct ETL service authentication | `known_gap` issue #161 | protected ETL service remains independently reachable and uses local HTTP Basic without a supported service-identity/token-relay contract from the gateway | authenticated east-west/direct-service boundary requires purpose-bound service authentication and runtime integration evidence | SECURITY, THREAT_MODEL, issue #161 |
| target connector lifecycle/catalog | `implemented_on_develop` | `TargetConnectorDispatcher`, `GET /api/etl/connectors` | connector lifecycle/catalog tests | ADR-0007, connector docs |
| any-to-any canonical CDC | `planned` | partial registry/mapper scaffold; live path remains raw PostgreSQL→Kafka | mapper/SPI tests prove scaffold only | `docs/cdc/any-to-any-cdc.md` |
| legacy local-auth bootstrap retirement | `active_pr` #155 | default Docker PostgreSQL init plus explicit compatibility artifact | `LegacyAuthBootstrapRetirementTest` and exact-head PR evidence | issue #150, ERD/data-governance follow-through |
| Qlik row-write scaffold removal from production discovery | `active_pr` #156 | target registry and production configuration binding are removed on branch | registry/catalog/config retirement tests | issue #153, ADR-0007 |
| machine-readable OpenAPI and AsyncAPI contracts | `active_pr` #157 | checked-in HTTP/Kafka contract artifacts on branch | `MachineReadableApiContractTest`; validator/schema proof still required before merge | issue #152, API contract |
| MySQL CDC scaffold removal from production discovery | `active_pr` #158 | MySQL reference scaffold loses Spring production discovery | `CdcSourceRegistryTest`; shared Jackson security failure tracked separately | issue #153, ADR-0007 |
| shared Jackson security baseline | `active_pr` #160 | direct-develop Maven dependency management imports Jackson 2.21.5 BOM before Spring Boot | `JacksonSecurityBaselineTest`; CVE-2026-54515, CVE-2026-59889, GHSA-mhm7-754m-9p8w must disappear from accepted security evidence | `docs/doctoring/jackson-2.21.5-security-baseline.md` |
| non-vacuous durable-job coverage gate | `known_gap` issue #162 | protected JaCoCo plugin-level dotted include patterns are reused where report/check expect class-file filters | hosted CI logged `Analyzed bundle with 0 classes` and still passed JaCoCo checks; do not claim 100% coverage from this control | issue #162, `docs/TEST_STRATEGY.md` |
| non-vacuous coverage repair | `active_pr` #164 | direct-`develop` JaCoCo report/check use class-file filters plus a BUNDLE class-count invariant | current PR evidence analyzes eight production classes; merge acceptance still requires accepted source identity and review | issue #162, `docs/TEST_STRATEGY.md` |
| SQL Server CDC scaffold retirement | `active_pr` #163 | SQL Server reference scaffold loses Spring production discovery | factory/registry tests prove configured use reports `unknown_source_type`; active PR is not shipped truth | issue #153, ADR-0007 |
| release artifact provenance | `planned` issue #165 | no protected release/provenance acceptance implementation yet | exact integrated protected head plus artifact/SBOM/provenance/reproducibility acceptance required | release/provenance authority |
| bundled Zipkin transport repair | `active_pr` #167 | Compose branch maps host 9412 to container 9411 and services use the internal 9411 endpoint | `DockerComposeZipkinTransportTest`; current feature evidence remains PR evidence until protected integration | issue #166, OPERABILITY |
| repository runtime supply-chain cleanup | `active_pr` #169 | `.replit` branch stops opaque JAR execution, mutable remote-script piping, and duplicate service delegates | `RepositoryRuntimeSupplyChainTest`; tracked root `zipkin.jar` cleanup remains issue #168 follow-through | issue #168, SECURITY/OPERABILITY |
| diagnostic confidentiality hardening | `active_pr` #170/#171/#172/#174/#176/#211 | controller, loader, CDC, parser, and DLT boundaries replace raw provider/JDBC/DDL/row/parser/exception diagnostics with stable non-sensitive contracts | focused RED→GREEN error-contract tests on each active branch; no active PR is shipped truth | SECURITY, THREAT_MODEL, API contract |
| Flyway-only schema mutation authority | `active_pr` #184 | ETL production JPA schema mutation is disabled so checked-in Flyway migrations remain the intended schema authority | `FlywaySchemaAuthorityTest`; synthetic-merge CI is supplementary, not literal-head proof | schema/recovery ADR follow-through |
| explicit Config Server repository authority | `active_pr` #189 | Config Server startup no longer falls back silently to an example repository; repository authority becomes explicit and fail-closed | configuration contract tests and startup acceptance remain PR-local | Architecture, SECURITY, OPERABILITY |
| runtime identifier compatibility inventory | `active_pr` #191 | runtime, Kafka, Debezium, configuration, and state identifiers are inventoried before `xtrmETL`→`mightyETL` migration | compatibility inventory and migration doctoring; no rename is shipped until protected integration | migration/compatibility authority |
| dead-letter privacy and terminal routing | `active_pr` #192/#197 | DLT diagnostic content is bounded/non-sensitive and replica application treats DLT records as terminal rather than re-entering the normal apply path | DLT confidentiality and terminal-routing regression tests | SECURITY, data-governance/replay authority |
| invalid amount fail-closed integrity | `active_pr` #199 | invalid amount-like values fail closed instead of silently corrupting or coercing target records | deterministic parser/transform boundary test | data-quality authority |
| CDC connector registry identity | `active_pr` #201 | duplicate connector identifiers are rejected rather than silently overwriting an implementation in the registry | duplicate-identity registry RED→GREEN test | ADR-0007, connector support matrix |
| PostgreSQL backup and restore provenance | `active_pr` #208 | logical backup bundle, manifest, exact source/version/migration identity, digest, atomic reservation, and clean-target restore rehearsal remain branch-owned | backup/restore contract tests; no RPO/RTO or disaster-recovery claim without measured protected evidence | OPERABILITY, recovery ADR follow-through |
| repository-wide owned-production coverage | `known_gap` issue #205 | current per-module controls do not yet prove that every owned production package is selected and measured by one repository-wide fail-closed inventory | issue acceptance must prove non-empty ownership inventory, exclusions, and aggregate statement/branch evidence | TEST_STRATEGY, release acceptance |
| Maven scanner dependency-graph completeness | `known_gap` issue #196 | current Trivy/Maven evidence can report green while warning that dependency versions or child dependencies could not be resolved | accepted scanner evidence must fail closed on incomplete dependency resolution and bind to exact source | SECURITY, TEST_STRATEGY, release evidence |
| structured record snapshot integrity | `active_pr` #222/#228 | structured transformation records are snapshotted at trust boundaries so later mutation or hostile map/object behavior cannot rewrite previously accepted intent | record/snapshot focused tests on active branches | data-integrity and concurrency authority |
| public bootstrap and environment API documentation | `active_pr` #224/#226/#230 | public bootstrap/environment/configuration surfaces receive beginner-readable API documentation without changing runtime behavior | docstring/Javadoc contracts plus full relevant tests; active PR documentation is not shipped truth | NFR-QUAL-2, API/operability docs |
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
| non-vacuous owned-production coverage | `known_gap` issue #162 | protected `etl-service` JaCoCo configuration; repair `active_pr` #164 | report/check must select the intended compiled class-file set and fail when that set is empty before 100% may be claimed |
| repository-wide coverage ownership | `known_gap` issue #205 | repository modules and generated/third-party boundaries require an explicit owned-code inventory | release evidence must prove complete non-empty owned scope, not only one selected class bundle |
| complete Maven dependency security graph | `known_gap` issue #196 | scanner/runtime dependency materialization | warnings that child dependencies or versions are unresolved invalidate a zero-finding success claim |

## 4. Conversation-to-repository reconciliation

| Durable conversation decision | Current status |
| --- | --- |
| reviews/check waits do not block unrelated work | external scheduler contract updated; #121 runtime implementation remains `active_pr` |
| RCA must lead to feasible remedy execution, not blocker narration | external scheduler contract updated; #121 contains runtime feasibility loop |
| every action is intermediate while safe work remains | external scheduler uses live queue, mid-run expansion and double exit sweep; embedded #121 follow-through is `planned` issue #154 |
| scheduler/task failure is a local control-plane symptom, not repository completion | issue #154 owns embedded-runtime alignment after its ancestry trigger; generic task error must hand back to fresh repository execution |
| practical run-budget exhaustion requires a clean atomic continuation, not a half-written branch | external scheduler uses budget-safe continuation; repository runtime alignment remains issue #154 and must not move #121 solely for wording |
| writer conflicts are branch-local, not repository-wide | scheduler contract + canonical ADR-0006 |
| central `.github`, naruon, contextual-orchestrator dedicated loops are read-only dependencies | scheduler contract + ADR-0006 |
| branch-wide exact-parent source publication | canonical ADR-0006; prefer Git Data + non-forced ref update and prove ancestry after any file-CAS fallback |
| no destructive stack rewriting | durable stack replacement PRs #143–#148 + ADR-0003 |
| durable jobs progress worker→pagination→polling→ETag→cancellation→replay | `active_pr` stack, never relabel shipped early |
| Kafka acknowledgement before offset progress | `active_pr` #139 |
| CDC stop must await actual task completion | `planned` issue #141 |
| gateway example token must be replaced by real Resource Server JWT | `active_pr` #142 |
| independently reachable ETL traffic requires a supported service-identity boundary | `known_gap` issue #161; do not assume gateway-only reachability |
| default clean installs must stop recreating abandoned local-auth persistence | `active_pr` #155; existing-volume compatibility remains explicit and non-destructive |
| scaffold connectors must be productionized or removed from production discovery | `active_pr` #156/#158/#163 plus issue #153 for remaining connectors |
| public HTTP/event contracts need machine-readable artifacts | `active_pr` #157; active-PR routes must not be promoted to protected truth |
| inherited Jackson findings must be fixed at the shared dependency boundary | `active_pr` #160 uses Jackson 2.21.5 LTS BOM; no CVE suppression or feature-branch duplication |
| 100% coverage claims must fail closed on an empty production target set | `known_gap` issue #162; repair is `active_pr` #164 and is no longer sequenced behind #157 |
| repository-wide coverage must prove the complete owned production inventory | `known_gap` issue #205; an eight-class focused gate is necessary but not sufficient for repository-wide release evidence |
| scanner success requires a complete resolved dependency graph | `known_gap` issue #196; zero findings with unresolved Maven versions/children is not accepted security evidence |
| bundled tracing must use Zipkin's real internal collector port while preserving an explicit host compatibility contract | `active_pr` #167; not shipped until protected integration |
| repository launch paths must not execute opaque binaries or mutable remote scripts as trusted bootstrap | `active_pr` #169 plus issue #168 follow-through |
| production schema mutation must have one authority | `active_pr` #184 selects Flyway over JPA auto-DDL; not protected truth until merged |
| recovery claims require exact backup/restore provenance and measured operational proof | `active_pr` #208; external Kafka/Debezium/DLT/warehouse effects remain separate recovery domains |
| DLT payloads and diagnostics require explicit privacy/retention/terminal-routing authority | `active_pr` #192/#197; not shipped until protected integration |
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
- `out_of_scope`: claiming disaster recovery, RPO, or RTO from a backup artifact without destructive-loss restore rehearsal and measured protected operational evidence.

## 6. Update rule

A PR that changes any row's implementation/status must update this matrix and the relevant canonical PRD/TRD/Architecture/ADR/UML/ERD/API/Security/Operability documents before protected merge. A status-only edit that contradicts source, migration, runtime, or evidence identity is a documentation defect. Newly opened material PRs/issues must be reconciled during the next stable documentation update rather than silently omitted, and exact SHAs/run IDs belong in dated evidence rather than timeless architecture claims.
