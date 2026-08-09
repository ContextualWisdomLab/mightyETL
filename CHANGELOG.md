# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Durable `POST /api/etl/jobs` submissions now return RFC 9110 `202 Accepted`, a stable pending-job representation, `Location` status-monitor metadata, and explicit replay metadata without changing the synchronous `/api/etl/process` contract. The incomplete intake controller is fail-closed and requires explicit `xtrmetl.etl.jobs.intake-enabled=true` operator opt-in until worker execution and terminal payload clearing are implemented.
- Concurrent requests using the same authenticated-principal-scoped semantic idempotency key now return immediate RFC 9457 `409 etl_idempotency_request_in_progress` responses through PostgreSQL `pg_try_advisory_xact_lock`; retries after completion still replay the committed response.
- `POST /api/etl/process` now supports optional authenticated-principal-scoped `Idempotency-Key` retries with atomic target writes, durable response replay, payload-conflict rejection, and explicit replay response metadata.
- `Idempotency-Key` now prefers the quoted RFC 9651 Structured Field String representation while retaining and normalizing the legacy raw representation to the same durable ledger key.
- ETL request errors now use RFC 9457 `application/problem+json` responses with a stable `errorCode`, fixed type URI, explicit 400/401/404/409/413/422/503/500 taxonomy, and no internal exception text in client responses.
- ETL requests now enforce bounded UTF-8 payload and record-count limits, prevalidate and transform the complete batch before the first JDBC call, and commit accepted records inside one Spring transaction.
- ETL transformations now preserve comma/colon-bearing values, use locale-independent text conversion and deterministic `BigDecimal` amount formatting, and retry only transient Spring data-access failures.
- The SQL Server Debezium scaffold is no longer a Spring component or production-discoverable source. `SqlServerDebeziumCdcSource` remains explicit reference/design code only, and configured `sqlserver-debezium` use fails closed as `unknown_source_type` until a maintained implementation and operational evidence exist.
- Product branding: user-facing docs and suggested image tags use **mightyETL** (formerly xtrmETL).
  - Legacy Java packages (`com.xtrmetl.*`), Maven `artifactId` `xtrmETL`, and some env/topic defaults remain for compatibility.
  - See `docs/rebrand-name-matrix.md`.

### Added

- Principal-scoped durable asynchronous ETL job intake and owner-scoped status resources, Flyway `etl_job_records` migration, deterministic replay/conflict coverage, and the explicit worker boundary in `docs/etl/durable-job-intake.md`.
- Durable idempotency ledger migration, PostgreSQL transaction advisory-lock adapter, deterministic concurrency/rollback coverage, and the operator/client contract `docs/etl/idempotent-retries.md`.
- ETL problem-details client and operator contract: `docs/api/problem-details.md`.
- Operator-configurable ETL admission limits under `mightyetl.etl.*` / `xtrmetl.etl.*`, backed by `ETL_MAX_PAYLOAD_BYTES` and `ETL_MAX_BATCH_RECORDS` environment variables with hard safety ceilings.
- ETL transaction rollback integration coverage and the operator runbook `docs/etl/bounded-atomic-batches.md`.
- Connector scaffolds (contracts + docs only): Qlik Sense, Databricks, Snowflake under `docs/connectors/` and `etl-service` SPI stubs.
- Any-to-any CDC design notes and source SPI scaffold: `docs/cdc/any-to-any-cdc.md`, `cdc-service` SPI stubs.
- CDC operations notes: `docs/cdc/ops-and-reliability.md`.
- Product upgrade progress tracker: `docs/mightyETL-product-upgrade-progress.md`.
- CDC status/sources API: `GET /api/cdc/status`, `GET /api/cdc/sources` (no secrets).
- `DebeziumChangeRecordMapper` + `CanonicalChangeRecord` (mapper unit-tested; not on live publish path).
- CDC target SPI registry (`kafka`, `jdbc-replica`) for any-to-any routing scaffold.
- `etl-service` `xtrmetl.connectors.*` disabled config keys for Databricks/Snowflake/Qlik.
- Dual-read config aliases: `mightyetl.*` preferred → `xtrmetl.*` (`MightyEtlConfigAliasEnvironmentPostProcessor`).
- Configurable replica tables (`xtrmetl.replica.tables`) for `(id,data)`-shaped tables.
- Optional CDC canonical-map counters (`xtrmetl.cdc.canonical-map-enabled`).
- ETL connector catalog API `GET /api/etl/connectors` + scaffold enable guard.
- CDC replication slot lag probe on `GET /api/cdc/status` (`ReplicationSlotProbe`).
- CDC multi-source config list + `CdcSourceFactory` (declarative; single live engine).
- `GET /api/cdc/targets` for target SPI discovery.
- Actuator `cdcEngine` health indicator (engine running + slot details).
- SPI lifecycle: `PostgresDebeziumCdcSource.start/stop` delegates to `CdcService`.
- CDC scaffold references: `mysql-debezium` remains discovery-only; `sqlserver-debezium` is reference-only and not production-discoverable.
- Root POM `<name>mightyETL</name>` (artifactId remains `xtrmETL`).
- README honest “Supported today” matrix; compose file product-name header.

### Added (historical)

- Comprehensive documentation suite (2026-01-08)
  - `README.md`: Quick start guide and project overview
  - `PRD.md`: Product Requirements Document with detailed specifications
  - `ARCHITECTURE.md`: System architecture and technical diagrams
  - `SUMMARY_KR.md`: Korean language summary
  - `CHANGELOG.md`: This file

