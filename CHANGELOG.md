# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Invalid `AMOUNT` values fail closed before persistence instead of being rewritten to `0.00`, preserving the distinction from a genuine zero.
- Durable `POST /api/etl/jobs` submissions now return RFC 9110 `202 Accepted`, a stable pending-job representation, `Location` status-monitor metadata, and explicit replay metadata without changing the synchronous `/api/etl/process` contract. The incomplete intake controller is fail-closed and requires explicit `xtrmetl.etl.jobs.intake-enabled=true` operator opt-in until worker execution and terminal payload clearing are implemented.
- Concurrent requests using the same authenticated-principal-scoped semantic idempotency key now return immediate RFC 9457 `409 etl_idempotency_request_in_progress` responses through PostgreSQL `pg_try_advisory_xact_lock`; retries after completion still replay the committed response.
- `POST /api/etl/process` now supports optional authenticated-principal-scoped `Idempotency-Key` retries with atomic target writes, durable response replay, payload-conflict rejection, and explicit replay response metadata.
- `Idempotency-Key` now prefers the quoted RFC 9651 Structured Field String representation while retaining and normalizing the legacy raw representation to the same durable ledger key.
- ETL request errors now use RFC 9457 `application/problem+json` responses with a stable `errorCode`, fixed type URI, explicit 400/401/404/409/413/422/503/500 taxonomy, and no internal exception text in client responses.
- ETL requests now enforce bounded UTF-8 payload and record-count limits, prevalidate and transform the complete batch before the first JDBC call, and commit accepted records inside one Spring transaction.
- ETL transformations now preserve comma/colon-bearing values, use locale-independent text conversion and deterministic `BigDecimal` amount formatting, and retry only transient Spring data-access failures.
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
- Scaffold CDC sources: `mysql-debezium`, `sqlserver-debezium` (discovery only).
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