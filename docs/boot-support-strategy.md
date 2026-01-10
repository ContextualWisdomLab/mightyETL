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
  - Zuul replacement (Spring Cloud Gateway)
  - Spring Kafka async API: replace ListenableFuture usage with CompletableFuture
  - Test updates for API and package changes

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

## Risks And Mitigations

- Zuul replacement is required for Boot 3.x; plan migration early.
- Validate Debezium and Kafka compatibility before dependency bumps.
- Jakarta namespace migration may break third-party libraries; audit dependencies early.
- Performance characteristics may change; establish baseline metrics before migration.
- Test coverage gaps may exist; enhance integration tests for critical paths.

## Decision Log

- Date: 2026-01-09
- Owner: Product Engineering Team
- Alternatives: Keep 2.7.x with commercial support vs upgrade to Boot 3.x (selected)
- Participants: Product Engineering Team, Architecture stakeholders
