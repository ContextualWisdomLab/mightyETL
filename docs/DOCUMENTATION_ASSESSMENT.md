# Documentation Completeness Assessment

**Baseline:** protected `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Assessment date:** 2026-08-10  
**Purpose:** acquisition-diligence and implementation truthfulness

## Verdict

The protected repository has useful historical documentation, but the canonical documentation set on protected `develop` is **not sufficient** for a commercial or acquisition-ready system. The principal defect is not raw document count; several root documents and their validation tests encode assumptions older than the shipped ETL/idempotency/durable-intake code, while multiple architecture-governance families are absent entirely.

PR #149 supplies a materially stronger canonical spine and is the current `active_pr` remediation, but an open documentation PR is not protected product truth. Even after that spine integrates, documentation remains a living control: newly opened implementation work, cross-cutting governance, recovery, data lifecycle, evidence semantics, licensing, and release proof must stay discoverable and source-backed. Issue #159 tracks that live follow-through.

A purchaser or maintainer must not need chat history, pull-request bodies, or undocumented institutional memory to determine what is shipped, what is under review, and what is merely planned.

## Status taxonomy

Every durable decision or capability in canonical documentation uses one of these labels:

- `implemented_on_develop` — present on the exact protected baseline named above.
- `active_pr` — implemented or being implemented on an open pull request; not shipped.
- `planned` — accepted issue/design direction without merge-ready production code.
- `superseded` — historical design/branch no longer intended as the integration path.
- `out_of_scope` — intentionally excluded from the current product boundary.
- `known_gap` — current shipped behavior that is intentionally documented as incomplete or unsafe for a claimed use.

Document-family fitness is assessed independently as `present_current`, `present_stale`, `partial`, `missing`, `not_applicable`, `superseded`, or `owned_by_separate_active_pr`. A strong design document can be `present_current` on PR #149 while the protected branch remains insufficient.

## Baseline audit

| Family | Protected baseline state | PR #149 fitness | Current sufficiency verdict |
| --- | --- | --- | --- |
| PRD | Root `PRD.md` presents historical sign-in/sign-up/JWT and retired per-record parallel semantics as current | substantial rewrite exists | `present_stale` until protected integration and post-169 reconciliation |
| TRD | Omits current persistence, exact-source acceptance, durable controls, and strict quality/evidence contracts | substantial rewrite exists | `present_stale` until protected integration and current work reconciliation |
| Architecture | Mixes historical authentication/data-flow assumptions with current services | current component/data/authority/deployment baseline exists | `partial`: latest schema/recovery/DLT/config/runtime authorities are not yet fully absorbed |
| ADR | No canonical ADR index on protected baseline | ADR-0001..0008 plus status-bearing index | `partial`: latest cross-cutting decisions need durable ADR coverage or explicit absorption |
| UML | No canonical component/sequence/state/deployment set on protected baseline | component, ETL, durable state, CDC, gateway, deployment, automation, CAS diagrams exist | `partial`: service identity, DLT, schema authority, recovery, and evidence flows remain incomplete |
| ERD / data model | No canonical current-vs-planned ERD on protected baseline | physical develop truth plus durable active-PR overlays | `partial`: clean-install retirement, lifecycle/tenancy/data-governance and recovery artifact authority need reconciliation |
| API/event contract | Behavior dispersed across code and feature docs | prose API contract exists; machine-readable contract is separate PR #157 | `owned_by_separate_active_pr` for OpenAPI/AsyncAPI; prose alone is not interoperability completion |
| Security / Threat Model | Security branch and trust-boundary claims are stale/incomplete | canonical Security and Threat Model exist | `partial`: diagnostic confidentiality, DLT privacy, dependency-graph completeness, service/config identity remain active work |
| Test strategy | No canonical evidence contract on protected baseline | red-green, source identity, coverage, migration, concurrency, security and release rules exist | `partial`: issue #196 and issue #205 prove remaining scanner/coverage authority gaps |
| Operability / recovery | Feature-specific notes only | system operability entry point exists | `partial`: PR #208 is active backup/restore provenance, but measured RPO/RTO and full-system recovery are absent |
| Traceability | Decisions dispersed across PRs, tests, chat | status-aware matrix exists | `present_current` on this PR after post-169 reconciliation, not protected truth until merge |
| Release / provenance / licensing | No integrated release authority and no owner-authorized root license | issue-backed requirements only | `missing_or_partial`; issue #151 and issue #165 remain unresolved |
| Data governance / privacy / retention | Fragmented across Security, ERD, and feature docs | purpose-bound principles exist | `partial`: DLT, pending payloads, tenant authority, deletion/retention evidence remain incomplete |
| Agent guidance | Existing guidance conflicts with authorized autonomous maintenance | writer lease/CAS and authority separation are reconciled | `active_pr`, not protected runtime; #121 and issue #154 remain the implementation path |
| Changelog | Exists and is actively maintained | canonical reconciliation recorded | `partial` until all current source/doc changes are integrated |

## Concrete drift found on protected develop

### Synchronous ETL

`EtlService` parses and transforms the complete bounded batch before the first JDBC write, then writes synchronously within one Spring transaction. The old product documentation's per-record fan-out/partial-failure story is obsolete. Optional `Idempotency-Key` processing is principal-scoped, uses a transaction-lifetime PostgreSQL try-lock, hashes the principal/key, and commits target writes plus the durable response ledger atomically.

### Durable asynchronous intake

`EtlJobController` is already present behind an explicit disabled-by-default intake flag. It provides `POST /api/etl/jobs` and owner-scoped `GET /api/etl/jobs/{job_record_id}` with `202 Accepted`, `Location`, replay metadata, and `Cache-Control: no-store`. On protected develop it is intake-only: worker execution is not integrated and `etl_job_records.job_status` is limited to `PENDING`, `RUNNING`, `SUCCEEDED`, and `FAILED`.

### Persistence and schema authority

Protected develop has at least these authoritative owned structures:

- local compose bootstrap `processed_data` plus legacy `users`, `roles`, and `user_roles` objects;
- Flyway `etl_idempotency_records` durable replay ledger;
- Flyway `etl_job_records` durable asynchronous intake records.

The legacy local-auth bootstrap objects are persisted reality but must not be confused with a shipped sign-up/sign-in product API. The protected configuration also allows a second schema mutation authority through JPA auto-DDL; PR #184 makes Flyway-only schema mutation explicit. Until protected integration, that decision is `active_pr`, not shipped truth.

### Gateway, direct-service, registry, and configuration identity

Protected develop still contains a placeholder `JwtAuthenticationFilter` that treats only the literal example token `valid_token` as valid. Therefore cryptographic JWT/resource-server identity cannot be claimed as `implemented_on_develop`; PR #142 is the active replacement path.

Protected `etl-service` is published directly by the default Compose topology and independently uses HTTP Basic for `/api/**`. Gateway JWT work does not establish a downstream service identity or prove gateway-only reachability. Issue #161 remains a `known_gap`. Eureka and CDC control-plane identity also require separate source-backed authority; one trust boundary cannot be inferred from another.

Config Server startup must not obtain authority from an example or silently selected remote repository. PR #189 is the active path for explicit fail-closed repository authority.

### CDC lifecycle, delivery, DLT, and registry integrity

Protected develop publishes Debezium JSON to Kafka without awaiting broker acknowledgement before returning from the change-event handler, and `stop()` clears engine/task references immediately after requesting close. PR #139 is the acknowledged-delivery repair path; issue #141 records the truthful graceful-stop completion gap.

PR #192 and PR #197 add active, unshipped DLT confidentiality and terminal-routing boundaries. PR #201 rejects duplicate CDC connector identifiers rather than allowing silent overwrite. These are durable architecture decisions that require Security, data-governance, connector, UML, and ADR reconciliation before protected merge.

### Exact-source CI, scanner completeness, and autonomous maintenance

Protected pull-request CI still uses default `actions/checkout` event-ref semantics, which can execute a generated merge ref. PR #121 carries literal-head CI/SBOM controls and separately permissioned OpenCode scheduler design, but remains `active_pr`.

A green scanner is not complete evidence when Maven dependency versions or child dependencies cannot be resolved. Issue #196 records this fail-open evidence gap. Likewise, a generated merge revision is not literal source proof. Scanner revision, dependency-materialization completeness, source head, live base, statuses, reviews, and model judgments remain separate authorities.

The external scheduler has been repeatedly strengthened to continue around local waits and use budget-safe clean continuation. The protected embedded runtime remains issue #154 / PR #121 work; changing #121 solely for wording would invalidate the repaired #143→#148 stack without independent product value.

### Coverage evidence

The protected JaCoCo durable-job gate can select zero production classes and report all configured zero-missed checks as satisfied. Issue #162 owns the quality defect; PR #164 is the active repair that separates report/check class-file filters and adds a non-empty class-count invariant. A zero-class bundle must never be represented as 100% owned-production coverage.

PR #164 proving eight intended classes is necessary but does not by itself prove repository-wide owned-production scope. Issue #205 therefore remains a separate `known_gap`: release evidence needs one explicit non-empty owned-code inventory and aggregate statement/branch proof across every owned production module, with generated/third-party exclusions justified rather than implicit.

### Observability and runtime supply chain

Protected tracing configuration repeats a non-standard Zipkin 9412 service-side port contract. Issue #166 and PR #167 carry host-compatibility/internal-9411 repair; issue #168 and PR #169 retire unsafe Replit Zipkin bootstrap execution and overlapping runtime launch authority. Runtime identifier compatibility inventory is active PR #191. None is shipped until protected integration.

### Recovery and external side effects

PR #208 is the active PostgreSQL logical backup and restore-provenance path. It binds backup artifacts to source SHA, database version, Flyway level, digest, restrictive publication, collision-safe identity, archive validation, clean-target restore, and migration re-verification. It does not prove application readiness, destructive-loss replacement, Kafka/Debezium/DLT/external-target reconciliation, or measured RPO/RTO. Those remain separate recovery acceptance work.

## Live work opened after the canonical spine was drafted

All work below remains unshipped and must stay visibly `active_pr`, `planned`, or `known_gap` rather than being omitted or promoted to protected truth:

- PR #155 — retire abandoned local-auth tables from new clean installations with explicit existing/private-consumer compatibility;
- PR #156 — remove the misleading Qlik row-write scaffold from production discovery/configuration;
- PR #157 — establish checked-in machine-readable OpenAPI/AsyncAPI contracts without advertising active-PR lifecycle behavior;
- PR #158 and PR #163 — remove nonfunctional MySQL and SQL Server Debezium scaffolds from production discovery;
- PR #160 — establish the shared Jackson 2.21.5 baseline for CVE-2026-54515, CVE-2026-59889, and GHSA-mhm7-754m-9p8w without suppressing findings;
- issue #161 — replace the independently reachable ETL HTTP Basic boundary with supported direct/east-west service authentication;
- issue #162 / PR #164 — make JaCoCo non-vacuous and prove a real production class set before any 100% claim;
- issue #165 — bind exact protected source, packages, SBOM, provenance, reproducibility, attestation verification, publication authority, rollback, and release acceptance;
- issue #166 / PR #167 — restore Zipkin internal 9411 while preserving explicit host compatibility;
- issue #168 / PR #169 — remove opaque runtime bootstrap, mutable remote scripts, duplicate launch authority, and tracked binary follow-through;
- PR #170, PR #171, PR #172, PR #174, PR #176, and PR #211 — harden diagnostic confidentiality across controller, loader, CDC, parser, DDL/row and DLT boundaries;
- PR #184 — establish Flyway-only production schema mutation authority;
- PR #189 — require explicit fail-closed Config Server repository authority;
- PR #191 — inventory runtime/Kafka/Debezium/config/state identifier compatibility before product-name migration;
- PR #192 and PR #197 — govern dead-letter privacy and terminal routing;
- PR #199 — reject invalid amount-like values fail-closed;
- PR #201 — make CDC connector registry identity collision-safe;
- issue #196 — reject Maven security evidence built from an unresolved dependency graph;
- issue #205 — prove repository-wide owned-production coverage rather than only focused class bundles;
- PR #208 — bind PostgreSQL backup/restore to exact provenance without inventing disaster-recovery attainment;
- PR #222 and PR #228 — preserve structured record snapshot integrity at mutable/hostile object boundaries;
- PR #224, PR #226, and PR #230 — make public bootstrap and environment/configuration APIs beginner-readable without changing behavior.

The legal/release boundary remains unresolved: issue #151 requires an explicit owner-approved licensing/copyright decision. Automation must not invent a root license merely to make packaging or documentation appear complete.

Issue #159 is `planned` follow-through for live documentation coverage and traceability. It is not a substitute for updating canonical docs when relevant implementation changes.

## ADR sufficiency

The eight foundational ADRs provide a coherent baseline, but they are **not sufficient for the whole current conversation and live repository** unless the following durable decisions are explicitly absorbed into existing ADRs or recorded in new, non-colliding ADRs after checking active-PR reservations:

1. one production schema mutation authority and Flyway migration/rollback/recovery semantics;
2. service, registry, Config Server, gateway, and direct/east-west identity authority;
3. diagnostic confidentiality and stable non-sensitive error contracts;
4. DLT payload/header retention, access, encryption, deletion, terminal routing, redrive, and replay authority;
5. non-vacuous focused and repository-wide quality evidence plus source/revision/evidence-channel separation;
6. complete dependency-graph scanner evidence and fail-closed supply-chain acceptance;
7. release/SBOM/provenance/reproducibility/licensing/NOTICE/publication authority;
8. runtime identifier and stateful compatibility migration;
9. backup, restore, destructive-loss recovery, external-side-effect reconciliation, and measured RPO/RTO authority;
10. tenancy choice and principal/tenant/data-residency/retention/deletion boundaries.

Adding filenames without decisions is not completion. Each ADR must state context, alternatives, decision, consequences, failure/recovery, migration/rollback, security/data-governance impact, tests/acceptance, and supersession conditions.

## UML and ERD sufficiency

`docs/UML.md` is structurally useful but `partial`. It must eventually add or update source-backed diagrams for:

- direct client→ETL, gateway→ETL, service-registry, Config Server, and CDC control-plane identity flows;
- schema mutation and Flyway migration/rollback/recovery authority;
- DLT publication, retention, terminal routing, redrive/replay and authorization;
- PostgreSQL backup→manifest verification→destructive-loss replacement→clean restore→application/invariant validation;
- exact source→scanner/SBOM/review→merge→protected-develop operational acceptance→release authority;
- runtime identifier migration and stateful compatibility;
- degraded modes and failure-domain boundaries.

`docs/ERD.md` remains accurate for the protected physical tables and active durable-job overlays, but is `partial` as a data-governance model. It must reconcile clean-install legacy-auth retirement, tenant/principal authority, pending payload retention, replay lineage once exact migrations stabilize, DLT/recovery artifact ownership where mightyETL actually persists them, and conceptual/external ownership labels. Do not invent tables merely to satisfy an ERD request; non-relational backup bundles, manifests, Kafka/DLT state, and external warehouses belong in a clearly labeled logical artifact/data model unless a real migration introduces persistence.

## Remaining cross-cutting documentation authority

The spine is necessary but file count alone is not sufficient. Each category below needs either a dedicated canonical document or a clearly discoverable index to one authoritative equivalent:

1. roadmap/lifecycle status and dependency-ordered exit criteria;
2. data governance, privacy, retention, principal/tenant authority, deletion and DLT evidence;
3. migration, rollback, forward recovery, downgrade, identifier migration and compatibility policy;
4. release, versioning, SBOM/provenance, reproducibility, licensing/NOTICE, publication and rollback evidence;
5. standalone/MSA deployment profiles, optional versus required dependencies and failure domains;
6. standards/research doctoring with APA 7 references linked to decisions and tests;
7. connector support matrix distinguishing production, scaffold, removed-from-discovery and planned integrations;
8. SLI/SLO targets versus actually measured attainment, including RPO/RTO;
9. acquisition-diligence controls covering security, rights, dependency obligations, recovery, data authority and residual known risks;
10. identity/trust-boundary authority distinguishing gateway, direct/east-west ETL, Eureka, Config Server, CDC and operator identities;
11. quality/evidence semantics distinguishing literal source, synthetic merge, complete/incomplete dependency graphs, focused/repository-wide coverage, and vacuous/non-vacuous evidence;
12. repository-runtime and observability supply-chain authority, including third-party binaries/images and supported startup paths;
13. backup/restore/recovery acceptance and external side-effect reconciliation;
14. data classification and terminal lifecycle for DLT, request payloads, snapshots, logs, metrics and backup artifacts.

These categories may be satisfied by existing canonical sections if indexed and machine-checkably discoverable. They are not complete merely because an issue or PR body describes them.

## Documentation completeness gate

The minimum canonical graph remains:

1. `PRD.md`
2. `TRD.md`
3. `ARCHITECTURE.md`
4. `SECURITY.md`
5. `docs/adr/README.md` plus detailed ADRs
6. `docs/UML.md`
7. `docs/ERD.md`
8. `docs/API_CONTRACT.md`
9. `docs/THREAT_MODEL.md`
10. `docs/TEST_STRATEGY.md`
11. `docs/OPERABILITY.md`
12. `docs/TRACEABILITY.md`
13. `docs/DOCUMENTATION_ASSESSMENT.md`
14. discoverable authorities for migration/rollback/recovery, data governance/privacy/retention, release/provenance/licensing, connector support, standards/research, and acquisition diligence
15. `AGENTS.md`, `CLAUDE.md`, `README.md`, and `CHANGELOG.md` aligned to those contracts

A future feature that changes a public API, persisted state, security/trust boundary, lifecycle state machine, deployment topology, autonomous-authority topology, compatibility promise, or merge/release evidence contract must update the relevant canonical family in the same pull request. Newly opened material PRs/issues must be reconciled during the next stable documentation update.

## Overall conclusion

- **Document breadth:** strong on PR #149; insufficient on protected `develop`.
- **PRD/TRD/Architecture depth:** substantial, but stale relative to post-169 work.
- **ADR coverage:** foundational but partial.
- **UML coverage:** useful, but partial for identity, DLT, recovery, schema and release authority.
- **ERD/data model:** truthful for protected persistence, partial for current lifecycle and artifact governance.
- **Traceability:** reconciled on this active documentation branch through current post-169 work; not protected truth until merge.
- **Acquisition-ready documentation:** not yet sufficient.

The completion condition is not “files exist.” It is protected integration of one coherent code-current graph, live capability maturity, machine-checkable consistency, accepted ADR coverage for durable decisions, and operational/release evidence that does not conflate source, synthetic merge, incomplete scanner, focused coverage, approval, or protected-runtime proof.

## Out of scope for this documentation slice

- claiming active durable-worker/pagination/polling/conditional-status/cancellation/replay branches as shipped;
- implementing gateway, direct-service, Config Server, service-registry, or CDC identity code;
- implementing CDC delivery, graceful stop, DLT, schema, recovery, coverage, scanner, connector, or runtime source changes;
- choosing a license on behalf of the owner in issue #151;
- publishing a release before issue #165 prerequisites are satisfied;
- inventing SLO/RPO/RTO attainment not measured on protected production-like infrastructure.

## References

GitHub. (2026). *Events that trigger workflows*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows

Nottingham, M., & Kamp, P.-H. (2024). *Structured Field Values for HTTP* (RFC 9651). RFC Editor. https://www.rfc-editor.org/info/rfc9651

Nottingham, M., Wilde, E., & Dalal, S. (2023). *Problem Details for HTTP APIs* (RFC 9457). RFC Editor. https://www.rfc-editor.org/info/rfc9457

Souppaya, M., Scarfone, K., & Dodson, D. (2022). *Secure Software Development Framework (SSDF) Version 1.1: Recommendations for Mitigating the Risk of Software Vulnerabilities* (NIST SP 800-218). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-218
