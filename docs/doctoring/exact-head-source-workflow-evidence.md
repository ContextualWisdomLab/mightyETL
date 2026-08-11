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

The permanent source-execution contract requires:

- the literal pull-request head expression in both workflows;
- checkout credential persistence disabled;
- an explicit post-checkout identity assertion; and
- no hard binding to `github.sha`, which denotes the merge revision on `pull_request` events.

## Dependency Review event-endpoint contract

Dependency Review does not execute contributor source. It asks GitHub's dependency-review service to evaluate the dependency delta represented by a pull-request event. The action's contract therefore differs from source-executing CI and SBOM checks.

Fail-first commit `077340a62e267f3dfbe05099b137bec57c11a5ae` originally asserted that the workflow must pass the pull request's base and head SHAs through the action's `base-ref` and `head-ref` inputs. CI run `31177454329` failed that new assertion while the established workflow used only the pull-request event and `fail-on-severity`.

A subsequent repair added those inputs. Review against the current upstream `actions/dependency-review-action` v5 documentation showed that premise to be incorrect: `base-ref` and `head-ref` are supported inputs, but they are **only used for event types other than `pull_request` and `pull_request_target`**. Supplying them to this `pull_request` workflow is therefore ignored and can create false confidence that an explicit binding exists when the action is actually using the event endpoints.

The corrective TDD sequence preserves both findings without rewriting history:

- `077340a62e267f3dfbe05099b137bec57c11a5ae` remains the original fail-first experiment;
- the intervening commits that added explicit `base-ref`/`head-ref` remain auditable as an incorrect repair;
- `cd706f235f9ddda4ee0d7244772453f2f5c934a5` changes the contract test first so ignored PR-event overrides are rejected; with the overrides still present, this is corrective RED evidence;
- `3358b40adaf52cc821e3fce923ecf5af49f77f7f` removes the ignored inputs and returns the workflow to the action's documented pull-request semantics.

The permanent Dependency Review contract requires:

- trigger through `pull_request` so the action obtains the comparison endpoints from the pull-request event;
- immutable pin `actions/dependency-review-action@a1d282b36b6f3519aa1f3fc636f609c47dddb294` (v5.0.0);
- `fail-on-severity: high` retained;
- no `base-ref` or `head-ref` override on the pull-request workflow, because upstream documents those inputs for non-pull-request events only; and
- no reuse of a successful dependency-review run after either the pull-request head or base moves.

This is an evidence correction, not a gate relaxation. The dependency review must still rerun for the current pull-request event after any endpoint change, and queued, pending, absent, skipped, neutral, cancelled, failed, or stale-event evidence is not accepted.

## Authority boundary

These controls change which exact revisions are measured or how their provenance is established; they do not convert checks into approval and do not weaken branch protection.

- The workflows remain `pull_request` workflows with repository permission `contents: read` only.
- No repository, model, cloud, deployment, or signing secret is exposed to pull-request source.
- The CI/SBOM checkout credential is removed before Maven or project code executes.
- The CI/SBOM checkout ref affects the checked-out source tree, not the event or reviewer identity.
- Dependency Review derives its base/head comparison from the pull-request event according to the pinned action's documented contract; mightyETL verifies freshness by accepting only a run associated with the unchanged current pull request rather than by supplying ignored inputs.
- Organization SAST and Security Scan evidence remains independently required and must itself be proven against the exact source head before merge.
- A green generated-merge run from an older head, a predecessor base, a queued run, or an absent workflow is not accepted as exact-head evidence.

This source-checkout pattern would be unsafe under a privileged `pull_request_target`, `workflow_run`, or comment-triggered workflow that exposes secrets or write authority to untrusted source. mightyETL does not use those privileged event shapes for these source-executing jobs.

## Stack and review consequence

Every change to the root stack head invalidates downstream ancestry and all older check, review, and approval evidence. After this repair passes its current exact head, each downstream branch must be advanced non-destructively to an auditable history containing the exact predecessor head, then rerun its own exact-head gates. No predecessor evidence transfers.

For Dependency Review specifically, any base or head movement invalidates the previous dependency delta even when the dependency manifest itself appears unchanged. A fresh pull-request event run must complete for the unchanged current endpoints before merge evidence is accepted.

## Operations and rollback

Operators should inspect the checkout log and exact identity step whenever the event payload, checkout action, or trigger changes. A passing source-executing run must show the expected source SHA as the checked-out `HEAD` before Maven execution.

For Dependency Review, operators should confirm that the run belongs to the current pull request after its latest base/head movement and that the workflow remains a `pull_request` workflow using the immutable action pin. Do not infer stronger evidence from `base-ref`/`head-ref` inputs on a pull-request event because upstream documents those inputs as unused for that event type.

Rollback to implicit CI/SBOM source checkout is prohibited because it restores synthetic-merge-only execution evidence. Reintroducing ignored Dependency Review endpoint overrides is also prohibited because it restores misleading evidence. If the upstream action changes its event/ref contract, fail closed, review the pinned action and primary documentation, update tests and doctoring first, and rerun the current pull request. Do not substitute an older head, older base, generated merge revision, or manually asserted status.

## References — APA 7th edition

GitHub. (2026a). *Events that trigger workflows*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows

GitHub. (2026b). *Securely using pull_request_target*. GitHub Docs. https://docs.github.com/en/actions/reference/security/securely-using-pull_request_target

GitHub. (2026c). *GITHUB_TOKEN*. GitHub Docs. https://docs.github.com/en/actions/concepts/security/github_token

GitHub. (2026d). *Dependency review action* (v5.0.0, commit a1d282b36b6f3519aa1f3fc636f609c47dddb294) [GitHub Action]. GitHub. https://github.com/actions/dependency-review-action
