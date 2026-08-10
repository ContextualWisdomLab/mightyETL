# ADR-0014: Tenancy and Data-Lifecycle Authority

**Status:** Proposed  
**Date:** 2026-08-10

## Context

Protected develop has meaningful principal-scoped ownership for idempotency and durable-job records, but its data sources, connector configuration, credentials, Kafka/CDC namespaces, registry/configuration services, databases, backups, metrics, and deployment controls are predominantly service-global. Principal hashing protects selected records; it does not define a product tenant or prove cross-customer isolation.

An enterprise ETL product can be defensible as one trusted tenant per deployment, database, broker, and credential boundary. It may also evolve toward shared-runtime multi-tenancy, but that requires end-to-end tenant authority across every access and data path. Ambiguity is unsafe because buyers can infer a stronger isolation claim than the code provides. Issue #186 tracks the decision.

## Decision under review

**Principal scoping is not tenant isolation.** Until this ADR is accepted with a selected option and implementation evidence, mightyETL makes no shared-runtime multi-tenant isolation claim.

### Option A — single tenant per deployment

One customer/security tenant owns each service runtime plus its PostgreSQL, Kafka/CDC namespace, connector credentials, secrets, encryption keys, audit, backup/restore, retention, and release/upgrade boundary. Principals and workload identities operate within that tenant. Multiple customers use separately isolated deployments or a host-owned orchestrator with explicit versioned instance boundaries.

This is the lowest-risk near-term option given protected global configuration and persistence.

### Option B — first-class shared-runtime multi-tenancy

An authenticated tenant context is bound to principal/workload identity and propagated through every owned persistence record, idempotency/job namespace, connector/credential lookup, CDC/Kafka topic and consumer identity, registry/configuration access, audit, quotas, metrics/logs, backup/export/deletion, migration, and API authorization. Storage isolation may use database, schema, row-level, or dedicated-resource controls only after a complete access-path and failure-domain analysis.

## Required decision rules

1. A caller cannot self-assert tenant authority through an unsigned header or request field.
2. Adding `tenant_id` to selected tables is insufficient while global connector, broker, credential, DLT, backup, or operator state remains shared.
3. Tenant authority, principal identity, workload/service identity, deployment authority, and data-purpose authorization remain distinct.
4. Data classification, retention, export, deletion, residency, encryption, audit, DLT, backup/restore, and incident response use the same chosen tenant unit.
5. Cross-tenant and wrong-deployment requests fail closed through public APIs and direct storage/control paths.
6. Standalone operation remains supported and MSA integration uses explicit versioned identity/context rather than hidden shared-database coupling.
7. Migration from Option A to Option B requires compatibility, data movement, key/credential, rollback/forward-recovery, and mixed-version evidence.
8. No documentation may describe owner-scoped idempotency/jobs as proof of general tenant isolation.

## Consequences

- The current product truth remains honest while a buyer-facing tenancy boundary is decided.
- Option A offers strong isolation with higher per-tenant operational overhead.
- Option B offers shared infrastructure but creates extensive authorization, data, broker, connector, recovery, observability, and migration obligations.
- Product, security, deployment, packaging, support, and pricing assumptions depend on the selected option.

## Alternatives rejected

- **principal hash equals tenant:** conflates user/workload ownership with customer security boundary.
- **tenant header without verified authority:** lets callers manufacture scope.
- **partial table-level tenant IDs:** leaves global side channels and credentials unisolated.
- **Docker network alone:** network segmentation is not complete data/control isolation.
- **blanket masking:** does not enforce authorization, retention, deletion, or resource ownership.
- **implicit one-tenant convention:** undocumented conventions are not buyer or operator contracts.

## Failure and recovery

Until acceptance, deployments use one trusted customer/security domain and do not co-host mutually untrusted tenants. Any suspected cross-boundary access stops affected processing, preserves bounded audit evidence, revokes/rotates relevant credentials, scopes backup/restore and deletion to the chosen tenant unit, and runs realistic isolation regression tests before reactivation.

## Security, privacy, and legal impact

Tenant choice governs customer data separation, processor/controller obligations, encryption/key management, data residency, retention/deletion, access logs, incident scope, support access, backup/export, and contract claims. It cannot be selected solely as a code convenience.

## Acceptance required before `Accepted`

- explicit selection of Option A, Option B, or a versioned transition with product non-goals;
- source-backed identity/data/control-flow inventory;
- PRD/TRD/Architecture/Security/Threat Model/UML/ERD/Operability/Traceability alignment;
- realistic cross-boundary negative tests for APIs, databases, connector credentials, CDC/Kafka, DLT, registry/configuration, logs/metrics, backup/restore, export/deletion, and operator actions;
- migration, rollback/forward-recovery, support and release evidence;
- exact-source CI, complete security/SBOM evidence, non-vacuous repository-wide coverage, independent review, and protected-develop operational acceptance.

## Supersession

Once a tenancy option is accepted and implemented, update this ADR to `Accepted` or supersede it with a versioned decision that preserves explicit identity, data lifecycle, migration, and operational boundaries.