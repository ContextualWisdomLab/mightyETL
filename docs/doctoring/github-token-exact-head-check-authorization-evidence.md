# GitHub-token exact-head check authorization evidence

## Decision

The hourly development workflow uses the repository-scoped `GITHUB_TOKEN` rather than a personal
access token or GitHub App installation token. When OpenCode creates or updates a same-repository
pull request with that token, mightyETL must explicitly authorize the resulting approval-required
pull-request workflow runs before treating the agent's work as ready for review.

Authorization is limited to starting validation for the exact current head. It is not pull-request
approval, review, merge authority, branch-protection bypass, or evidence that any check succeeded.
The OpenCode process itself never receives Actions write authority.

## Platform behavior

GitHub prevents most events created with `GITHUB_TOKEN` from recursively starting another workflow.
For pull requests opened, synchronized, reopened, or updated through GitHub Actions, GitHub creates
pull-request workflow runs in an approval-required state rather than granting an automated writer an
unreviewed recursive execution path. The workflow-run approval endpoint requires Actions write
permission.

Without a bounded authorization step, a scheduled OpenCode run could push a valid fix while leaving
the new exact head without CI, SAST, SBOM, dependency, or security execution. That would break the
required review → fix → exact-head revalidation loop even though the source change itself was valid.

## Split-job authority model

The workflow separates mutable development from run authorization:

```text
maintain-repository job
  ├─ checks out protected default-branch source
  ├─ installs and executes OpenCode
  ├─ may prepare a feature branch and pull request
  ├─ has actions: read
  └─ outputs only the pre-agent pull-request head map

                job output boundary
                         ↓

authorize-exact-head-checks job
  ├─ never checks out or executes repository code
  ├─ receives no NVIDIA model credential
  ├─ has actions: write, contents: read, pull-requests: read
  ├─ compares before/after exact heads
  └─ authorizes only approval-required exact-head workflow runs
```

This prevents generated code, repository scripts, or the OpenCode process from using Actions write
permission. The sole privileged job consumes only GitHub API metadata and a compact JSON map of pull
request numbers to commit SHAs produced before the agent starts.

## Fail-closed authorization algorithm

The protected default-branch workflow performs the following steps:

1. Before OpenCode starts, snapshot the exact heads of all same-repository pull requests targeting
   `develop` and expose that compact object as the maintenance job's output.
2. After the maintenance job completes or fails without cancellation, start the isolated
   authorization job.
3. Require the prior output to exist and parse as a JSON object.
4. Enumerate the same pull-request set again.
5. Select only a new pull request or a pull request whose head changed during this run.
6. Refuse automatic run authorization when the pull request changes any path below `.github/` or
   any `CODEOWNERS` file. Those policy changes require explicit human authorization.
7. Read the still-current pull-request head and require it to equal the selected expected SHA.
8. Discover only `pull_request` workflow runs whose `head_sha` equals that expected SHA.
9. Fail visibly when no exact-head run materializes.
10. Re-read the pull-request head immediately before authorization and reject a moved head.
11. Approve only exact-head runs in `action_required` or `waiting` state.
12. Leave check execution, review, mergeability, branch protection, and expected-head merge
    disposition to their existing independent gates.

The isolated job runs even when OpenCode fails, provided the workflow was not cancelled. This covers
a partial agent session that pushed a branch before later failing. If the pre-run snapshot is absent,
run discovery fails, the head moves, a policy file changed, no run appears, or GitHub rejects
authorization, the workflow fails instead of reporting successful revalidation.

## Authority and credential boundary

The workflow-level permission remains `contents: read`. The maintenance job retains only the
minimum branch, pull-request, issue, check, status, security-read, and Actions-read permissions needed
for development. The separate authorization job receives the workflow's only `actions: write`
permission plus read-only contents and pull-request metadata access. No personal token, GitHub App
token, OIDC token, or additional repository secret is introduced.

The authorization job never checks out the repository, never executes repository files, and never
receives `NVIDIA_API_KEY`. Its Actions write permission is used solely for the workflow-run approval
endpoint. The implementation contains no pull-request review approval command and no merge API call.
The OpenCode prompt continues to forbid approval, merge, protected-branch push, branch-protection
bypass, review-agent modification, and unauthorized workflow-policy changes.

## Test-first evidence

`HourlyOpenCodeMaintenanceWorkflowTest` first required the following contracts before production
implemented them:

- a pre-agent exact-head snapshot exported as a job output;
- the absence of Actions write authority from the OpenCode job;
- exactly one isolated authorization job with Actions write permission;
- no checkout or NVIDIA credential in that authorization job;
- same-repository and `develop` targeting;
- refusal of `.github/**` and `CODEOWNERS` changes;
- exact-head workflow-run discovery;
- two head-SHA time-of-check/time-of-use validations;
- explicit failure when no run materializes;
- authorization through the workflow-run endpoint only;
- continued absence of pull-request approval and merge operations.

The production workflow then implemented those contracts with job outputs, `gh api`, canonical JSON
processing through `jq`, and an exact SHA passed as a jq argument rather than interpolated into jq
source.

## Verification checklist

Reviewers must verify on the exact current pull-request head that:

- the scheduler still runs only from protected default-branch workflow source;
- the snapshot precedes the OpenCode process and is the only cross-job mutable evidence;
- the maintenance job has `actions: read`, not `actions: write`;
- the authorization job is the only job with `actions: write`;
- the authorization job has no checkout, repository-code execution, or NVIDIA credential;
- only heads changed by that run are considered;
- `.github/**` and all `CODEOWNERS` paths are excluded from automatic authorization;
- both current-head reads equal the expected head;
- run discovery filters `event=pull_request` and the exact head SHA;
- only waiting or action-required runs reach the approval endpoint;
- absent runs and authorization failures make the workflow fail;
- no review approval, merge, protected-branch push, or secret fallback was added;
- every authorized run must still complete successfully before merge disposition can proceed.

## Rollback

If GitHub changes the approval-required run model or the endpoint becomes unavailable, disable the
hourly development workflow. Do not remove the exact-head authorization contract while leaving the
agent able to push changes with `GITHUB_TOKEN`, because that recreates unvalidated agent heads.

Do not move `actions: write` back into the OpenCode job. A replacement based on a GitHub App may
remove the isolated authorization job only after its installation permissions, recursive-trigger
behavior, actor identity, secret lifecycle, exact-head workflow evidence, and independent review
boundary are documented and tested through a separate pull request.

## References — APA 7th

GitHub, Inc. (2026). *GITHUB_TOKEN*. GitHub Docs.
https://docs.github.com/en/actions/concepts/security/github_token

GitHub, Inc. (2026). *Triggering a workflow*. GitHub Docs.
https://docs.github.com/en/actions/using-workflows/triggering-a-workflow

GitHub, Inc. (2026). *REST API endpoints for workflow runs*. GitHub Docs.
https://docs.github.com/en/rest/actions/workflow-runs
