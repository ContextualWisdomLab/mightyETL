# ADR-0006: Exact-Source Evidence, Separated Agent Authority, and Branch-Wide Writer CAS

**Status:** Accepted  
**Date:** 2026-08-09

## Context

GitHub pull-request workflows can execute a generated merge revision by default. Autonomous development also needs to persist a candidate without giving the untrusted model the ability to approve, merge, or mutate protected branches. File-level Contents API compare-and-swap protects one blob but can silently incorporate an unrelated concurrent branch commit.

## Decision

1. Literal-source gates explicitly checkout/verify the exact pull-request head when governance requires source identity.
2. `synthetic-merge` results are compatibility evidence, not literal-head proof.
3. OpenCode model execution uses `NVIDIA_NIM_API_KEY` and read-only GitHub authority.
4. Branch publication, PR mutation, Actions authorization, independent review, and merge are separately permissioned deterministic authorities.
5. Repository writer leases are branch-local: concurrent movement freezes only the affected branch for that invocation; other safe mightyETL work continues.
6. Exact-parent branch publication prefers Git Data blob/tree/commit construction from the exact reread parent followed by non-forced (`force=false`) ref update. If the ref moved, publication fails/replans.
7. `.github`, naruon, contextual-orchestrator and other dedicated writer-loop repositories are read-only dependencies from this loop.
8. RCA must produce materially distinct remedies, verify real-world feasibility, execute safe options, rerun the failing gate, then rotate instead of stopping on the first infeasible remedy.

## Consequences

- model compromise has a narrower blast radius;
- exact source/provenance claims are auditable;
- one blocked PR/check/reviewer does not idle the run;
- no force-push or self-modifying temporary repair workflow is needed to fake linear ancestry/evidence.

## Alternatives rejected

- **give model `contents: write`:** expands untrusted authority.
- **treat aggregate green as exact-head regardless of checkout:** evidence laundering.
- **file blob SHA as branch-wide lease:** misses concurrent changes to other files.
- **repository-wide stop after one branch conflict:** wastes safe executable work.