## [1.0.0] - 2026-01-08

### Project Documentation Initiative

This release focuses on reverse-engineering and documenting the
existing xtrmETL platform.

#### Added Documentation

1. **README.md** (478 lines)
   - Project overview and value proposition
   - Quick start guide with prerequisites
   - Service descriptions for all microservices
   - Authentication flow and API examples
   - Database setup scripts
   - Testing instructions
   - Monitoring setup with Zipkin
   - Technology stack reference
   - Development guidelines

2. **PRD.md** (608 lines)
   - Executive summary and product vision
   - Problem statement analysis
   - Solution overview with core capabilities
   - Functional requirements (FR-CDC-1 through FR-GATE-1)
   - Non-functional requirements (Performance, Reliability, Security, etc.)
   - Complete data model specifications
   - API specifications with examples
   - Deployment architecture
   - Use cases and scenarios
   - Future enhancements roadmap
   - Success metrics and KPIs
   - Risk assessment and mitigation strategies
   - Comprehensive glossary

3. **ARCHITECTURE.md** (633 lines)
   - High-level system architecture diagrams
   - Service communication patterns (synchronous/asynchronous)
   - Detailed data flow diagrams for:
     - ETL processing
     - CDC event capture
     - Authentication flow
   - Service discovery and registration
   - Security architecture
   - Monitoring and observability stack
   - Deployment architectures (single-node and multi-node)
   - Debezium integration details
   - Spring Retry mechanism
   - Network and port configuration
   - Scalability considerations

4. **SUMMARY_KR.md** (206 lines)
   - Korean language summary for stakeholders
   - Project purpose and goals
   - Key features overview
   - System architecture summary
   - Technology stack
   - Use cases
   - API specifications
   - Quick start guide
   - Future improvements
   - Technical debt assessment

#### Project Understanding

Through code analysis, identified the platform as:

- **Enterprise ETL and CDC Platform**
- Microservices-based architecture using Spring Cloud
- Real-time Change Data Capture using Debezium
- Data transformation pipelines with parallel processing
- JWT-based security with role-based access control
- Event streaming via Apache Kafka
- Service discovery with Netflix Eureka
- Distributed tracing with Zipkin

#### Key Components Documented

1. **CDC Service** (Port 8001)
   - PostgreSQL change data capture
   - Debezium embedded engine
   - Kafka event publishing
   - Real-time monitoring capabilities

2. **ETL Service** (Port 8000)
   - JSON data processing
   - Parallel record processing
   - Configurable transformations
   - Automatic retry mechanism
   - Target database loading

3. **Zuul Gateway** (Port 8080)
   - API Gateway with routing
   - JWT authentication filter
   - Load balancing
   - Request routing to services

4. **Eureka Server** (Port 8761)
   - Service discovery
   - Service registration
   - Health monitoring

5. **Config Server** (Port 8888)
   - Centralized configuration (planned)

6. **Zipkin** (Port 9412)
   - Distributed tracing
   - Performance monitoring

#### Technology Stack Documented

- Java 25
- Spring Boot 2.7.14
- Spring Cloud 2021.0.8
- Debezium 2.3.x - 2.5.x
- PostgreSQL 12+
- Apache Kafka
- Netflix Zuul
- Netflix Eureka
- Maven

#### Identified Technical Debt

- Common module referenced but not implemented
- MyBatis dependencies present but unused
- Redis integration configured but not utilized
- Config Server implemented but not actively used
- Missing Spring Boot Actuator health checks

#### Future Enhancements Documented

- Multi-database CDC support (MySQL, Oracle, SQL Server)
- Custom transformation functions
- Data quality validation
- Web UI for configuration and monitoring
- Schema registry integration
- Dead Letter Queue for failed messages
- Enhanced metrics dashboard

### Files Changed

- `CHANGELOG.md` (new)
- `README.md` (new)
- `PRD.md` (new)
- `ARCHITECTURE.md` (new)
- `SUMMARY_KR.md` (new)

### Issue Resolved

This release addresses the GitHub issue requesting reverse-engineering of the program's purpose and PRD creation. The issue noted: "이 프로그램이 무엇을 하고 싶었던 프로그램인지 역추적하고 PRD 작성. 아마도 데이터베이스 CDC 프로그램이었던 것 같음."

**Confirmation**: Yes, this is a database CDC (Change Data Capture) program, specifically an enterprise-grade ETL and CDC platform for real-time data integration.

### Documentation Statistics

- Total lines of documentation: 1,925
- Total files created: 4
- Total size: ~75 KB
- Languages: English (primary), Korean (summary)

### Related Documents

For more information, see:

- [README.md](README.md) - Quick start guide
- [PRD.md](PRD.md) - Product Requirements Document  
- [ARCHITECTURE.md](ARCHITECTURE.md) - Technical architecture
- [SUMMARY_KR.md](SUMMARY_KR.md) - Korean summary
- Original design notes (Korean) in project files

---

## Notes on Versioning

Since this is documentation work on an existing codebase:

- Version 1.0.0 represents the first documented release
- The actual codebase existed before this documentation
- Future versions will track both code and documentation changes

## Changelog Maintenance

This changelog will be updated:

- When new features are added
- When bugs are fixed
- When documentation is significantly updated
- For each release or milestone

---

**Changelog Version**: 1.0  
**Last Updated**: 2026-08-04  
**Maintained By**: Development Team