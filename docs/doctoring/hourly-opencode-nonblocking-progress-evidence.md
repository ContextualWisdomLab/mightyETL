# Hourly OpenCode nonblocking progress evidence

Reviewed on: **2026-08-08**

## Incident

The scheduled maintenance contract correctly refused to treat queued checks, missing approvals,
synthetic-merge-only scanner output, and separately leased repository state as passing evidence.
However, it did not distinguish those external-only waits from repository-local source defects.
An external gate could therefore leave the hourly development loop repeatedly reporting the same
blocker without selecting safe, independent mightyETL work.

This was an orchestration defect, not permission to weaken merge policy. The correct behavior is:

1. keep every unavailable or stale gate non-passing;
2. repair the earliest valid repository-local finding first;
3. never deepen an invalid stack;
4. when no open pull request is source-actionable, select at most one independent bounded slice
   from protected `develop` that does not depend on or overlap the invalid stack; and
5. keep separately leased repositories read-only.

## Decision

`AGENTS.md` now defines `source-actionable` narrowly. A pull request is source-actionable only when
its exact current head contains a valid repository-local finding or failing source gate that this
repository can repair. Review latency, approval latency, queued checks, synthetic-only scanner
identity, or an unintegrated read-only central dependency remain blockers to merge but do not stop
all unrelated mightyETL development.

When no open pull request is source-actionable, the scheduled agent may create exactly one bounded,
non-conflicting candidate from protected `develop`. The candidate must not depend on, retarget,
rewrite, or overlap files changed by the invalid stack. If no such slice exists, the run returns
without a candidate rather than manufacturing activity.

The repository-writer lease is unchanged. `ContextualWisdomLab/.github`, `naruon`,
`contextual-orchestrator`, and every separately leased repository remain read-only to this loop.
The scheduler may inspect their exact integration state but may not mutate them, dispatch a
write-capable agent there, or post a mutation-trigger comment.

## Why `AGENTS.md` is authoritative to OpenCode

OpenCode's project initialization creates a repository-root `AGENTS.md`, and its official
instructions recommend committing that file so OpenCode can understand project structure and
coding patterns. The scheduled workflow checks out protected default-branch source before invoking
plain `opencode run`, so the repository instruction is present at agent startup without adding a
second credential or changing the review-agent contract.

## Test-first evidence

### RED

Commit `7ae7dcc2446c5a6bacd75a3602b83e8ea1f6f3f2` added
`HourlyOpenCodeProgressPolicyTest` before the policy existed. Literal-head CI run `31256917651`
checked out that exact SHA. macOS job `93101583785` ran 313 tests and failed exactly the two new
policy tests:

- `continuesOneBoundedNonConflictingSliceWhenNoPullRequestIsSourceActionable`;
- `preservesReadOnlyDependencyLeasesWhileContinuingLocalWork`.

The failure was caused by absent scheduler policy text, not compilation, unrelated production code,
or a stale checkout. That commit and run remain permanent fail-first evidence.

### GREEN implementation

Commit `8bac86c8bcdd981a9b88d888bc6cfbabb8d1ee53` added the minimal AGENTS contract required by the
failing tests. Checks from the RED head do not transfer. The exact current head must complete its own
CI, dependency, SBOM, SAST, security, commit-status, review-thread, and independent-approval gates
before merge.

## Safety properties

- External-only blockers remain not passing.
- No approval, review, check, or scanner evidence is synthesized.
- No protected branch is written directly.
- No invalid downstream stack boundary is deepened.
- At most one independently reviewable candidate may be produced per run.
- Candidate paths must be disjoint from the invalid stack's changed paths.
- Central and separately leased repositories remain read-only.
- The existing NVIDIA NIM and review-agent credential contracts are unchanged.

## Failure behavior

If an agent cannot prove that a proposed slice is independent of every invalid stack item, it must
produce no candidate. If any relevant head, base, open-PR set, or automation branch changes during
the run, deterministic publication continues to fail closed. If a later repository instruction
conflicts with the workflow's permission boundary, the narrower no-write and no-merge controls win.

## Rollback

Rollback removes this policy section and its regression test together only after replacing them
with a stricter reviewed mechanism that still preserves both properties: unavailable gates are not
passing, and external latency does not halt unrelated bounded work. Do not roll back by allowing
stack deepening, central-repository mutation, direct pull-request lifecycle operations, or stale
check reuse.

## References

GitHub. (n.d.). *Workflow syntax for GitHub Actions*. GitHub Docs. Retrieved August 8, 2026, from
https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax

OpenCode. (2026). *Intro*. https://opencode.ai/docs
