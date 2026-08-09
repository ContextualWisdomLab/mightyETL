# Documentation Completeness Assessment

**Baseline:** protected `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Assessment date:** 2026-08-09  
**Purpose:** acquisition-diligence and implementation truthfulness

## Verdict

The protected repository has useful historical documentation, but the canonical documentation set on protected `develop` is **not sufficient** for a commercial or acquisition-ready system. The principal defect is not raw document count; several root documents and their validation tests encode assumptions older than the shipped ETL/idempotency/durable-intake code, while multiple architecture-governance families are absent entirely.

PR #149 supplies a materially stronger canonical spine and is the current `active_pr` remediation, but an open documentation PR is not protected product truth. Even after that spine integrates, documentation remains a living control: newly opened implementation work, cross-cutting governance and release evidence must stay discoverable and source-backed. Issue #159 tracks that live follow-through.

A purchaser or maintainer must not need chat history, pull-request bodies, or undocumented institutional memory to determine what is shipped, what is under review, and what is merely planned.

## Status taxonomy

Every durable decision or capability in canonical documentation uses one of these labels:

- `implemented_on_develop` — present on the exact protected baseline named above.
- `active_pr` — implemented or being implemented on an open pull request; not shipped.
- `planned` — accepted issue/design direction without merge-ready production code.
- `superseded` — historical design/branch no longer intended as the integration path.
- `out_of_scope` — intentionally excluded from the current product boundary.
- `known_gap` — current shipped behavior that is intentionally documented as incomplete or unsafe for a claimed use.

## Baseline audit

| Family | Baseline state | Sufficiency | Remediation in this documentation slice |
| --- | --- | --- | --- |
| PRD | Root `PRD.md` exists but still presents unshipped sign-in/sign-up/JWT behavior and retired per-record parallel semantics as current | Inadequate | Rewrite around bounded atomic ETL, idempotency, durable intake, CDC, connector truth, and explicit capability status |
| TRD | Root `TRD.md` exists but omits current persistence, exact-head acceptance, durable-job controls, and strict quality contracts | Inadequate | Rewrite technical/runtime/data/quality/release requirements |
| Architecture | Root `ARCHITECTURE.md` exists but mixes historical authentication/data-flow assumptions with current services | Inadequate | Replace with current component/data/authority/deployment architecture and active-PR overlays |
| ADR | No canonical `docs/adr/` index on protected baseline | Missing | Add decision index and foundational ADRs |
| UML | No canonical UML/sequence/state/deployment set | Missing | Add Mermaid component, sequence, state, deployment, and automation-authority views |
| ERD / data model | No canonical current-vs-planned ERD | Missing | Add persisted `processed_data`, legacy local auth bootstrap, idempotency ledger, durable jobs, and active-PR extensions |
| API contract | API behavior is dispersed across controller code and feature docs | Missing canonical entry point | Add API/status/error/idempotency/versioning contract |
| Threat model | No canonical threat model found | Missing | Add assets, trust boundaries, abuse cases, controls, residual risks |
| Test strategy | Test notes exist, but no canonical test/evidence contract | Missing | Add red-green, exact-source, coverage, migration, concurrency, security and release evidence rules |
| Operability | Feature-specific operations docs exist, but no system-level SLI/SLO/backup/recovery/control-plane entry point | Missing | Add system operability contract |
| Traceability | Decisions are spread across PR bodies, feature docs, tests, and chat | Missing | Add status-aware requirement/decision/code/test/PR traceability matrix |
| Security | Root `SECURITY.md` exists but says security fixes are released from `main`, while the repository default/protected integration branch is `develop` | Partial / stale | Align branch truth, security gates, reporting, identity known gap, data protection, and supply-chain expectations |
| Agent guidance | `AGENTS.md`/`CLAUDE.md` prohibit commits unless a human explicitly asks, conflicting with the separately authorized hourly autonomous maintenance design | Stale / internally inconsistent | Scope autonomous writes to mightyETL, require writer leases/CAS, and retain protection/review boundaries |
| Changelog | Exists and is actively maintained | Partial | Record canonical-documentation reconciliation |

## Concrete drift found on protected develop

### Synchronous ETL

`EtlService` currently parses and transforms the complete bounded batch before the first JDBC write, then writes synchronously within one Spring transaction. The old product documentation's per-record fan-out/partial-failure story is therefore obsolete. Optional `Idempotency-Key` processing is principal-scoped, uses a transaction-lifetime PostgreSQL try-lock, hashes the principal/key, and commits target writes plus the durable response ledger atomically.

### Durable asynchronous intake

`EtlJobController` is already present behind an explicit disabled-by-default intake flag. It provides `POST /api/etl/jobs` and owner-scoped `GET /api/etl/jobs/{job_record_id}` with `202 Accepted`, `Location`, replay metadata, and `Cache-Control: no-store`. On protected develop it is intake-only: worker execution is not yet integrated and `etl_job_records.job_status` is limited to `PENDING`, `RUNNING`, `SUCCEEDED`, and `FAILED`.

### Persistence

Protected develop has at least these authoritative owned structures:

- local compose bootstrap `processed_data` plus legacy `users`, `roles`, and `user_roles` objects;
- Flyway `etl_idempotency_records` durable replay ledger;
- Flyway `etl_job_records` durable asynchronous intake records.

The legacy local auth bootstrap objects are persisted reality but must not be confused with a shipped sign-up/sign-in product API.

### Gateway identity boundary

Protected develop still contains a placeholder `JwtAuthenticationFilter` that treats only the literal example token `valid_token` as valid. Therefore cryptographic JWT/resource-server identity cannot be claimed as `implemented_on_develop`. PR #142 is the active replacement path and remains `active_pr` until protected integration.

### CDC lifecycle and delivery

Protected develop publishes Debezium JSON to Kafka without awaiting broker acknowledgement before returning from the change-event handler, and `stop()` clears engine/task references immediately after requesting close. PR #139 is the acknowledged-delivery repair path; issue #141 records the truthful graceful-stop completion gap. Neither is shipped on the assessed protected baseline.

### Exact-source CI and autonomous maintenance

Protected develop's pull-request CI still uses default `actions/checkout` event-ref semantics. Under GitHub `pull_request`, that means the generated merge ref can be checked out. PR #121 carries literal-head CI/SBOM controls and the separately permissioned OpenCode scheduler design, but remains `active_pr` and must not be described as deployed automation until merge.

## Live work opened after the canonical spine was drafted

The documentation graph must expand while implementation continues. At this assessment, all of the following remain unshipped and therefore must stay visibly `active_pr` rather than being silently omitted or promoted to protected truth:

- PR #155 — remove abandoned local-auth tables from new default PostgreSQL clean installations while preserving explicit compatibility handling for existing/private consumers;
- PR #156 — remove the misleading Qlik row-write scaffold from production connector discovery;
- PR #157 — establish checked-in machine-readable OpenAPI/AsyncAPI contracts without advertising active-PR lifecycle behavior;
- PR #158 — remove the nonfunctional MySQL Debezium scaffold from automatic Spring production discovery.

The legal/release boundary also remains unresolved: issue #151 requires an explicit owner-approved licensing/copyright decision. Automation must not invent a root license merely to make packaging or documentation appear complete.

Issue #159 is `planned` follow-through for live documentation coverage and traceability. It is not a substitute for updating canonical docs when the relevant implementation actually changes.

## Remaining cross-cutting documentation authority

The spine is necessary but file count alone is not sufficient. Each category below needs either a dedicated canonical document or a clearly discoverable index to one authoritative equivalent; duplicating prose merely to satisfy filenames is discouraged.

1. roadmap/lifecycle status and dependency-ordered exit criteria;
2. data governance, privacy, retention, principal/tenant authority and deletion evidence;
3. migration, rollback, forward recovery, downgrade and compatibility policy;
4. release, versioning, SBOM/provenance, reproducibility, licensing/NOTICE and rollback evidence;
5. standalone/MSA deployment profiles, optional versus required dependencies and failure domains;
6. standards/research doctoring with APA 7 references linked to decisions and tests;
7. connector support matrix distinguishing production, scaffold, removed-from-discovery and planned integrations;
8. SLI/SLO targets versus actually measured attainment;
9. acquisition-diligence controls covering security, rights, dependency obligations, recovery, data authority and residual known risks.

These categories may be satisfied by existing canonical sections if they are indexed and machine-checkably discoverable. They must not be represented as complete merely because an issue or PR body describes them.

## Documentation completeness gate

This slice defines the minimum canonical documentation graph:

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
14. `AGENTS.md`, `CLAUDE.md`, and `CHANGELOG.md` aligned to those contracts

A future feature that changes a public API, persisted state, security/trust boundary, lifecycle state machine, deployment topology, autonomous-authority topology, compatibility promise, or merge/release evidence contract must update the relevant canonical family in the same pull request. Newly opened material PRs/issues must be reconciled during the next stable documentation update.

## Out of scope for this documentation slice

- claiming active durable-worker/pagination/polling/conditional-status/cancellation/replay branches as shipped;
- fixing the gateway identity production code in PR #142;
- fixing CDC delivery or graceful-stop production code in PR #139 / issue #141;
- merging the OpenCode scheduler in PR #121;
- integrating the source changes in #155–#158;
- choosing a license on behalf of the owner in issue #151;
- inventing SLO attainment data that has not been measured on protected production-like infrastructure.

## References

GitHub. (2026). *Events that trigger workflows*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows

Nottingham, M., & Kamp, P.-H. (2024). *Structured Field Values for HTTP* (RFC 9651). RFC Editor. https://www.rfc-editor.org/info/rfc9651

Nottingham, M., Wilde, E., & Dalal, S. (2023). *Problem Details for HTTP APIs* (RFC 9457). RFC Editor. https://www.rfc-editor.org/info/rfc9457

Souppaya, M., Scarfone, K., & Dodson, D. (2022). *Secure Software Development Framework (SSDF) Version 1.1: Recommendations for Mitigating the Risk of Software Vulnerabilities* (NIST SP 800-218). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-218
