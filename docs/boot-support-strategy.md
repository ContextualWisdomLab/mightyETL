# Spring Boot Support Strategy

## Decision

- Baseline is Spring Boot 3.5.x (3.5.9) with Java 25.
- Spring Cloud baseline is 2025.0.x (2025.0.1).

## Rationale

- Spring Boot 2.7.x OSS support ended, increasing security and maintenance risk.
- Spring Security and Spring Kafka APIs are already moving toward 3.x patterns.

## Scope And Impact

- Modules: cdc-service, etl-service, config-server, eureka-server, zuul-gateway
- Expected changes:
  - Java 25 baseline and Jakarta namespace migration
  - Spring Cloud BOM upgrade (`org.springframework.cloud:spring-cloud-dependencies:2025.0.x`, aligned with Boot 3.5.x)
  - Zuul replacement (Spring Cloud Gateway) with route definitions replacing Zuul filters
  - Spring Kafka async API: replace ListenableFuture usage with CompletableFuture
  - Test updates for API and package changes
- Module impact:
  - cdc-service: KafkaTemplate async API migration, Debezium JSON event handling, Jakarta package updates.
  - etl-service: SecurityConfig DSL alignment, Jakarta namespace updates, security tests adjustment.
  - config-server: Spring Cloud 2023.0.x alignment, Java 25 baseline, Jakarta migration in configuration.
  - eureka-server: Spring Cloud BOM upgrade, Java 25 baseline, jakarta.* dependency updates.
  - zuul-gateway: replace Zuul filters with Spring Cloud Gateway routes/filters, update gateway security and tests.

## Plan

1. Pre-migration cleanup (Step 1): fix nullability, deprecations, and test warnings until all modules are unit-test green.
2. Dependency alignment: upgrade parent BOMs and validate starters.
3. Code migration: Jakarta imports, security DSL, Kafka async API updates.
4. Verification: module builds, targeted integration tests, staged rollout.

### Timeline

- Step 1: Q1 2026 (nullability/deprecation cleanup, unit tests green).
- Step 2: Q2 2026 start (BOM alignment and dependency validation).
- Step 3: Q2 2026 late - Q3 2026 early (Jakarta, SecurityConfig DSL, Kafka async API).
- Step 4: Q3 2026 (integration tests and canary validation).
- Notes: Zuul replacement can proceed in parallel and is not a hard gate for other modules; Step 2 must complete per-module before Step 3 for that module.

#### Per-Module Prerequisites (Parallelization Guidance)

- Step 2 (per-module) can start only after Step 1 is green for that module and the baseline versions in `pom.xml` are reflected in the Compatibility Matrix.
- Step 3 (per-module) can start only after Step 2 is complete for that module (BOMs aligned, dependency conflicts resolved, and a smoke build/test passes).
- Suggested order (lower coupling → higher coupling): `eureka-server`/`config-server` → `etl-service`/`cdc-service` → `zuul-gateway` (route/auth parity work).

### Rollback Strategy

- Maintain a Boot 2.7.x support branch (currently 2.7.18) for rollback during migration phases.
- Require verification gates at each step before proceeding.
- Use staged rollout with canary deployment for production environments.
- Rollback triggers (canary window): sustained elevated error rate, P99 latency regression vs baseline, or Kafka lag/consumer errors above agreed thresholds.
- Rollback runbook: stop rollout → route traffic back to the stable release → redeploy last-known-good from the Boot 2.7.x support branch (tagged) → validate health checks + key SLOs + data compatibility.
- Data compatibility rules: prefer additive DB migrations + feature flags during canary; avoid irreversible schema changes until Step 4 exit gates are met.

### Compatibility Targets

- Spring Boot 3.5.x with Spring Cloud `org.springframework.cloud:spring-cloud-dependencies:2025.0.x`
- Spring Kafka 3.3.x (CompletableFuture-based KafkaTemplate)
- Debezium 3.x+ for embedded engine (Jakarta namespace compatible); alternatively run Debezium via Kafka Connect (separate process/JVM)
- Spring Cloud Gateway 4.3.x (via Spring Cloud 2025.0.x)
- Note: patch versions and tested upper bounds are pinned during Step 2 and tracked in the Compatibility Matrix + CI output.

### Verification Gates

- Step 1 exit: nullability/deprecation fixes merged and module unit tests green.
- Step 2 exit: BOM alignment resolves, Debezium/Kafka compatibility matrix confirmed, module builds + smoke tests pass.
- Step 3 exit: Jakarta/security/Kafka async migrations compile and integration tests green.
- Step 4 exit: staging canary passes agreed window with error/latency within baseline, rollback runbook verified.

