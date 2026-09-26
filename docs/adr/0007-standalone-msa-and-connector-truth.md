# ADR-0007: Standalone/Modular MSA Operation and Honest Connector Capability

**Status:** Accepted  
**Date:** 2026-08-09

## Context

mightyETL is both an ETL/CDC product and a reusable CWL service. Forcing the full service mesh for every use harms modular adoption. Conversely, advertising connector scaffolds as production integrations creates buyer risk.

## Decision

- ETL and CDC services remain independently operable with only the dependencies required by their enabled feature path.
- Gateway/Eureka/Config/tracing are composable infrastructure, not mandatory for every standalone deployment.
- Connector registries/catalogs expose runtime capability and scaffold state honestly.
- PostgreSQL remains the protected-develop production ETL load path; raw PostgreSQL Debezium→Kafka remains the protected live CDC path.
- A connector becomes a production write target only with credentials/configuration contracts, integration/domain-validity tests, failure/idempotency semantics, operability/rollback, and release evidence.

## Consequences

- services can be adopted independently or as MSA modules;
- future CWL integration uses stable APIs/SPIs instead of hidden source coupling;
- catalogs are safe for procurement/operations because support level is explicit.

## Alternatives rejected

- **full-stack-only deployment:** unnecessary coupling.
- **documentation-only production support:** confuses scaffold with runtime capability.
