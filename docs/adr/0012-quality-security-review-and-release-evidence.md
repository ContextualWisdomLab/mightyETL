# ADR-0012: Quality, Security, Review, and Release Evidence Authority

**Status:** Accepted with known gaps  
**Date:** 2026-08-10

## Context

GitHub exposes multiple evidence channels: contributor source head, pull-request base snapshot, live protected base, generated synthetic merge, workflow checkout, check run, commit status, scanner result, SBOM, model judgment, formal review, merge decision, release artifact, and protected-runtime observation. Collapsing those identities into one green badge permits stale, vacuous, incomplete, or synthetic evidence to authorize a merge or release it did not actually prove.

Protected coverage can analyze zero classes and still satisfy zero-missed thresholds. A scanner can return zero findings while Maven dependency versions or child dependencies remain unresolved. Synthetic merge checks can prove integration compatibility but not literal source identity. Issues #162, #196, and #205 and PRs #121/#164 capture those defects. Issues #151/#165 capture licensing and release authority gaps.

## Decision

1. **A green aggregate is not a release authority.** Each required gate preserves its own subject revision, input completeness, tool/policy version, conclusion, and authority.
2. Contributor `source_head_sha`, `pr_base_snapshot_sha`, independently resolved `live_base_tip_sha`, synthetic merge revision, and workflow checkout revision are distinct evidence identities.
3. Coverage applies percentage or zero-missed thresholds only after proving a non-empty intended production set. A focused set does not establish repository-wide scope; every owned module/package must be inventoried, selected non-vacuously, aggregated, or explicitly justified as generated/third-party/out-of-scope.
4. Security evidence fails closed when dependency resolution is incomplete, source materialization is stale/ambiguous, the scanner did not execute, or the accepted policy/tool identity is absent. Zero findings are meaningful only over a complete declared subject.
5. Dependency Review, SAST, hard vulnerability scan, SBOM, provenance, reproducibility, formal review, model judgment, and protected operational proof remain separate controls. One does not infer another.
6. COMMENTED reviews, statuses, checks, reactions, model verdicts, author reviews, dismissed/predecessor-head reviews, and textual acknowledgements are not qualifying independent formal approval.
7. Merge requires the unchanged exact source head, current live base/ancestry, every applicable deterministic gate, zero valid unresolved findings, and qualifying independent non-author approval where governance requires it.
8. Release requires one exact integrated protected head plus package/image build and install/run smoke, SBOM and provenance bound to exact artifacts, reproducibility evidence, licensing/NOTICE authority, migration/rollback/recovery, protected operational acceptance, and publication/rollback verification.
9. Active-PR, synthetic, external-provider, or dated evidence may support diagnosis and design but cannot be relabeled as shipped protected truth.
10. Certification/conformance/acquisition claims require their own authorized evidence; passing repository checks does not imply CSAP, SOC 2, ISO, or legal approval.

## Consequences

- Evidence is more auditable and less vulnerable to stale-head or aggregate-status laundering.
- Some historically green PRs remain non-passing until literal subject and input completeness are proven.
- Release pipelines must preserve artifact identity across build, SBOM, provenance, publication, and verification.
- Reviewer capacity and legal/licensing decisions remain genuine governance dependencies rather than bot statuses.

## Alternatives rejected

- **accept aggregate Security Scan green:** hides job/source/input distinctions.
- **treat 0/0 coverage as 100%:** vacuous truth is not product evidence.
- **treat zero findings with unresolved dependencies as clean:** incomplete scan universe.
- **transfer old-head reviews/checks:** violates exact subject identity.
- **let a model approve its own change:** destroys independent governance.
- **publish first and backfill provenance later:** breaks exact artifact/source binding.
- **invent a license to unblock release:** legal authority is external to automation.

## Failure and recovery

A missing, stale, skipped, cancelled, neutral-required, synthetic-only, incomplete, or failed gate remains non-passing. The loop performs RCA, repairs the earliest causal boundary, regenerates evidence on the unchanged exact subject, and rotates to other work while external approval or provider capacity waits. A released artifact with broken identity/provenance is quarantined or withdrawn according to the release runbook.

## Security and governance impact

Checks and logs avoid secrets and customer payloads while preserving immutable evidence receipts. Scanner policy, allowlists, suppressions, reviewer eligibility, release credentials, OIDC trust, signing keys, and publication roles are least-privilege governed assets. Exceptions are reviewed, time-bounded, source-specific, and never implicit.

## Compatibility and migration

Existing workflows may continue to emit synthetic integration evidence, but documentation and merge logic must label it honestly. PR #121, PR #164, issues #196/#205, and central read-only dependencies are migration paths; no old evidence transfers when source, base, workflow, policy, or artifact identity changes.

## Acceptance evidence

- tests for exact source/live-base/synthetic identity separation;
- fail-closed zero-class and repository-wide ownership coverage tests;
- fail-closed incomplete dependency-graph scanner tests;
- SBOM/provenance/artifact digest and outside-source install/run verification;
- formal reviewer eligibility and exact-reviewed-head checks;
- licensing/NOTICE consistency checks without autonomous license choice;
- migration/recovery and protected operational acceptance;
- publication plus independent artifact/source verification.

## Supersession

Supersede only with a reviewed evidence model that preserves or strengthens subject identity, non-vacuity, input completeness, independent approval, artifact provenance, legal authority, and protected-runtime acceptance.