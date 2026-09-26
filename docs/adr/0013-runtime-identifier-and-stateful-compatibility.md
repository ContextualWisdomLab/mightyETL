# ADR-0013: Runtime Identifier and Stateful Compatibility

**Status:** Accepted with known gaps  
**Date:** 2026-08-10

## Context

The product brand is mightyETL, while protected source and runtime surfaces still include historical `xtrmETL` identifiers. Those identifiers are not one homogeneous string. Java packages, Maven coordinates, Spring configuration prefixes, environment variables, service names, Kafka topics, Debezium connector names, replication slots/publications, database objects, metrics, trace service names, Docker resources, API paths, and artifact coordinates have different compatibility, ownership, and rollback semantics.

A bulk search-and-replace can orphan state, create duplicate consumers, split metrics, break configuration, or make rollback impossible. PR #191 is the active inventory path and remains unshipped.

## Decision

1. **Runtime identifiers migrate by semantic category**, not by global text replacement.
2. Brand/display names may change independently from stable protocol, package, state, and configuration identifiers when compatibility risk requires it.
3. Before any rename, inventory each identifier's owner, consumers, persisted/external state, uniqueness scope, security meaning, compatibility promise, migration mechanism, rollback, and observability impact.
4. Stateful identifiers—including Kafka topics/consumer groups, Debezium connector names, replication slots/publications, database schemas/tables, Docker volumes, secret/config keys, and durable artifact paths—require explicit migration and collision evidence. A documentation rename does not move state.
5. Configuration transitions use versioned aliases only when needed. Reads may accept a bounded legacy alias; writes and generated examples use one canonical identifier. Alias use is observable, non-secret, documented, and has a removal criterion.
6. Java package/Maven/artifact/API renames preserve compatibility through deliberate major-version or adapter strategy. Relocated code and coordinates must not produce ambiguous duplicate classes/artifacts.
7. Metrics/traces/log fields preserve continuity through explicit old→new mappings and cardinality review; identifiers containing customer, principal, job, payload, or secret data are not introduced as labels.
8. Migration order is dependency-aware: inventory → accepted mapping → compatibility implementation → dual-read/alias where justified → state migration → verification → canonical write → deprecation → removal.
9. Rollback is defined before cutover and does not rely on destructive force, state overwrite, or hidden dual writers.
10. Active-PR and planned identifiers remain labeled as such in PRD/TRD/Architecture/UML/ERD/API/Operability/Traceability; a new product name never implies state migration already occurred.

## Consequences

- Renaming is slower but auditable and recoverable.
- Some historical identifiers may remain intentionally stable until a major compatibility boundary.
- Operators get explicit manifests, warnings, and cutover evidence instead of silent drift.
- Duplicate broker/database/service state and observability fragmentation become tested failure modes.

## Alternatives rejected

- **repository-wide search/replace:** ignores external and persisted consumers.
- **rename only display text and claim completion:** leaves runtime truth ambiguous.
- **write both old and new state indefinitely:** creates split-brain and duplicate effects.
- **drop legacy identifiers immediately:** breaks installed environments without evidence.
- **reuse one compatibility alias for every category:** package, config, broker, DB, and metrics require different controls.

## Failure and recovery

If inventory is incomplete, migration does not start. If dual-read/cutover detects collision, divergence, duplicate consumption, or missing state, writers stop at the earliest safe boundary, evidence is preserved, and the documented rollback restores the previous canonical writer/read path. No force-push or destructive state rewrite is used to disguise partial migration.

## Security and governance impact

Identifier manifests may expose topology and deployment metadata, so access is bounded. Secret values remain separate from identifier names. Renames must preserve authorization scopes, audit continuity, retention, tenant/deployment boundaries, and SBOM/provenance coordinates.

## Compatibility and migration

PR #191 must bind the actual protected source and deployment inventory. Every subsequent implementation PR names its category, active consumers, compatibility period, migration/rollback commands, and exact acceptance evidence. Historical `xtrmETL` values remain truthful compatibility data until their category-specific cutover is complete.

## Acceptance evidence

- machine-readable identifier inventory and mapping;
- source/config/package/API compatibility tests;
- Kafka/Debezium/replication state migration rehearsals;
- database and Docker volume collision/rollback tests;
- metric/trace continuity and cardinality checks;
- fresh-install, upgrade, downgrade/recovery and mixed-version scenarios;
- exact-source CI, complete security/SBOM evidence, non-vacuous coverage, independent review, and protected operational acceptance.

## Supersession

Supersede only with a reviewed compatibility/versioning policy that covers all stateful and external identifier categories with equivalent inventory, migration, observability, and rollback evidence.