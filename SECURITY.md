# Security Policy and Engineering Security Contract

**Protected integration branch:** `develop`  
**Last reconciled:** 2026-08-09

This file combines vulnerability-reporting policy with the repository-level security contract. It does not claim SOC 2, CSAP, ISO/IEC 27001, or other certification that has not actually been audited and awarded. Architecture/control evidence should nevertheless be written so later certification diligence can map it without reverse-engineering source history.

## Supported versions and branches

Security fixes are developed through protected pull requests targeting `develop`, which is the repository default integration branch. A versioned release is supported according to its published release notes and vulnerability policy; historical feature branches are not supported production lines.

| Surface | Supported for current development |
| --- | --- |
| protected `develop` | Yes |
| versioned supported releases | According to release notes |
| arbitrary feature/repair branches | No |
| historical/superseded stack branches | No |

`main` is not silently treated as the current development source of truth merely because older documentation used that convention.

## Reporting a vulnerability

Do not open a public GitHub issue for a suspected vulnerability before maintainers can assess exposure.

Preferred channels:

1. GitHub private vulnerability reporting in the repository Security tab.
2. If private reporting is unavailable, contact repository maintainers privately and disclose the minimum reproduction evidence needed for triage.

Include:

- affected release/commit and component;
- reproducible source-to-sink path or proof of concept where safe;
- confidentiality/integrity/availability impact;
- preconditions and likely blast radius;
- known mitigation/workaround;
- whether secrets or personal/business data may have been exposed.

## Response objectives

- acknowledgement target: within 2 business days;
- initial severity/impact triage: within 5 business days;
- status update target: at least weekly while remediation is active.

These are response objectives, not contractual SLA guarantees unless a commercial support agreement says otherwise.

## Security architecture status

### Gateway identity — `known_gap`

Protected `develop` still contains a placeholder class named `JwtAuthenticationFilter` whose validator recognizes the literal example token `valid_token`. This is not cryptographic JWT verification and must not be represented as production authentication.

PR #142 is the `active_pr` remediation using Spring Security reactive OAuth 2.0 Resource Server JWT configuration plus a fail-closed standalone deny mode. Production deployments must restrict protected-develop gateway exposure until an accepted identity boundary is integrated/configured.

Historical `/auth/signin`, `/auth/signup`, local password/BCrypt designs are `superseded` product concepts even though the Docker bootstrap still contains legacy `users`, `roles`, and `user_roles` tables.

### ETL owner/idempotency boundary — `implemented_on_develop`

- keyed ETL and durable-job operations require a runtime-authenticated `Principal`;
- raw principals and raw idempotency keys are not stored in durable ledgers;
- owner-scoped durable-job status uses the principal-derived hash as an independent predicate;
- malformed/missing/foreign-owned resource identifiers use the same not-found classification;
- client problems/logs/metrics exclude payloads, raw principals, keys, hashes, SQL, credentials, lease identifiers, and internal exception text unless a narrowly authorized diagnostic channel explicitly requires internal data.

### CDC — known delivery/lifecycle gaps

- PR #139 is `active_pr` for Kafka acknowledgement before Debezium source progress plus finite future waiting.
- Issue #141 is `planned` for truthful graceful stop completion.
- Until integrated, do not claim exactly-once end-to-end CDC delivery or that the ordinary stop response proves Debezium `run()` termination.

## PII and sensitive business data

mightyETL cannot remain useful if all personally identifying/business-critical fields are destroyed by blanket masking. The security/privacy contract is instead:

- purpose-bound authentication and authorization;
- least-privilege database, Kafka, connector, and support access;
- encrypted transport and deployment-appropriate encryption at rest;
- minimum necessary retention and bounded durable payload lifetime;
- auditable privileged access/export operations;
- tenant/owner isolation where the product owns tenancy semantics;
- no raw payload/identity/key values in ordinary metrics and errors;
- hashes classified as pseudonymous internal security data rather than automatically anonymous;
- data-residency/processor obligations documented for external connectors.

This is a control strategy, not legal advice or a certification claim.

## GitHub / autonomous-agent security

PR #121 is the `active_pr` scheduler/security design. Until merged, it is not protected-develop runtime.

