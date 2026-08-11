# Hourly OpenCode nonblocking progress evidence

Reviewed on: **2026-08-08**

## Incident

The scheduled maintenance contract correctly refused to treat queued checks, missing approvals,
synthetic-merge-only scanner output, and separately leased repository state as passing evidence.
However, the first repair placed the nonblocking policy only in root `AGENTS.md`. The runtime
`PROMPT` passed directly to `opencode run` still selected work using these older conditions:

- act on a pull request whenever a dependency-eligible development pull request exists; and
- develop a new product slice only when no development pull request exists.

That made the behavior operationally incomplete. An open pull request blocked only by an external
review, approval, scanner identity, central dependency, quota, or runner condition could still be
mistaken for source-actionable work. The agent could repeatedly observe the same blocker without
performing root-cause analysis, testing whether a proposed remedy was executable in the current
run, or selecting an independent feasible product slice.

This was an orchestration defect, not permission to weaken merge policy. The runtime contract must:

1. keep every unavailable or stale gate non-passing;
2. identify the root cause rather than restating a symptom;
3. generate bounded remedies that address the identified cause;
4. test each remedy against live authority, protection, capacity, dependency, and ownership
   constraints;
5. execute and verify the best safe option that is feasible now;
6. preserve an external or infeasible gate as blocked while immediately selecting the next safe
   feasible non-overlapping action; and
7. never deepen an invalid stack or cross a repository-writer lease.

## Root-cause analysis

The immediate root cause was **instruction-path drift**. The repository guidance and the actual
scheduled model prompt no longer expressed the same work-selection state machine. The prompt used
open-PR existence as the branch condition, while the intended policy required source-actionability.

The following are explicitly not root causes:

- a queued check by itself;
- a repeated retry that reproduces the same state;
- an aggregate-green workflow whose relevant evidence uses a synthetic merge revision;
- an independent approval that does not yet exist; or
- a protected dependency that this repository is not authorized to mutate.

Those are observations or external boundaries. A valid RCA traces them to the responsible source,
configuration, permission, quota, runner, provider, dependency, or policy boundary.

## Decision

Both root `AGENTS.md` and the runtime OpenCode prompt now require the same bounded loop:

```text
observe exact current state
→ perform RCA
→ generate cause-addressing options
→ classify feasibility
→ execute the highest-impact safe feasible option
→ rerun the exact failing test or gate
→ verify the condition changed
→ otherwise keep that gate fail-closed and continue with the next feasible action
```

A remediation is classified as one of:

- **executable now** — current repository authority, tooling, runtime, compute budget, dependency
  state, path ownership, and writer lease permit the action;
- **requires an external actor** — a human reviewer, organization policy owner, protected central
  repository, provider, entitlement administrator, or other authority must act; or
- **unsafe or infeasible** — the action would bypass protection, exceed bounded resources, race
  another writer, mutate an unleased repository, deepen an invalid stack, or cannot be verified.

The agent executes only the first category. The second and third categories do not become passing
evidence. They also do not halt unrelated work when a safe independent slice exists.

A pull request is source-actionable only when its exact current head contains a valid
repository-local finding or failing source gate that this repository can repair. Review latency,
approval latency, queued checks, synthetic-only scanner identity, or an unintegrated read-only
central dependency remain blockers to merge but do not stop unrelated mightyETL development.

When no open pull request is source-actionable, even if blocked pull requests remain open, the
scheduled agent may create exactly one bounded, non-conflicting candidate from the unchanged
protected `develop` head. The candidate must not depend on, retarget, rewrite, or overlap files
changed by a blocked or invalid stack. If no such slice exists, the run returns without a candidate
rather than manufacturing activity.

The repository-writer lease is unchanged. `ContextualWisdomLab/.github`, `naruon`,
`contextual-orchestrator`, and every separately leased repository remain read-only to this loop.
The scheduler may inspect their exact integration state but may not mutate them, dispatch a
write-capable agent there, or post a mutation-trigger comment.

## Why feasibility is an execution gate

A technically plausible remedy is not necessarily executable. For example:

- changing a protected central workflow from this repository violates the writer lease;
- manufacturing an approval violates independent-review policy;
- treating a synthetic merge scan as literal-head evidence does not repair source identity;
- repeatedly rerunning an unavailable provider does not address entitlement or quota; and
- opening another stacked branch can worsen an invalid dependency graph.

