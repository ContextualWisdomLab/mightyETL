# Requirement, Decision, Implementation, and Evidence Traceability

**Protected baseline:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Last reconciled:** 2026-08-10

This matrix prevents chat, issue bodies, PR bodies, statuses, synthetic merge previews, and active-branch implementation from silently becoming protected product truth.

## 1. Status taxonomy

- `implemented_on_develop`
- `active_pr`
- `planned`
- `superseded`
- `out_of_scope`
- `known_gap`

A capability changes status only after its source, persistence, API/event, security, operational, and evidence boundaries agree on the exact protected integration state.

## 2. Core product and commercial-readiness traceability

| Capability / requirement | Status | Source / evidence | Decision / docs |
| --- | --- | --- | --- |
| bounded whole-batch ETL admission | `implemented_on_develop` | `EtlService`, `EtlBatchProperties`, batch safety tests | ADR-0002 |
| atomic synchronous target transaction | `implemented_on_develop` | `EtlService.processData`, transaction rollback tests | ADR-0002 |
| RFC 9457 ETL error taxonomy | `implemented_on_develop` | `EtlApiProblemHandler`, `EtlRequestError` | API contract, ADR-0011 |
| principal-scoped Idempotency-Key | `implemented_on_develop` | V1 `etl_idempotency_records`, concurrency/rollback tests | ADR-0002 |
| durable asynchronous intake/status | `implemented_on_develop` | `EtlJobController`, `EtlJobService`, V2 `etl_job_records` | ADR-0003 |
| lease-fenced worker | `active_pr` #143 | repaired worker branch/migrations; exact-head evidence only | ADR-0003 |
| owner-scoped keyset pagination | `active_pr` #144 | repaired pagination branch | ADR-0003 |
| Retry-After polling advice | `active_pr` #145 | polling branch | ADR-0003, API/UML |
| conditional weak ETag status | `active_pr` #146 | status branch | ADR-0003, API/UML |
| owner cancellation / CANCELLED | `active_pr` #147 | V6 plus owner/concurrency/controller tests | ADR-0003 |
| terminal replay with lineage | `active_pr` #148 | replacement replay branch; predecessor evidence does not transfer | ADR-0003 |
| Kafka acknowledgement before Debezium progress | `active_pr` #139 | acknowledgement and bounded-timeout tests | ADR-0004 |
| graceful CDC stop completion | `planned` issue #141 | protected stop clears references before proven task completion | ADR-0004 |
| production JWT Resource Server | `active_pr` #142 | registered runtime security-chain tests | ADR-0005, ADR-0010 |
| protected gateway current state | `known_gap` | literal `valid_token` placeholder | ADR-0005 |
| direct ETL service authentication | `known_gap` issue #161 | ETL remains independently reachable with local HTTP Basic and no supported downstream service identity | ADR-0010 |
| Eureka registration/query identity | `planned` issue #185 | routing metadata is not authorization | ADR-0010 |
| CDC control-plane authentication | `planned` issue #187 | start/stop/status/discovery require separate operator/workload authority | ADR-0010 |
| target connector lifecycle/catalog | `implemented_on_develop` | `TargetConnectorDispatcher`, `GET /api/etl/connectors` | ADR-0007 |
| any-to-any canonical CDC | `planned` | registry/mapper scaffold; protected live path remains PostgreSQL Debezium→Kafka | connector docs |
| legacy local-auth bootstrap retirement | `active_pr` #155 | clean-install retirement plus explicit compatibility evidence | ADR-0014, ERD |
| Qlik row-write scaffold removal from production discovery | `active_pr` #156 | registry and configuration retirement tests | ADR-0007 |
| machine-readable OpenAPI and AsyncAPI contracts | `active_pr` #157 | checked-in schemas and contract tests | API contract |
| MySQL CDC scaffold removal from production discovery | `active_pr` #158 | registry/factory retirement tests | ADR-0007 |
| SQL Server CDC scaffold retirement | `active_pr` #163 | registry/factory retirement tests | ADR-0007 |
| shared Jackson security baseline | `active_pr` #160 | Jackson 2.21.5 BOM; CVE-2026-54515, CVE-2026-59889, GHSA-mhm7-754m-9p8w remain acceptance subjects | ADR-0012 |
| non-vacuous durable-job coverage gate | `known_gap` issue #162 | protected JaCoCo reported `Analyzed bundle with 0 classes`; report/check require class-file selection | ADR-0012, Test Strategy |
| non-vacuous coverage repair | `active_pr` #164 | non-empty BUNDLE class-count invariant and selected class-file filters | ADR-0012 |
| repository-wide owned-production coverage | `known_gap` issue #205 | focused bundle cannot prove every owned module/package | ADR-0012 |
| Maven scanner dependency-graph completeness | `known_gap` issue #196 | unresolved versions/children invalidate zero-finding success | ADR-0012 |
| release artifact provenance | `planned` issue #165 | exact source/artifact/SBOM/provenance/reproducibility/publication/rollback acceptance absent | ADR-0012 |
| explicit repository licensing/copyright policy | `planned` issue #151 | no eligible owner-authorized root license decision | ADR-0012 |
| bundled Zipkin transport repair | `active_pr` #167 | host 9412 compatibility with internal collector 9411 | Operability |
| repository runtime supply-chain cleanup | `active_pr` #169 | unsafe opaque/mutable launch paths removed on branch | ADR-0012, ADR-0013 |
| diagnostic confidentiality hardening | `active_pr` #170/#171/#172/#174/#176/#211 | controller, JDBC, parser, DDL, row, CDC, and DLT stable non-sensitive contracts | ADR-0011 |
| Flyway-only schema mutation authority | `active_pr` #184 | production JPA schema mutation disabled on branch | ADR-0009 |
| explicit Config Server repository authority | `active_pr` #189 | no example/fallback repository authority | ADR-0010 |
| runtime identifier compatibility inventory | `active_pr` #191 | package/config/Kafka/Debezium/state identifiers classified before migration | ADR-0013 |
| dead-letter privacy and terminal routing | `active_pr` #192/#197 | bounded diagnostics and terminal quarantine routing tests | ADR-0011 |
| invalid amount fail-closed integrity | `active_pr` #199 | invalid amount-like values reject rather than coerce | data-quality contract |
| CDC connector registry identity | `active_pr` #201 | duplicate connector identifiers fail closed | ADR-0007 |
| PostgreSQL backup and restore provenance | `active_pr` #208 | source/database/Flyway/digest-bound bundle and clean-target restore rehearsal | ADR-0009 |
| structured record snapshot integrity | `active_pr` #222/#228 | mutable/hostile record boundaries snapshot accepted intent | data-integrity contract |
| public bootstrap and environment API documentation | `active_pr` #224/#226/#230 | beginner-readable public API/Javadoc contracts | quality contract |
| synchronous ETL UTF-8 text representation | `active_pr` #236 | exact MVC RED→GREEN for `text/plain;charset=UTF-8`; synthetic CI only | API contract |
| canonical documentation spine | `active_pr` #149 | PRD/TRD/Architecture/ADR/UML/ERD/API/Security/Test/Operability/Traceability and contract tests | ADR-0001 |
| live documentation coverage and traceability closure | `planned` issue #159 | protected integration and continuing machine-checkable reconciliation | ADR-0001 |

