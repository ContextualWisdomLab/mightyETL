# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Canonical product and acquisition-diligence documentation now reconciles protected `develop` with the actual bounded atomic ETL, principal-scoped idempotency, durable asynchronous intake, CDC delivery/lifecycle gaps, gateway identity gap, active durable-job stack, exact-source evidence requirements, autonomous writer-lease/CAS rules, standalone/MSA operation, and PII control strategy; historical authentication/parallel-processing designs are explicitly marked superseded instead of shipped.
- Repository agent guidance now treats a source/ref conflict as branch-local, requires work-conserving RCA → feasible remediation → execution → exact proof, and keeps unrelated safe mightyETL work active rather than stopping after one blocker or one completed action.
- Durable `POST /api/etl/jobs` submissions now return RFC 9110 `202 Accepted`, a stable pending-job representation, `Location` status-monitor metadata, and explicit replay metadata without changing the synchronous `/api/etl/process` contract. The incomplete intake controller is fail-closed and requires explicit `xtrmetl.etl.jobs.intake-enabled=true` operator opt-in until worker execution and terminal payload clearing are implemented.
- Concurrent requests using the same authenticated-principal-scoped semantic idempotency key now return immediate RFC 9457 `409 etl_idempotency_request_in_progress` responses through PostgreSQL `pg_try_advisory_xact_lock`; retries after completion still replay the committed response.
- `POST /api/etl/process` now supports optional authenticated-principal-scoped `Idempotency-Key` retries with atomic target writes, durable response replay, payload-conflict rejection, and explicit replay response metadata.
- `Idempotency-Key` now prefers the quoted RFC 9651 Structured Field String representation while retaining and normalizing the legacy raw representation to the same durable ledger key.
- ETL request errors now use RFC 9457 `application/problem+json` responses with a stable `errorCode`, fixed type URI, explicit 400/401/404/409/413/422/503/500 taxonomy, and no internal exception text in client responses.
- ETL requests now enforce bounded UTF-8 payload and record-count limits, prevalidate and transform the complete batch before the first JDBC call, and commit accepted records inside one Spring transaction.
- ETL transformations now preserve comma/colon-bearing values, use locale-independent text conversion and deterministic `BigDecimal` amount formatting, and retry only transient Spring data-access failures.
- Product branding: user-facing docs and suggested image tags use **mightyETL** (formerly xtrmETL). Legacy Java packages (`com.xtrmetl.*`), Maven `artifactId` `xtrmETL`, and some env/topic defaults remain for compatibility; see `docs/rebrand-name-matrix.md`.

### Added

- Canonical ADR, UML, ERD, API-contract, threat-model, test-strategy, operability, traceability, and documentation-assessment entry points with machine-checkable documentation contracts and explicit `implemented_on_develop` / `active_pr` / `planned` / `superseded` / `out_of_scope` / `known_gap` status semantics.
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

- Comprehensive initial documentation suite (2026-01-08): `README.md`, `PRD.md`, `ARCHITECTURE.md`, `SUMMARY_KR.md`, and `CHANGELOG.md`.
- The 2026-01 reverse-engineering snapshot described JWT/RBAC, per-record parallel processing, and local auth endpoints that later source reconciliation showed were not current shipped contracts. Those statements are retained as historical provenance only; canonical 2026-08 documentation supersedes them.

## [1.0.0] - 2026-01-08

### Project Documentation Initiative

Version 1.0.0 records the first documented reverse-engineering baseline of the pre-existing xtrmETL codebase. Its detailed historical assumptions are preserved in repository history. Current product truth is defined by the exact protected source plus the canonical documentation graph listed in `README.md`.

### Documentation added

- `README.md`
- `PRD.md`
- `ARCHITECTURE.md`
- `SUMMARY_KR.md`
- `CHANGELOG.md`

### Historical product interpretation

The project was identified as an enterprise ETL/CDC platform using Spring services, PostgreSQL, Debezium, Kafka, Eureka, Config Server, and Zipkin. Subsequent protected-source work changed the ETL transaction/idempotency behavior and disproved parts of the historical authentication/parallel-processing narrative. Those later changes are recorded in `[Unreleased]` above.

## Changelog maintenance

Update this file when product behavior, public API/persistence, security/trust boundary, compatibility, operational/release contract, or canonical architecture governance changes.
