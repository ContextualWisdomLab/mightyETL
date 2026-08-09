# ADR-0008: Purpose-Bound PII Controls Instead of Blanket Masking

**Status:** Accepted  
**Date:** 2026-08-09

## Context

ETL/CDC frequently exists to move business records that include personal or identifying data. Blanket masking at ingestion can destroy legitimate data-processing utility, while uncontrolled copying/logging of PII creates regulatory and security risk.

## Decision

mightyETL does not impose blanket PII masking on authorized business payloads. Instead it requires:

- purpose-bound authentication/authorization;
- tenant/owner isolation where the API owns tenancy semantics;
- encrypted transport and deployment-appropriate encryption at rest;
- least-privilege database/connector credentials;
- retention/minimization appropriate to durable payload/ledger purpose;
- auditable privileged access and exports;
- non-leaking errors/logs/metrics;
- hashes treated as pseudonymous internal security data, not automatically anonymous;
- explicit connector/data-residency policy for external systems.

## Consequences

- legitimate ETL/CDC workflows remain useful;
- privacy controls move to access, retention, audit, and purpose rather than destructive data mutation;
- product documentation must not imply that hashing/masking alone satisfies privacy obligations.

## Alternatives rejected

- **blanket masking:** can make ETL results unusable and break referential/business semantics.
- **no privacy controls because ETL needs raw data:** unacceptable least-privilege/audit posture.
