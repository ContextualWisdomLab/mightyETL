# ADR-0001: Canonical Documentation and Implementation Status

**Status:** Accepted  
**Date:** 2026-08-09

## Context

mightyETL accumulated detailed feature plans and PR descriptions, while root PRD/TRD/Architecture retained historical authentication and parallel-processing assumptions. ADR/UML/ERD/threat/operability/traceability entry points were missing. A buyer could not determine shipped versus active-PR behavior without reconstructing history.

## Decision

Maintain one canonical documentation graph: PRD, TRD, Architecture, Security, ADR index, UML, ERD, API contract, Threat Model, Test Strategy, Operability, Traceability, AGENTS, CLAUDE and CHANGELOG.

Every capability is labeled `implemented_on_develop`, `active_pr`, `planned`, `superseded`, `out_of_scope`, or `known_gap` where ambiguity is possible. PR/issue/chat descriptions cannot promote capability status by themselves.

## Consequences

- Documentation becomes a merge/release gate rather than a post-hoc narrative.
- Feature PRs must update affected canonical families.
- Historical concepts remain available through `superseded` traceability without masquerading as current truth.
- Machine tests compare canonical claims to source contracts.

## Alternatives rejected

- **PR bodies as architecture records:** mutable, branch-scoped, and difficult for buyers/operators to discover.
- **One monolithic README:** inadequate for decision, behavior, data, security, and operational views.
- **Delete all historical docs:** removes useful rationale and provenance.