## 3. Cross-cutting decision traceability

| Durable authority | Status | Governing ADR | Current implementation / gap |
| --- | --- | --- | --- |
| schema mutation, migration, backup, restore, and recovery | `active_pr` / `known_gap` | ADR-0009 | PR #184 and PR #208; destructive-loss application/external-effect proof and measured RPO/RTO absent |
| gateway, direct-service, CDC, registry, Config Server, and operator identity | `active_pr` / `planned` / `known_gap` | ADR-0010 | PR #142/#189; issue #161/#185/#187; no inherited authentication |
| diagnostic confidentiality, DLT quarantine, retention, deletion, and redrive | `active_pr` | ADR-0011 | PR #170/#171/#172/#174/#176/#192/#197/#211 |
| exact, complete, non-vacuous quality/security/review/release evidence | `known_gap` / `active_pr` / `planned` | ADR-0012 | PR #121/#164; issue #151/#162/#165/#196/#205 |
| runtime identifiers and stateful compatibility migration | `active_pr` | ADR-0013 | PR #191 inventory; no bulk rename or state migration shipped |
| tenancy and data lifecycle | `planned` / `known_gap` | ADR-0014 | issue #186; principal scoping is not tenant isolation; one-tenant-per-deployment vs shared runtime remains Proposed |