The scheduler therefore checks feasibility before writing. This turns “find a solution” into a
bounded decision that can actually be executed and verified under current conditions.

## Test-first evidence

### Initial RED: nonblocking progress policy

Commit `7ae7dcc2446c5a6bacd75a3602b83e8ea1f6f3f2` added
`HourlyOpenCodeProgressPolicyTest` before the nonblocking policy existed. Literal-head CI run
`31256917651` checked out that exact SHA. macOS job `93101583785` ran 313 tests and failed exactly
the two initial policy tests:

- `continuesOneBoundedNonConflictingSliceWhenNoPullRequestIsSourceActionable`;
- `preservesReadOnlyDependencyLeasesWhileContinuingLocalWork`.

The failure was caused by absent scheduler policy text, not compilation, unrelated production code,
or a stale checkout. That commit and run remain permanent fail-first evidence.

### Current RED: RCA and realistic feasibility

Commit `dba0af66481e7ba98fd16b19792feebd7fb71e1b` added two additional contract tests before the
runtime workflow prompt was changed. Literal-head CI run `31259348638` checked out that exact SHA.
macOS job `93107620623` ran 315 tests and failed exactly these two new tests:

- `performsRootCauseAnalysisAndFeasibilityClassificationBeforeActing`;
- `executesTheBestFeasibleActionAndContinuesAfterExternalOnlyBlockers`.

The existing 313 tests, including the earlier nonblocking and lease tests, passed. This proves the
new failure was specifically the missing runtime RCA and feasibility contract rather than a broad
repository regression.

### Implementation and refactor

Commit `ab3d5b28353289b8d2f3789ce06b63b506d37b8a` added the minimal runtime prompt changes required
by the current RED tests. During exact diff inspection, an unrelated `needs` identifier typo caused
by whole-file workflow publication was found before it could be accepted. Commit
`36c87525a95bfeedebd604c545af4d9427271984` restored the original exact-head authorization
expression. The corrective commit changes only that one identifier.

Root guidance was then aligned with the runtime prompt so future agents see the same RCA,
feasibility, execution, verification, and fallback state machine. Checks from every predecessor
head are stale. The final exact current head must complete its own CI, dependency, SBOM, SAST,
security, commit-status, review-thread, and independent-approval gates before merge.

## Safety properties

- External-only blockers remain not passing.
- RCA must identify a responsible boundary rather than relabel a symptom.
- Proposed remedies are tested against current authority and operational constraints before use.
- Only a safe option classified executable now may be performed.
- The exact failing test or gate is rerun after remediation.
- An external or infeasible preferred option does not stop the next independent feasible action.
- No approval, review, check, or scanner evidence is synthesized.
- No protected branch is written directly.
- No invalid downstream stack boundary is deepened.
- At most one independently reviewable candidate may be produced per run.
- Candidate paths must be disjoint from blocked and invalid stack paths.
- Central and separately leased repositories remain read-only.
- The existing NVIDIA NIM and review-agent credential contracts are unchanged.

## Failure behavior

If the scheduler cannot trace a blocker to evidence, it must not guess at a destructive remedy. If
an option requires authority unavailable to the current run, that option remains external rather
than being retried as though it were executable. If the preferred option is infeasible, the run
continues to the next safe feasible non-overlapping action. If no safe action exists, it performs
read-only analysis and emits no candidate.

If an agent cannot prove that a proposed slice is independent of every blocked or invalid stack
item, it must produce no candidate. If any relevant head, base, open-PR set, or automation branch
changes during the run, deterministic publication continues to fail closed. If repository guidance
conflicts with the workflow permission boundary, the narrower no-write and no-merge controls win.

## Rollback

Rollback removes the RCA and feasibility prompt, aligned repository guidance, and regression tests
together only after replacing them with a stricter reviewed mechanism that preserves all of these
properties: unavailable gates are not passing; remedies address evidenced causes; feasibility is
checked before execution; exact verification follows action; and external latency does not halt
unrelated bounded work. Do not roll back by allowing stack deepening, central-repository mutation,
direct pull-request lifecycle operations, or stale check reuse.

## References

GitHub. (n.d.). *Workflow syntax for GitHub Actions*. GitHub Docs. Retrieved August 8, 2026, from
https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax

OpenCode. (2026). *Intro*. https://opencode.ai/docs
