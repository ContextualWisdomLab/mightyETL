# Machine-readable contracts

This directory contains release-facing interoperability contracts for behavior implemented on the protected mightyETL integration branch. These files are product interfaces, not aspirational roadmaps.

## Authority model

- `openapi/mightyetl.yaml` describes HTTP behavior implemented on protected `develop` only.
- `asyncapi/mightyetl-cdc.yaml` describes the live PostgreSQL Debezium → Kafka publication boundary implemented on protected `develop` only.
- An endpoint, header, lifecycle state, delivery guarantee, authentication mode, connector, or event transformation that exists only on an open pull request must not appear as protected product truth here.
- Source annotations, controller/service behavior, migrations, and executable tests remain the runtime authority. `MachineReadableApiContractTest` binds the checked-in contracts to representative source declarations and fails when material source/contract identity drifts.

## Versioning

Contract `info.version` values identify the protected-development contract line, not an independently releasable semantic-version stream. A release must snapshot these contracts from the exact integrated release head and update them whenever a backward-incompatible public API or event contract changes.

Backward-compatible additions may extend schemas or add operations only after the corresponding implementation is protected-integrated. Breaking changes require an explicit migration/deprecation plan, affected-client analysis, release notes, and the repository's normal independent review and release gates.

## Failure and delivery claims

The OpenAPI contract uses RFC 9457 `application/problem+json` only for ETL failures that the current `EtlApiProblemHandler` actually owns. Framework-owned, gateway-owned, and CDC text-error behavior are not silently normalized into that schema.

The AsyncAPI contract deliberately describes replay-tolerant **at-least-once** CDC behavior. Protected `CdcService` currently forwards raw Debezium JSON to Kafka without waiting for acknowledgement before returning from its event handler. Stronger acknowledgement-before-source-progress behavior belongs to its separately reviewed implementation and must not be claimed here until protected integration proves it.

## Validation

Normal repository CI executes the Java contract tests. A future external OpenAPI/AsyncAPI validator may be added only when its version, supply-chain provenance, syntax support, and failure semantics are immutably governed; absence of such a tool is not grounds for weakening the current source-bound contract tests.