## 4. CI, security, review, and release evidence

| Control | Status | Evidence authority |
| --- | --- | --- |
| protected default PR checkout | `implemented_on_develop` | may execute generated synthetic merge; not literal source by inference |
| literal-head CI/SBOM | `active_pr` #121 | explicit source checkout and SHA assertion |
| branch-wide writer CAS | `active_pr` #121 | exact live parent plus prepared descendant and `force=false` ref update |
| selected-bundle non-vacuity | `known_gap` issue #162 / `active_pr` #164 | selected production class set must be non-empty |
| repository-wide coverage ownership | `known_gap` issue #205 | every owned production module/package must be inventoried and measured |
| complete Maven dependency security graph | `known_gap` issue #196 | unresolved versions/children are non-passing |
| independent counted review | `known_gap` | formal non-author exact-head APPROVED only where governance requires it |
| release/legal authority | `planned` issue #151/#165 | exact protected artifact, licensing/NOTICE, provenance, publication and rollback evidence |

Checks, statuses, model judgments, security scanners, Dependency Review, SBOM, formal review, merge, protected runtime, artifact, provenance, licensing, and publication are separate evidence channels. A green aggregate does not substitute for a missing or incomplete subject-specific gate.

## 5. Conversation-to-repository reconciliation

| Durable conversation decision | Current status |
| --- | --- |
| waiting on review/check/provider blocks only the affected lane | external scheduler contract; PR #121 runtime remains `active_pr` |
| RCA must produce distinct feasible remedies and exact proof | PR #121 runtime and issue #154 follow-through |
| a prompt/docs update is never invocation completion | issue #154 durable scheduler-incident contract |
| generic scheduled-task failure is a control-plane symptom, not product completion | issue #154; internal exception must not be fabricated |
| practical budget exhaustion requires clean continuation, not a half-written branch | issue #154; no temporary writer or knowingly broken non-test-only head |
| writer conflict is branch-local | ADR-0006 |
| file SHA is not branch-parent CAS | ADR-0006; prefer exact-parent Git Data plus non-forced ref update |
| no destructive stack rewriting or old-evidence transfer | ADR-0003 and repaired #143→#148 stack |
| standalone and modular MSA operation both remain supported | ADR-0007 |
| PII controls preserve legitimate ETL utility | ADR-0008 and ADR-0011 |
| Flyway is the production schema authority | ADR-0009; implementation `active_pr` #184 |
| no trust boundary inherits another boundary's authentication | ADR-0010 |
| dead-letter records are terminal quarantine | ADR-0011 |
| a green aggregate is not release authority | ADR-0012 |
| runtime identifiers migrate by semantic category | ADR-0013 |
| principal scoping is not tenant isolation | ADR-0014 Proposed |
| active PRs are never shipped product truth | ADR-0001 and this matrix |

## 6. Superseded and out-of-scope claims

- `superseded`: local `/auth/signup` and `/auth/signin` product design; legacy compose objects remain persisted compatibility state until PR #155 integrates.
- `superseded`: per-record CompletableFuture fan-out and partial synchronous ETL commits.
- `superseded`: older durable branches replaced by #143→#148; old checks/reviews/approvals do not transfer.
- `out_of_scope`: claiming end-to-end exactly-once across Kafka, remote warehouses, APIs, or brokers without connector-specific proof.
- `out_of_scope`: using GitHub Copilot or `COPILOT_GITHUB_TOKEN` as the development-model credential.
- `out_of_scope`: claiming disaster recovery, RPO, RTO, certification, licensing clearance, or release readiness from a backup, green aggregate, issue, or active PR alone.

## 7. Update rule

A PR that changes a public API/event, persisted or external data model, security/trust boundary, lifecycle, deployment, autonomous authority, compatibility contract, connector truth, recovery, or merge/release evidence must update the relevant PRD/TRD/Architecture/ADR/UML/ERD/API/Security/Operability and this matrix in the same integration line. Exact SHAs and run IDs belong in dated evidence or PR bodies, not timeless architecture. Newly opened material work is reconciled during the next stable documentation update and never silently promoted to protected truth.
