# ADR-0011: Diagnostic and Dead-Letter Data Governance

**Status:** Accepted with known gaps  
**Date:** 2026-08-10

## Context

ETL, connector, JDBC, parser, DDL, CDC, and broker failures can contain raw SQL, provider messages, record identifiers, payload fragments, paths, credentials, customer values, and internal topology. Returning or logging those diagnostics as public errors creates confidentiality and stability risk. Conversely, destructive blanket masking can remove the evidence required to investigate and safely redrive failed enterprise data.

Dead-letter traffic is especially sensitive: it may preserve the exact record and headers that failed, but it must not silently re-enter the normal replica path or live indefinitely without ownership. PRs #170/#171/#172/#174/#176/#211 harden diagnostic boundaries. PRs #192/#197 harden dead-letter confidentiality and terminal routing. These remain active-PR evidence.

## Decision

1. Public API errors and ordinary operator logs use stable product error codes, bounded safe summaries, and scoped correlation identifiers. Raw provider/JDBC/DDL/parser/exception messages, secrets, internal paths, uncontrolled SQL, and customer values are not public contracts.
2. Detailed diagnostics may exist only in a purpose-bound privileged evidence channel with least privilege, encryption, bounded retention, audit, and explicit data classification.
3. **Dead-letter records are terminal quarantine** by default. A DLT record cannot be consumed by the normal apply path or treated as ordinary successful progress.
4. Dead-letter payload, key, topic, selected headers, failure classification, source revision, connector identity, attempt lineage, and creation time are preserved only to the extent required for diagnosis or authorized redrive. Secret-bearing or unnecessary headers are excluded.
5. DLT retention, encryption, access, export, deletion, residency, and incident handling are explicit deployment/data-governance contracts. Broker defaults are not product policy.
6. Redrive is a separate authenticated, authorized, idempotent operation. It validates current schema/policy/connector compatibility, creates new lineage, preserves the original quarantine record until policy permits deletion, and cannot bypass the current production validation path.
7. Correlation identifiers are opaque and bounded. Raw row IDs, principal names, idempotency keys, connector credentials, SQL, payloads, and exception text do not become metric labels.
8. PII is controlled by purpose, authorization, minimization, encryption, retention, audit, and deletion. Blanket masking is rejected when it destroys legitimate ETL/recovery utility.
9. Diagnostic behavior, DLT lifecycle, and redrive semantics require synchronized API/event contracts, Security, Threat Model, Operability, UML, data model, and tests.

## Consequences

- Public error contracts remain stable across provider/library upgrades.
- Privileged diagnosis remains possible without leaking data to ordinary callers or telemetry.
- DLT storage and redrive require explicit operational ownership rather than being a best-effort broker side effect.
- Retaining diagnostic payloads can increase regulated-data obligations and must be justified by purpose and duration.

## Alternatives rejected

- **return `exception.getMessage()` to callers:** unstable and potentially sensitive.
- **log entire failed rows by default:** creates uncontrolled secondary data stores.
- **drop every failed payload immediately:** can make safe diagnosis/redrive impossible.
- **route `.DLT` back through the normal consumer automatically:** creates loops and bypasses authorization/validation.
- **trust broker retention defaults:** does not express product purpose, deletion, export, or residency policy.
- **mask every value irreversibly:** destroys legitimate business and recovery utility.

## Failure and recovery

If privileged evidence cannot be stored securely, the operation fails with a stable non-sensitive error and records only the minimum safe audit state. If quarantine publication fails, source progress must follow the connector's explicit fail-closed policy rather than reporting success. Redrive failure creates bounded attempt evidence and leaves the original quarantine record terminal.

## Security and privacy impact

DLT and privileged diagnostic stores are sensitive data domains. Access must be deployment/tenant/purpose scoped, export auditable, retention bounded, and cryptographic keys separated from payloads. Security incidents involving those stores follow the same notification, preservation, deletion, and recovery controls as primary customer data.

## Compatibility and migration

Existing raw diagnostic text or DLT consumers require inventory before removal. Compatibility may expose a temporary stable-code plus privileged-detail path, but it must not preserve uncontrolled public leakage. Existing DLT records need classification and retention review before a new redrive API is enabled.

## Acceptance evidence

- public error and log non-leakage tests across controller, JDBC, parser, DDL, row, CDC, and DLT paths;
- bounded diagnostic size and metric-cardinality tests;
- terminal DLT routing tests;
- unauthorized/expired/wrong-tenant redrive rejection;
- idempotent redrive and immutable lineage tests;
- retention/deletion/export/recovery runbook evidence;
- exact-source CI, complete security/dependency evidence, non-vacuous coverage, independent review, and protected operational proof.

## Supersession

Supersede only with a reviewed data-governance design that preserves stable public diagnostics, purpose-bound privileged evidence, terminal quarantine, authorized lineage-preserving redrive, and enforceable lifecycle controls.