## Risks And Mitigations

 - Zuul replacement is required for Boot 3.x; completed by migrating to Spring Cloud Gateway (#63/#64).
- Validate Debezium and Kafka compatibility before dependency bumps; maintain version matrix and run weekly CI compatibility checks with rollback to pinned versions.
- Jakarta namespace migration may break third-party libraries; audit dependencies, prioritize blockers, and track vendor patches with a migration checklist.
- Performance characteristics may change; baseline P95 latency/throughput/lag, define thresholds, and run regression tests weekly.
- Test coverage gaps may exist; target 80% unit coverage and add integration tests for CDC pipeline, auth, and recovery paths.
- Dual-version support during rollout may be required; plan traffic routing, feature flags, and data compatibility rules, plus mixed-version CI gates and a Jakarta+Kafka async test matrix.

### Risk Mitigation Execution Checklist (RACI)

| Area | Artifact | Owner (team/role) | Due / SLA | Approver | Acceptance Criteria |
| --- | --- | --- | --- | --- | --- |
| Version matrix | This document (see “Compatibility Matrix”) | Platform Infra Team (maintainer) | On dependency bump PRs (same PR) + weekly (Mon 09:00) | Engineering lead | Matrix updated and linked from PR/CI summary; baseline versions match `pom.xml` |
| CI compatibility checks | CI pipeline running `mvn -B test` (all modules) | Platform Infra Team | Weekly (Mon 09:00) + required on dependency bump PRs | Engineering lead | Baseline is green; candidate is at least compile/package-green |
| Dependency audit (Jakarta & 3rd-party libs) | GitHub Issue “Boot 3 migration dependency audit” checklist | Security Team (security lead) | Kickoff (start of Step 1) + monthly (1st business day) + before Step 2 | Platform architect | Issue created and assigned; scans attached; blockers triaged with owners + mitigation plans |

#### Role Mapping

- Platform team → Platform Infra Team (CI/CD + dependency governance)
- Security lead → Security Team lead / AppSec owner
- Engineering lead → Product Engineering lead
- Platform architect → Architecture owner for migration approval

#### Compatibility Matrix

| Profile | Java | Boot | Cloud | Notes |
| --- | --- | --- | --- | --- |
| Baseline (current) | 25 | 3.5.9 | 2025.0.1 | Baseline stack for ongoing development |
| Candidate (target) | 25 | 3.5.x | 2025.0.x | Track newer patch versions within the same major/minor line |

#### Weekly CI Compatibility Coverage

- **Modules**: `cdc-service`, `etl-service`, `config-server`, `eureka-server`, `zuul-gateway`.
- **Baseline profile**: full build + unit tests for each module (expected green).
- **Candidate profile**: at minimum compile/package validation until migration work is complete (expected to move to green as Step 3 progresses).
- **Artifact of record**: each scheduled run links back to this document section by including the baseline/candidate versions in the job summary.

#### Dependency Audit Tracking (Jakarta Namespace Risk)

- Track audit outcomes and blockers in a single GitHub Issue (“Boot 3 migration dependency audit”) with a checklist and owners per library; assign ownership at Step 1 kickoff.
- Before Step 2 (and weekly on Mon 09:00, owned by Platform Infra Team): run a dependency tree + update scan (`mvn -B -DskipTests dependency:tree` and `mvn -B -DskipTests versions:display-dependency-updates`) and attach results to the audit issue; blockers must be triaged within 2 business days.
- During Step 3: record “Jakarta blockers” (libraries still on `javax.*`, incompatible transitive deps) in the audit issue and gate progression on having a mitigation (upgrade, replacement, shading, or rollback plan).

## Decision Log

- Date: 2026-01-09 (decision meeting; PR opened 2026-01-10)
- Owner: Product Engineering Team
- Alternatives: Keep 2.7.x with commercial support vs upgrade to Boot 3.x (selected)
- Rationale:
  - Keep 2.7.x: commercial support cost (~EUR 10k/yr), lower migration effort, retains Zuul short-term.
  - Upgrade to 3.x: OSS security updates, Spring Cloud/Kafka feature access, Java 25 alignment, Boot 3.2 LTS window.
  - Decision drivers: reduce post-EOL security exposure, pay down tech debt, align with platform roadmap.
- Participants:
  - engineering lead (Product Engineering)
  - platform architect (Architecture)
  - security lead (Security)
  - ops lead (Operations)
  - product manager (Product)
- Exit gate approvers:
  - Step 1 exit: engineering lead + platform architect
  - Step 2 exit: ops lead + security lead
  - Step 3 exit: QA lead + platform architect
  - Step 4 exit: product manager + ops lead
- Consensus: platform/security agreed; main risk flagged is Zuul migration complexity in Step 3.