Governing authority principles:

- model execution uses only `NVIDIA_NIM_API_KEY` for LLM access;
- `COPILOT_GITHUB_TOKEN` is not an autonomous-development credential;
- the model/source-reading job has read-only GitHub authority;
- deterministic branch publication, PR mutation, Actions authorization, independent review, and protected merge are separate authorities;
- a reviewer/model/status/check cannot synthesize a formal independent approval;
- branch publication validates exact predecessor/base, path/commit bounds, non-destructive ancestry, and post-write SHA;
- exact-parent branch publication uses branch-wide compare-and-swap semantics, preferably Git Data commit construction + non-forced `force=false` ref update;
- another writer moving one branch freezes source writes to that branch for the invocation, not all repository work;
- dedicated `.github`, naruon, contextual-orchestrator and other repository loops are read-only dependencies from the mightyETL writer.

## CI/security evidence

Before merge/release, inspect the gates applicable to the exact current source/head/base:

- CI/test/coverage;
- Dependency Review;
- CycloneDX SBOM;
- SAST/Semgrep/CodeQL or configured equivalents;
- hard filesystem/container/security scanners;
- secret scanning;
- formal reviews/unresolved review threads;
- commit statuses;
- migration/rollback/compatibility evidence;
- provenance/release acceptance.

A green aggregate produced from a generated pull-request merge revision does not become literal-head evidence by description. `queued`, `pending`, skipped-required, neutral-required, absent, cancelled, failed, stale-head, predecessor-head, old-base, status-only, and synthetic-merge-only evidence remain non-passing for gates requiring exact source proof.

## Current workflow references

Repository security/quality automation includes, as present on the exact branch being evaluated:

- `.github/workflows/ci.yml`;
- `.github/workflows/dependency-review.yml`;
- `.github/workflows/sbom.yml`;
- configured SAST/security workflows under `.github/workflows/`;
- Dependabot configuration under `.github/dependabot.yml` when present.

Do not rely on this prose instead of inspecting live workflow files; workflow names and central reusable dependencies can evolve.

## Secure development requirements

- use red-green-refactor TDD for security fixes;
- use parameterized SQL and validated dynamic identifiers/DDL boundaries;
- keep payload/batch/retry/concurrency bounds explicit;
- preserve exact transaction/lease ownership semantics;
- pin immutable workflow/action/release inputs where practical and policy-required;
- keep 100% configured owned-production statement/branch coverage;
- document public production APIs and security consequences;
- perform threat-model/ADR updates for changed trust boundaries;
- preserve migration/recovery evidence;
- do not bypass branch protection, independent review, required checks, or writer leases to clear a queue.

## Compliance-readiness posture

The architecture should make later SOC 2/CSAP/enterprise diligence defensible through evidence for access control, change management, vulnerability management, logging/audit, backup/recovery, encryption, supplier/dependency controls, incident response, least privilege, and release provenance. No document may label the product certified/compliant without the corresponding external/organizational evidence.

## Disclosure and remediation expectations

- coordinate public disclosure with maintainers;
- prioritize user/customer mitigation and supported-release fixes;
- preserve incident/release SHA and SBOM/provenance evidence;
- avoid publishing live exploit details before reasonable remediation time;
- do not close a finding merely because a scanner thread is resolved—the underlying condition/control disposition must remain truthful.

## Local parity checks

When hosted CI is unavailable, repository scripts can provide supporting evidence:

- macOS/Linux: `./scripts/ci.sh`;
- Windows PowerShell: `./scripts/ci.ps1`.

Local evidence never substitutes for a required protected GitHub gate unless repository policy explicitly says it does.

## References — APA 7th

National Institute of Standards and Technology. (2020). *Security and privacy controls for information systems and organizations* (NIST SP 800-53 Rev. 5). https://doi.org/10.6028/NIST.SP.800-53r5

Souppaya, M., Scarfone, K., & Dodson, D. (2022). *Secure Software Development Framework (SSDF) Version 1.1* (NIST SP 800-218). National Institute of Standards and Technology. https://doi.org/10.6028/NIST.SP.800-218
