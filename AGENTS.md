# AGENTS

This repository permits explicitly authorized autonomous development and maintenance. The current hourly mightyETL commercial loop is an authorized repository writer subject to the safety, review, exact-evidence, and branch-lease rules below. Absence of a human comment immediately before each commit is not a prohibition when the active user/scheduler mandate explicitly authorizes autonomous repository work.

## Repository scope and writer lease

- This loop may mutate **ContextualWisdomLab/mightyETL only**.
- ContextualWisdomLab/.github, contextual-orchestrator, naruon, and repositories with their own enabled dedicated writer loops are read-only dependencies from this writer.
- Before every mightyETL branch/ref/source write, refetch the target PR head, live base tip, exact target blob/ref, and relevant PR/review state.
- Source/ref/base/blob movement or another active write-capable agent targeting the same branch is a **branch-local writer conflict**. Freeze source writes to that branch for the remainder of the invocation, reconcile read-only, and continue safe work on other untouched branches/issues/docs/read-only lanes.
- Review/check/comment completion alone is not a branch writer conflict.
- Never race another writer.

## Work-conserving execution

A diagnosis, blocker, commit, PR update, review request, resolved thread, merge, documentation fix, or finished product slice is an intermediate state while safe work remains.

After every action/defer decision, return to the live queue and select the next highest-value safe item. Pending checks, review latency, rate limits, central dependencies, and external approval block only the affected action. Do not end an invocation by narrating an unchanged blocker while another safe mightyETL task exists.

Before exit, run a second fresh sweep of PRs/issues/branches/reviews/checks/security/stack/docs/release/product gaps. Final output is forbidden while a safe executable repository action remains, subject to practical invocation/tool budget.

## RCA and realistic remediation

For every failed/missing/pending gate or unexpected result:

1. reproduce/refetch the exact first failing boundary;
2. distinguish symptom, immediate cause, technical root cause, systemic/control cause where material;
3. enumerate materially distinct remedies that would change the cause;
4. verify each remedy against current GitHub/API support, permissions, credentials, protection/rulesets, stack order, writer lease, provider state, runtime budget, path ownership, blast radius, rollback, security/coverage/review effects, and an exact acceptance test;
5. classify `execute_now`, `defer_until_trigger`, `read_only_dependency`, `external_only`, or `reject`;
6. execute the smallest highest-impact safe `execute_now` option test-first;
7. rerun the exact failing test/gate and authoritative state;
8. if it fails/no-ops, update the hypothesis and try another distinct safe layer or rotate work.

Never invent a token, reviewer, permission, endpoint, model, secret, or integration. Never blindly repeat a failed mutation.

## Branch-wide exact-parent publication

A Contents API blob SHA is file-level CAS, not branch-wide expected-parent CAS. For a source change whose parent identity matters:

- prepare blobs/tree/commit from the exact live parent;
- immediately reread the branch ref/base before publication;
- publish only as a descendant using a non-forced (`force=false`) ref update;
- if the ref advanced, do not attach the stale commit; freeze/replan the branch.

Never use destructive force push, destructive rebase, `-X ours`, `-X theirs`, self-modifying encoded-patch repair workflows, or rewritten fail-first evidence to make history appear clean.

## Pull requests, stacks, reviews, and merge

- Treat every remembered SHA/check/review/base as historical until refetched.
- Every stacked head must descend from the exact current immediate predecessor.
- Repair the earliest invalid boundary first; replacement branches preserve old fail-first branches/history.
- Old checks, reviews, approvals, statuses, and base snapshots do not transfer across head/base replacement.
- Review human, CodeRabbit, GitHub Advanced Security, Dependabot, OpenCode, Noema, Strix and other feedback as hypotheses; fix only current valid findings.
- Resolve only addressed threads.
- Formal independent non-author approval is required where current mightyETL/CWL governance requires it; COMMENTED/status/text/reaction/author/synthetic evidence does not qualify.
- Never self-approve, synthesize approval, weaken protection/tests/security, or bypass required checks.

## TDD and verification

Production behavior changes use red-green-refactor TDD. A RED test is valid only if it reaches the intended production boundary; setup/import/fixture failure is a test defect.

Expected verification includes, as applicable:

- `./mvnw -B test`;
- exact 100% configured owned-production statement/branch coverage;
- public production docstring/Javadoc coverage;
- migration/rollback/concurrency/security/compatibility tests;
- `git diff --check`;
- exact-source GitHub CI/security/dependency/SBOM/provenance evidence;
- standalone and MSA smoke acceptance.

Skipped-required, queued, pending, neutral-required, absent, cancelled, failed, stale-head, predecessor-head, old-base, status-only, and synthetic-merge-only evidence is not accepted for a gate requiring literal exact-head success.

## Database and data safety

- Owned database object names use at least two descriptive words and snake_case by default.
- Legacy nonconforming names require an explicit safe migration/removal + rollback plan; do not silently rename them.
- Never silently discard accepted ETL rows.
- Preserve transaction/idempotency/lease authority in the database where designed.
- Do not blanket-mask PII needed for legitimate product operation. Use purpose-bound authorization, least privilege, encryption, minimization/retention, auditable privileged access, and non-leaking telemetry/errors.

## Product and architecture truth

Canonical docs are part of the product:

- `PRD.md`, `TRD.md`, `ARCHITECTURE.md`, `SECURITY.md`;
- `docs/adr/README.md` + ADRs;
- `docs/UML.md`, `docs/ERD.md`, `docs/API_CONTRACT.md`;
- `docs/THREAT_MODEL.md`, `docs/TEST_STRATEGY.md`, `docs/OPERABILITY.md`, `docs/TRACEABILITY.md`;
- `CHANGELOG.md`.

A public API, persisted state, lifecycle, trust boundary, deployment, autonomous-authority, compatibility, or release-evidence change updates the affected canonical docs in the same PR. Use `implemented_on_develop`, `active_pr`, `planned`, `superseded`, `out_of_scope`, and `known_gap` truthfully.

Find and remove production demo stubs, hard-coded success, fake integrations, obsolete product names, and keyword-only shortcuts in touched paths. Do not call a scaffold a production connector.

## Autonomous LLM development

- GitHub Actions autonomous development uses an immutably pinned OpenCode Agent with `NVIDIA_NIM_API_KEY` only through GitHub Secrets/provider mapping.
- Never use GitHub Copilot or `COPILOT_GITHUB_TOKEN` for autonomous development.
- Do not alter the independent review-agent credential contract merely to make development work.
- Prefer contextual-orchestrator for LLM-backed product/test integration only when its separate repository writer lease permits changes; otherwise treat it read-only and continue local work.

## Standards, research, and commercial readiness

Use current authoritative standards/primary technical documentation and peer-reviewed research when material, recording APA 7 references in doctoring/ADRs. Design for defensible SOC 2/CSAP acquisition diligence without falsely claiming certification.

Release only from an integrated protected head that passes all required tests, exact coverage, security, migration/rollback, compatibility, packaging, SBOM/provenance, review, approval, operational, and release-acceptance gates. Update `CHANGELOG.md` and verify published artifacts.
