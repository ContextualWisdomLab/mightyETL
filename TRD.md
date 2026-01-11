# Technical Requirements Document (TRD)

This document defines the technical requirements, constraints, and operational expectations for xtrmETL.
It complements `PRD.md` (what/why) and `ARCHITECTURE.md` (how it fits together).

## 1. Scope

### 1.1 In Scope

- Services: `cdc-service`, `etl-service`, `zuul-gateway`, `eureka-server`, `config-server`.
- Core capabilities: CDC from PostgreSQL, ETL processing of JSON payloads, API gateway routing, JWT-based authentication, service discovery, distributed tracing.

### 1.2 Out of Scope (for v1)

- Web UI / admin console
- Multi-database support beyond PostgreSQL
- Full Spring Boot 3.x migration work (tracked separately; see `docs/boot-support-strategy.md`)

## 2. Runtime & Build Requirements

- Java: 25 (build and runtime)
- Build tool: Maven (3.6+)
- OS: Linux/macOS compatible development environment

## 3. Ports & Network Contracts

- Zuul Gateway: `8080`
- ETL Service: `8000`
- CDC Service: `8001`
- Eureka Server: `8761`
- Zipkin: `9412` (if enabled)

## 4. Dependency Baselines (Current)

Pinned in `pom.xml` unless noted otherwise:

- Spring Boot: `2.7.18`
- Spring Cloud: `2021.0.9`
- Compiler target: `maven-compiler-plugin` uses `release=${java.version}` (Java 25)

CDC-specific (currently pinned in `cdc-service/pom.xml`):

- Debezium API: `3.4.0.Final`
- Debezium Embedded: `3.4.0.Final`
- Debezium Postgres Connector: `3.4.0.Final`

## 5. Service Requirements

### 5.1 API Gateway (zuul-gateway)

- Routes requests to downstream services.
- Must enforce authentication/authorization for protected APIs.

### 5.2 Authentication

- Endpoints:
  - `POST /auth/signup`
  - `POST /auth/signin`
- JWT requirements:
  - Tokens must be signed (shared secret or keypair).
  - Expiry and validation enforced on protected routes.
  - Role-based access control (at minimum: `USER`, `ADMIN`).

### 5.3 ETL Service

- Endpoint:
  - `POST /api/etl/process`
- Accepts JSON array payloads, validates required fields, and persists transformed results.
- Must be resilient to partial failures (retry/backoff where applicable) and produce deterministic transformations for the same input.

### 5.4 CDC Service

- Endpoints:
  - `POST /api/cdc/start`
  - `POST /api/cdc/stop`
- Captures PostgreSQL changes and publishes downstream events (Kafka optional depending on deployment).
- Must provide safe start/stop semantics and clear operational logging.

### 5.5 Config Server / Eureka Server

- Eureka must be available before other services register.
- Config Server is optional/future-facing; configuration must also work via local `application.yml` + environment variables.

## 6. Data & Compatibility Requirements

- Database: PostgreSQL 12+ (logical replication enabled when CDC is used).
- Schema changes during staged rollout should be additive whenever possible.
- Event/schema compatibility:
  - CDC event formats should remain backward compatible across a canary window.
  - Any breaking change requires an explicit migration plan and rollback path.

## 7. Observability Requirements

- Tracing: Zipkin integration supported (Micrometer tracing/Brave).
- Logging:
  - Must include correlation identifiers where available.
  - Must log security-relevant events (login attempts, authorization failures) without leaking secrets.
- Health:
  - Services should expose health endpoints suitable for orchestration probes.

## 8. Operational & Reliability Requirements

- Canary rollout must include explicit rollback triggers (latency/error-rate regressions and/or Kafka lag thresholds).
- Rollback must be executable via a tagged last-known-good release and/or a maintained support branch (see `docs/boot-support-strategy.md`).

## 9. Testing & Quality Gates

- Unit tests must pass: `mvn test`
- Security checks (if enabled in PR checks) must pass before merging.
- Documentation consistency is validated by `etl-service` documentation tests and should remain green.

## 10. Migration Notes (Boot 3.x / Jakarta)

- Boot 3.x migration planning and prerequisites are tracked in `docs/boot-support-strategy.md`.
- Dependency baselines and compatibility targets should be captured in a version matrix and validated via CI before Step 2/3 execution.
