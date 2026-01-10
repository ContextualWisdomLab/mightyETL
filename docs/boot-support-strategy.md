# Spring Boot Support Strategy

## Decision

- Adopt a Spring Boot 3.x upgrade path (target 3.2.x LTS) with Java 17.
- Keep 2.7.13 in the short term while aligning code to the newer APIs.

## Rationale

- Spring Boot 2.7.x OSS support ended, increasing security and maintenance risk.
- Spring Security and Spring Kafka APIs are already moving toward 3.x patterns.

## Scope And Impact

- Modules: cdc-service, etl-service, config-server, eureka-server, zuul-gateway
- Expected changes:
  - Java 17 baseline and Jakarta namespace migration
  - Spring Cloud BOM upgrade (2023.x series)
  - Zuul replacement (Spring Cloud Gateway) with route definitions replacing Zuul filters
  - Spring Kafka async API: replace ListenableFuture usage with CompletableFuture
  - Test updates for API and package changes
- Module impact:
  - cdc-service: KafkaTemplate async API migration, Debezium JSON event handling, Jakarta package updates.
  - etl-service: SecurityConfig DSL alignment, Jakarta namespace updates, security tests adjustment.
  - config-server: Spring Cloud 2023.x alignment, Java 17 baseline, Jakarta migration in configuration.
  - eureka-server: Spring Cloud BOM upgrade, Java 17 baseline, jakarta.* dependency updates.
  - zuul-gateway: replace Zuul filters with Spring Cloud Gateway routes/filters, update gateway security and tests.

## Plan

1. Pre-migration cleanup (this issue): fix nullability, deprecations, and test warnings.
2. Dependency alignment: upgrade parent BOMs and validate starters.
3. Code migration: Jakarta imports, security DSL, Kafka async API updates.
4. Verification: module builds, targeted integration tests, staged rollout.

### Timeline

- Step 1: Q1 2026
- Step 2: Q2 2026
- Step 3: Q2-Q3 2026
- Step 4: Q3 2026

### Rollback Strategy

- Maintain a 2.7.13 support branch for rollback during migration phases.
- Require verification gates at each step before proceeding.
- Use staged rollout with canary deployment for production environments.

### Compatibility Targets

- Spring Boot 3.2.x with Spring Cloud 2023.x (Leyton)
- Spring Kafka 3.1.x (CompletableFuture-based KafkaTemplate)
- Debezium 2.5.x+ (validate against Boot 3.2/Jakarta dependencies)
- Spring Cloud Gateway 4.x (bundled with Cloud 2023.x)

### Verification Gates

- Step 1 exit: nullability/deprecation fixes merged and module unit tests green.
- Step 2 exit: BOM alignment resolves, Debezium/Kafka compatibility matrix confirmed, module builds + smoke tests pass.
- Step 3 exit: Jakarta/security/Kafka async migrations compile and integration tests green.
- Step 4 exit: staging canary passes agreed window with error/latency within baseline, rollback runbook verified.

## Risks And Mitigations

- Zuul replacement is required for Boot 3.x; target Q2 2026, owner: Platform team, migrate auth/routing/filter parity, rollback gate keeps 2.7.13 branch.
- Validate Debezium and Kafka compatibility before dependency bumps; maintain version matrix and run weekly CI compatibility checks with rollback to pinned versions.
- Jakarta namespace migration may break third-party libraries; audit dependencies, prioritize blockers, and track vendor patches with a migration checklist.
- Performance characteristics may change; baseline P95 latency/throughput/lag, define thresholds, and run regression tests weekly.
- Test coverage gaps may exist; target 80% unit coverage and add integration tests for CDC pipeline, auth, and recovery paths.

## Decision Log

- Date: 2026-01-09
- Owner: Product Engineering Team
- Alternatives: Keep 2.7.x with commercial support vs upgrade to Boot 3.x (selected)
- Rationale:
  - Keep 2.7.x: commercial support cost (~EUR 10k/yr), lower migration effort, retains Zuul short-term.
  - Upgrade to 3.x: OSS security updates, Spring Cloud/Kafka feature access, Java 17 alignment, Boot 3.2 LTS window.
  - Decision drivers: reduce post-EOL security exposure, pay down tech debt, align with platform roadmap.
- Participants: Product Engineering Team, Architecture stakeholders
- Exit gate approvers:
  - Step 1 exit: engineering lead + platform architect
  - Step 2 exit: ops lead + security lead
  - Step 3 exit: QA lead + platform architect
  - Step 4 exit: product manager + ops lead
- Consensus: platform/security agreed; main risk flagged is Zuul migration complexity in Step 3.
