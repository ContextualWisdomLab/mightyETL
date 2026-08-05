# Changelog

All notable changes to mightyETL are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Lease-fenced durable ETL execution across replicas with deterministic PostgreSQL
  `FOR UPDATE SKIP LOCKED` claiming, process and per-claim fencing, expiry reclaim, bounded attempts,
  exact-live-lease transitions, and terminal payload clearing.
- Hashed durable execution identity and domain-separated reuse of `etl_idempotency_records`, so
  response replay, target writes, and `SUCCEEDED` commit atomically without retaining or
  reconstructing raw principals or raw client idempotency keys.
- Stable durable-worker failure classifications, finite-cardinality outcome and duration metrics,
  migration/rollback evidence, contention and stale-lease rollback tests, and the operator runbook
  `docs/operations/durable-job-worker.md`.
- A separate fail-closed hourly OpenCode maintenance workflow pinned to OpenCode 1.18.13 and
  `nvidia/qwen/qwen3-coder-480b-a35b-instruct`, using the existing `NVIDIA_NIM_API_KEY` through
  OpenCode's `NVIDIA_API_KEY` provider variable while preserving the independent review agent and
  deterministic merge-disposition workflow.
- Principal-scoped durable asynchronous ETL job intake and owner-scoped status resources, Flyway
  `etl_job_records` migration, deterministic replay/conflict coverage, and the authoritative
  contract `docs/etl/durable-job-intake.md`.
- Durable synchronous idempotency ledger migration, PostgreSQL transaction advisory-lock adapter,
  deterministic concurrency/rollback coverage, and `docs/etl/idempotent-retries.md`.
- RFC 9457 ETL problem-details contract in `docs/api/problem-details.md`.
- Operator-configurable ETL admission limits under `mightyetl.etl.*` and supported `xtrmetl.etl.*`
  aliases, backed by bounded environment variables.
- ETL transaction rollback integration coverage and `docs/etl/bounded-atomic-batches.md`.
- Connector contract and documentation scaffolds for Qlik Sense, Databricks, and Snowflake.
- Any-to-any CDC design notes, source and target SPI scaffolds, status/source/target APIs, replication
  slot lag evidence, health indicators, and operations documentation.
- Product upgrade progress tracking and the preferred `mightyetl.*` configuration namespace with
  supported compatibility aliases.

### Changed

- The hourly pull-request disposition loop now requires at least one non-author approval anchored to
  the exact current head SHA; stale approvals, comment-only reviews, and absence of requested changes
  cannot authorize unattended merge.
- The hourly OpenCode workflow now scopes write permissions to its maintenance job, installs an
  immutable checksum-pinned release archive, validates the exact archive shape, and uses a removable
  repository-local GitHub CLI credential helper while retaining `persist-credentials: false`.
- The managed Jackson component set now uses the patched 2.21.5 BOM, closing CVE-2026-54515,
  CVE-2026-59889, and GHSA-mhm7-754m-9p8w while keeping managed artifacts aligned.
- Durable `POST /api/etl/jobs` submissions return RFC 9110 `202 Accepted`, `Location` status-monitor
  metadata, stable replay metadata, and fail-closed intake activation without changing synchronous
  `/api/etl/process` behavior.
- The durable job worker and all worker configuration aliases remain disabled by default. Operators
  may independently enable intake or execution for controlled drain and maintenance procedures.
- Concurrent synchronous idempotency requests use PostgreSQL transaction advisory locks and return
  deterministic RFC 9457 conflict responses instead of waiting without a client-visible bound.
- `POST /api/etl/process` supports authenticated-principal-scoped idempotency keys, atomic target and
  response-ledger writes, response replay, and payload-conflict rejection.
- `Idempotency-Key` prefers the RFC 9651 quoted Structured Field String representation while retaining
  the normalized legacy safe-ASCII representation.
- ETL request errors use non-sensitive RFC 9457 `application/problem+json` responses with stable
  error codes and explicit HTTP taxonomy.
- ETL admission validates the complete bounded UTF-8 batch before the first JDBC write, preserves
  punctuation-bearing values, uses locale-independent conversion and deterministic decimal
  formatting, and retries only transient data-access failures.
- User-facing documentation and recommended image tags use **mightyETL**. Legacy Java packages,
  Maven artifact identifiers, and selected environment/topic defaults remain compatibility surfaces
  documented in `docs/rebrand-name-matrix.md`.

### Security

- Durable-worker telemetry excludes payloads, raw principals, raw idempotency keys, internal hashes,
  job and lease identifiers, SQL, exception messages, and unbounded exception labels.
- Exact payload-digest and response-ledger conflicts fail closed with
  `etl_job_integrity_failure`; stale workers cannot commit target, ledger, or terminal-state effects.

## [1.0.0] - 2026-01-08

### Added

- Initial reverse-engineered product documentation: `README.md`, `PRD.md`, `ARCHITECTURE.md`, and
  `SUMMARY_KR.md`.
- Baseline documentation for the Java/Spring microservice architecture, PostgreSQL ETL path,
  Debezium-based CDC path, Kafka publication, service discovery, gateway routing, and Zipkin tracing.

### Known baseline limitations

- Several connector and multi-source capabilities were documented or scaffolded rather than live.
- Config Server, Redis, and selected dependencies were present without complete production usage.
- Operational health, security, idempotency, bounded admission, and durable asynchronous execution
  required the later unreleased hardening documented above.
