# Exact-head CI, SBOM, and Dependency Review source evidence

## Incident: source-executing checks

The `CI` and `SBOM (CycloneDX)` pull-request workflows previously relied on the default `actions/checkout` ref. GitHub defines `GITHUB_SHA` for a `pull_request` event as the last commit on the generated pull-request merge branch, and the default checkout therefore materializes that synthetic merge revision rather than the pull request's literal current head.

Run `31175322160` demonstrated the mismatch on pull request #121. The macOS job checked out synthetic merge commit `95f543c80dbfc43796179c12d8ceda3196cb9eeb`, whose message merged source head `9bbd20b42967b8401776d9399cec6a5c24aa4512` into base `622e5e6c3d534f230c390f10e3832efadfc01825`. The checkout also retained its repository credential. Those results could describe merge-preview compatibility, but they were not direct execution evidence for the exact source head and could not satisfy mightyETL's expected-head policy.

## Decision: source-executing checks

Both source-executing workflows now bind checkout to:

```yaml
ref: ${{ github.event.pull_request.head.sha || github.sha }}
persist-credentials: false
```

For `pull_request`, the expression selects the literal contributor head. For `push` and the existing manual test entrypoint, the pull-request payload is absent and the expression selects the event SHA. Each workflow immediately compares `git rev-parse HEAD` with the same expression and fails before toolchain setup when the materialized source does not match.

The CI matrix performs this assertion on Ubuntu, macOS, and Windows. Self-hosted execution uses Bash on Unix and PowerShell on Windows but retains the identical expected SHA. SBOM generation applies the same boundary before Maven resolves the aggregate dependency graph.

## Test-first evidence: source-executing checks

Commit `9bbd20b42967b8401776d9399cec6a5c24aa4512` added only `ExactHeadWorkflowCheckoutTest`. CI run `31175322160` then failed exactly two new assertions while the established test surface otherwise ran:

```text
ExactHeadWorkflowCheckoutTest.continuousIntegrationChecksOutAndAssertsTheExactSourceRevision
ExactHeadWorkflowCheckoutTest.sbomGenerationChecksOutAndAssertsTheExactSourceRevision
```

The failure log independently exposed the synthetic merge checkout and persisted credential. The production workflow changes were applied only after this RED evidence.

The permanent contract requires:

- the literal pull-request head expression in both workflows;
- checkout credential persistence disabled;
- an explicit post-checkout identity assertion; and
- no hard binding to `github.sha`, which denotes the merge revision on `pull_request` events.

## Incident and decision: dependency-delta evidence

Dependency Review does not execute contributor source, but it evaluates the dependency delta between two revisions. Relying on an action's implicit event defaults makes that evidence less explicit than mightyETL's exact-head gate requires, especially after a stacked predecessor or head moves.

Fail-first commit `077340a62e267f3dfbe05099b137bec57c11a5ae` added `DependencyReviewExactHeadWorkflowTest`, requiring the pinned GitHub Dependency Review Action to receive the pull-request event's exact base and head SHAs. CI run `31177454329` failed on that test while the existing workflow still supplied only `fail-on-severity`.

Green repair `0b947a691a7114a31a3ba11be50c8c3f484ac838` changed `.github/workflows/dependency-review.yml` to pass:

```yaml
with:
  base-ref: ${{ github.event.pull_request.base.sha }}
  head-ref: ${{ github.event.pull_request.head.sha }}
  fail-on-severity: high
```

The workflow remains pinned to `actions/dependency-review-action@a1d282b36b6f3519aa1f3fc636f609c47dddb294` (v5.0.0). The pinned action's `action.yml` declares both `base-ref` and `head-ref` as supported optional inputs, so the repair uses the action's reviewed public contract rather than undocumented behavior.

The permanent Dependency Review contract requires:

- explicit event base SHA passed as `base-ref`;
- explicit event head SHA passed as `head-ref`;
- an immutable action commit pin;
- fail-closed severity policy retained; and
- no reuse of a successful dependency delta after either endpoint moves.

## Authority boundary

These repairs change which exact revisions are measured; they do not convert checks into approval and do not weaken branch protection.

- The workflows remain `pull_request` workflows with repository permission `contents: read` only.
- No repository, model, cloud, deployment, or signing secret is exposed to pull-request source.
- The CI/SBOM checkout credential is removed before Maven or project code executes.
- The workflow definition still follows GitHub's pull-request event semantics; the explicit checkout ref affects the checked-out source tree, not the event or reviewer identity.
- Dependency Review receives exact event endpoint SHAs and remains a distinct dependency-delta control.
- Organization SAST and Security Scan evidence remains independently required and must itself be proven against the exact source head before merge.
- A green generated-merge run from an older head, a predecessor base, a queued run, or an absent workflow is not accepted as exact-head evidence.

This pattern would be unsafe under a privileged `pull_request_target`, `workflow_run`, or comment-triggered workflow that exposes secrets or write authority to untrusted source. mightyETL does not use those privileged event shapes for these source-executing jobs.

## Stack and review consequence

Every change to the root stack head invalidates downstream ancestry and all older check, review, and approval evidence. After this repair passes its current exact head, each downstream branch must be advanced non-destructively to an auditable history containing the exact predecessor head, then rerun its own exact-head gates. No predecessor evidence transfers.

For Dependency Review specifically, any base or head movement also invalidates the previous dependency delta even when the dependency manifest itself appears unchanged. The workflow must rerun with the new event endpoints.

## Operations and rollback

Operators should inspect the checkout log and exact identity step whenever the event payload, checkout action, or trigger changes. A passing source-executing run must show the expected source SHA as the checked-out `HEAD` before Maven execution.

Operators should inspect the Dependency Review run inputs whenever stacked ancestry changes. A passing dependency review must correspond to the event's exact current base and head SHA pair.

Rollback to implicit source checkout or implicit Dependency Review endpoints is prohibited because either restores ambiguous evidence. If exact endpoint binding becomes unavailable, fail the workflow and investigate event metadata or action behavior. Do not substitute an older head, older base, generated merge revision, or manually asserted status.

## References — APA 7th edition

GitHub. (2026a). *Events that trigger workflows*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows

GitHub. (2026b). *Securely using pull_request_target*. GitHub Docs. https://docs.github.com/en/actions/reference/security/securely-using-pull_request_target

GitHub. (2026c). *GITHUB_TOKEN*. GitHub Docs. https://docs.github.com/en/actions/concepts/security/github_token

GitHub. (2026d). *Dependency review action* (v5.0.0, commit a1d282b36b6f3519aa1f3fc636f609c47dddb294) [GitHub Action]. GitHub. https://github.com/actions/dependency-review-action
