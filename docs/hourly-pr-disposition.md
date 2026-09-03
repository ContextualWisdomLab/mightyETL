# Pull-request maintenance and manual disposition

mightyETL runs `.github/workflows/hourly-pr-maintenance.yml` at minute 17 of every hour and on manual dispatch. The repository-local caller is intentionally small: it delegates the queue to the centrally governed `ContextualWisdomLab/.github` reusable scheduler pinned to commit `6eb06cdd08c79a06f7b390069d4ffa49e2eb7dba`.

The central route performs the operational sequence needed to keep the pull-request queue moving:

1. inspect up to 100 open pull requests targeting `develop`;
2. request missing current-head reviews within the bounded run budget;
3. update eligible outdated branches;
4. re-evaluate current-head reviews, review threads, checks, commit statuses, and mergeability;
5. use policy-governed direct merge or auto-merge only when the protected repository rules permit it.

Branch protection, expected-head semantics, exact-head review evidence, and required checks remain authoritative. A scheduled run cannot manufacture an approval, reinterpret a failed or pending check as successful, or bypass a repository rule.

## One scheduled authority

`.github/workflows/hourly-pr-maintenance.yml` is the only scheduled pull-request maintenance authority in this repository. It uses single-flight concurrency with cancellation disabled, so a later hourly tick cannot discard an in-progress queue evaluation.

The former `.github/workflows/hourly-pr-disposition.yml` remains available through `workflow_dispatch` only as a **manual fail-closed fallback**. It is intentionally unscheduled so there is **no duplicate scheduled merge authority** racing the central review, branch-update, and merge scheduler.

Use the manual fallback only when the central reusable workflow is unavailable and an operator needs to disposition already reviewed, already green pull requests. It does not request reviews, repair code, update branches, or execute pull-request contents.

## Central caller security properties

- The reusable workflow is referenced by immutable commit SHA rather than a mutable branch or tag.
- The caller grants only `actions`, `checks`, `contents`, `id-token`, and `pull-requests` permissions required by the central scheduler.
- The caller does not inherit repository secrets.
- `NVIDIA_NIM_API_KEY` and independent reviewer credentials are not passed to the merge scheduler.
- The central scheduler obtains its bounded GitHub App authority through its own OIDC exchange and validates the live target repository, base branch, pull-request number, and current head before mutation.
- Neither the caller nor the manual fallback checks out or executes untrusted pull-request code.

## Manual fallback eligibility gates

The manual fallback merges a pull request only when all of the following hold:

1. The base branch is `develop` and the pull request is not a draft.
2. The author is the configured trusted maintainer and the head branch belongs to this repository.
3. No `do-not-merge`, `manual-merge`, `security-review`, or `breaking-change` label is present.
4. Workflow changes are excluded unless a maintainer explicitly applies `automerge-workflow`.
5. For each reviewer, the most recent decisive review (`APPROVED` or `CHANGES_REQUESTED`) is evaluated; comment-only reviews cannot erase an outstanding change request.
6. Every current, non-outdated review thread is resolved.
7. Every named required CI, dependency, SBOM, SAST, and security check has completed with `success`; a skipped required check is not sufficient.
8. No other reported check has failed or remains pending, commit status contexts are successful, and GitHub reports the pull request as cleanly mergeable.
9. The merge request includes the expected head SHA, preventing a time-of-check/time-of-use merge after the branch moves.

Eligible pull requests are squash-merged. GitHub branch protection can still reject a merge; that rejection affects only the current pull request and does not abort inspection of the remaining queue.

## Scope boundary

The hourly caller and manual fallback dispose pull-request work that already exists. Buyer-gap discovery, test-first implementation, scientific validation, release decisions, and product development remain separate authenticated development-agent responsibilities. This separation keeps merge authority deterministic while allowing the OpenCode development loop to use `NVIDIA_NIM_API_KEY` without giving a model credential to the scheduler.
