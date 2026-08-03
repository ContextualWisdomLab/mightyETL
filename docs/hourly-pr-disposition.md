# Hourly pull-request disposition loop

mightyETL runs `.github/workflows/hourly-pr-disposition.yml` at minute 11 of every hour and on manual dispatch.

The workflow automates only the merge-disposition portion of the maintenance loop. It does not invent product changes, bypass reviews, or execute code from pull-request branches.

## Eligibility gates

A pull request is merged only when all of the following hold:

1. The base branch is `develop` and the pull request is not a draft.
2. The author is the configured trusted maintainer and the head branch belongs to this repository.
3. No `do-not-merge`, `manual-merge`, `security-review`, or `breaking-change` label is present.
4. Workflow changes are excluded unless a maintainer explicitly applies `automerge-workflow`.
5. The latest review state for every reviewer contains no `CHANGES_REQUESTED` decision.
6. Every required CI, dependency, SBOM, SAST, and security check has reported and completed successfully, neutrally, or as intentionally skipped.
7. Commit status contexts are successful and GitHub reports the pull request as cleanly mergeable.
8. The merge request includes the expected head SHA, preventing a time-of-check/time-of-use merge after the branch moves.

Eligible pull requests are squash-merged. GitHub branch protection remains authoritative and can still reject a merge.

## Security properties

- The scheduled workflow runs from the protected default branch, not from untrusted pull-request code.
- It does not check out or execute pull-request contents.
- It uses the repository-scoped `GITHUB_TOKEN` with only `contents`, `pull-requests`, `checks`, and `statuses` permissions.
- External forks and untrusted authors are never merged unattended.
- Changes to workflow files require a separate explicit label and therefore remain manual by default.

## Scope boundary

The hourly workflow disposes eligible pull requests. Product discovery, implementation, review remediation, release decisions, and buyer-gap analysis still require an authenticated development agent or maintainer session. This separation prevents a cron job from manufacturing or approving unreviewed code while keeping a green, fully reviewed queue from remaining open unnecessarily.
