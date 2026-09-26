# ADR-0010: Service and Configuration Identity Authority

**Status:** Accepted with known gaps  
**Date:** 2026-08-10

## Context

mightyETL exposes several independent trust boundaries: external clients entering the gateway, clients or workloads reaching ETL directly, CDC control operations, service discovery, Config Server repository access, operator interfaces, databases, brokers, and external connectors. Protected develop has placeholder or incomplete controls at multiple boundaries. A gateway JWT does not authenticate downstream service calls, Eureka registration is not workload authorization, and repository configuration is not trustworthy merely because a URL exists.

PR #142 is the active gateway Resource Server path. Issue #161 owns direct/east-west ETL identity. Issue #185 owns registry identity, issue #187 owns CDC control-plane authentication, and PR #189 owns explicit Config Server repository authority.

## Decision

1. **No trust boundary inherits another boundary's authentication.** Each reachable service or control plane defines and verifies its own accepted identity, audience, purpose, and authority.
2. Gateway authentication authorizes entry to the gateway only. Gateway→ETL/CDC calls require a supported downstream service-identity or token-exchange/relay contract; direct service exposure must independently fail closed.
3. Principal identity, workload/service identity, operator identity, and tenant authority are distinct concepts. One value must not be silently reused as proof of another.
4. Eureka registration/discovery metadata is routing information, not authorization. Registration, query, and management operations require explicit identity and least privilege when exposed beyond a trusted deployment boundary.
5. Config Server obtains configuration only from an explicit, approved repository authority with fail-closed startup behavior. Example/fallback repositories, mutable unauthenticated scripts, and implicit environment selection cannot become production authority.
6. CDC start, stop, status, source/target discovery, and future redrive operations require an explicit control-plane identity separate from event payload provenance.
7. Service credentials are referenced through narrowly scoped configuration/secret handles, never emitted to logs, model prompts, API responses, metrics, or repository files.
8. Standalone deployment remains supported. A service may use a deployment-local identity mechanism, but its contract and limitations must be explicit and tested rather than inferred from network location.
9. Trust changes require synchronized PRD/TRD/Architecture/UML/Security/Threat Model/API/Operability/Traceability updates and protected operational proof.

## Consequences

- Compromise or misconfiguration at one boundary does not automatically authorize another.
- Gateway-only demos cannot be described as end-to-end service authentication.
- Deployment configuration becomes more explicit and may require additional workload credentials or mesh/OIDC integration.
- Standalone and composed MSA modes can use different mechanisms while preserving the same fail-closed semantic contract.

## Alternatives rejected

- **trust all internal network traffic:** network placement is not workload identity.
- **reuse end-user bearer token everywhere without audience/purpose controls:** expands replay and confused-deputy risk.
- **treat Eureka registration as authentication:** discovery data is not authorization evidence.
- **allow Config Server example fallback:** gives an unintended repository configuration authority.
- **self-asserted tenant or service headers:** callers cannot manufacture authority.
- **one shared broad credential for every service:** violates least privilege and impairs audit/revocation.

## Failure and recovery

Unknown issuer/audience, missing workload identity, unavailable trusted configuration source, expired credential, invalid registry registration, or unverified CDC operator request fails closed with stable non-sensitive diagnostics. Recovery changes the failing credential/configuration boundary, proves the exact identity path, and reruns direct plus composed negative/positive acceptance; it does not bypass the service.

## Security and governance impact

Identity metadata and credentials have separate classifications. Subject/tenant/workload identifiers are bounded audit data; secrets remain protected values. Logs contain stable event codes and scoped correlation identifiers, not raw tokens, passwords, private keys, repository credentials, or customer payloads.

## Compatibility and migration

Existing placeholder tokens, HTTP Basic, anonymous registry/config access, and direct service exposure remain `known_gap` until their exact replacement integrates. Migration inventories callers and deployment profiles, introduces a fail-closed compatibility window where justified, provides rollback without reopening anonymous authority, and updates client/operator runbooks.

## Acceptance evidence

- source-backed trust-boundary inventory;
- registered runtime security-chain tests rather than helper-only tests;
- direct ETL, gateway-routed ETL, CDC control, Eureka, and Config Server positive/negative integration tests;
- wrong audience/issuer/purpose/workload/tenant rejection;
- credential non-leakage tests;
- standalone and composed deployment smoke tests;
- exact-source CI, complete scanner/SBOM evidence, non-vacuous coverage, independent review, and protected-develop operational proof.

## Supersession

Supersede only with a reviewed identity architecture that preserves explicit boundary ownership, least privilege, standalone/MSA compatibility, revocation, audit, and negative-path evidence.