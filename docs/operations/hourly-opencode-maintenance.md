# Hourly OpenCode maintenance

## Purpose

`.github/workflows/hourly-opencode-maintenance.yml` runs at minute 43 of every hour, in UTC. It uses a checksum-pinned OpenCode 1.18.13 executable and the repository's existing `NVIDIA_NIM_API_KEY` to repair one dependency-eligible development pull request or prepare one bounded buyer-visible product improvement.

The workflow is separate from independent review and deterministic merge disposition. It never approves or merges a pull request, pushes to `develop` or `main`, weakens branch protection, changes the review agent's credential path, or publishes a release.

## Required secret and model

The only model secret referenced by this workflow is:

```text
NVIDIA_NIM_API_KEY
```

The OpenCode step maps it to:

```text
NVIDIA_API_KEY
```

A step-level environment variable is visible to that step's Bash shell and every child process, including OpenCode. The workflow has no GitHub Copilot, Anthropic, OpenAI, partner-only NVIDIA, or automatic model fallback. A missing secret fails before the agent starts.

The pinned provider/model is:

```text
nvidia/deepseek-ai/deepseek-v4-pro
```

Current model-selection evidence and replacement rules are recorded in `docs/doctoring/nvidia-opencode-model-selection-evidence.md`.

## Immutable execution contract

| Control | Value |
| --- | --- |
| Schedule | `43 * * * *` |
| OpenCode release | `v1.18.13` |
| Release asset | `opencode-linux-x64.tar.gz` |
| SHA-256 | `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937` |
| Archive shape | exactly one regular-file member named `opencode` |
| Checkout | full-SHA-pinned `actions/checkout` |
| OpenCode timeout | `TERM` at 45 minutes, `KILL` after 30 seconds |
| Job timeout | 50 minutes |
| Overlap | serialized, active run not cancelled |
| Session sharing | disabled |

The installer downloads only the immutable GitHub release asset over HTTPS, verifies its checksum, requires exactly one member, requires GNU tar's regular-file type before extraction, extracts into a fresh mode-`0700` directory without restoring archive ownership or permissions, refuses overwrite, rejects symbolic links and non-regular output, and verifies the exact executable version. It does not use npm installation, a floating package version, or a mutable OpenCode action reference. Detailed evidence is in `docs/doctoring/opencode-archive-extraction-evidence.md`.

## Direct-token Git bootstrap

`USE_GITHUB_TOKEN=true` makes OpenCode use the repository-scoped token rather than an OpenCode App or OIDC token. Checkout keeps `persist-credentials: false`. Before OpenCode starts, the maintenance job:

1. requires `GITHUB_TOKEN` and `GH_TOKEN`;
2. removes inherited repository-local GitHub credential helpers;
3. installs a repository-local `!gh auth git-credential` helper;
4. sets the local author to `opencode-agent[bot]`;
5. registers an `EXIT` trap that removes the helper after success, failure, or timeout.

No plaintext or encoded token is stored in Git configuration. No personal token or additional repository secret is introduced.

## Split-job permission model

The workflow-level permission is only `contents: read`.

### `maintain-repository`

This job checks out and executes repository and agent code. It receives:

- `actions: read`
- `checks: read`
- `contents: write`
- `issues: write`
- `pull-requests: write`
- `security-events: read`
- `statuses: read`

It does **not** receive Actions write permission. Therefore OpenCode, generated code, and checked-out repository scripts cannot authorize workflow runs.

### `authorize-exact-head-checks`

This separate job receives:

- `actions: write`
- `contents: read`
- `pull-requests: read`

It never checks out or executes repository code and never receives `NVIDIA_API_KEY`. Its only write operation is the GitHub workflow-run approval endpoint for an approval-required run bound to a verified exact head. It contains no pull-request review approval or merge operation.

## Why exact-head run authorization is required

GitHub prevents most events created with `GITHUB_TOKEN` from recursively triggering workflows. Pull-request runs created after an Actions-authored open or synchronize event can remain approval-required. Without explicit authorization, an agent could push a valid repair whose exact head never receives CI, dependency review, SBOM, SAST, or security scans.

The maintenance job snapshots same-repository pull requests targeting `develop` before OpenCode starts and exports only the compact pull-request-number-to-head-SHA map. The isolated authorization job runs after the maintenance job succeeds or fails without cancellation and:

1. requires the snapshot to exist and parse as a JSON object;
2. enumerates the same pull-request set after the agent run;
3. selects only a new pull request or a head changed by that run;
4. refuses automatic authorization for any `.github/**` or `CODEOWNERS` change;
5. verifies that the current head equals the expected SHA;
6. discovers only `pull_request` workflow runs for that exact SHA;
7. fails if no run materializes;
8. verifies the head again immediately before authorization;
9. approves only runs in `action_required` or `waiting` state.

The expected SHA is passed to `jq` as data, not interpolated into jq source. Authorization only starts validation. Every check must still complete successfully, all review threads must be resolved, a non-author approval must be anchored to the same head, and branch protection and expected-head merge disposition must still permit merge.

Test-first, least-privilege, time-of-check/time-of-use, and rollback evidence is recorded in `docs/doctoring/github-token-exact-head-check-authorization-evidence.md`.

## Agent authority boundary

The agent may inspect open pull requests and their exact heads, fix one dependency-eligible development pull request, or create one bounded product pull request when no development pull request exists. It must use current authoritative standards and primary documentation, add APA 7th references where material, preserve modular MSA operation, use descriptive multiword `snake_case` database names, add beginner-readable production documentation, maintain deterministic statement and branch coverage, update `CHANGELOG.md`, and report incomplete gates truthfully.

The agent must not:

- approve or merge a pull request;
- push directly to protected branches;
- treat pending, absent, cancelled, skipped-required, stale, neutral-required, or failed checks as passing;
- bypass review, security, coverage, or branch-protection policy;
- inspect or disclose secret values;
- change the existing review agent, its provider, workflow, credential flow, or secret names;
- create a second development pull request while one is dependency-eligible;
- modify workflow policy without a specifically authorized `automation-maintenance` issue;
- publish a release without a separately authorized release workflow and complete acceptance evidence.

Even with an authorized automation issue, `.github/**` and `CODEOWNERS` changes are excluded from automatic run authorization and require human action.

## Normal sequence

1. GitHub starts the scheduled workflow from protected default-branch source.
2. OpenCode is installed from the immutable checksum-pinned archive.
3. The maintenance job snapshots current same-repository `develop` pull-request heads.
4. The NVIDIA and GitHub credential boundaries are validated and the removable Git helper is installed.
5. OpenCode reviews the current queue, executes one bounded test-first change, and leaves a feature branch or pull request.
6. The credential cleanup trap removes the local helper.
7. The isolated authorization job compares before and after heads.
8. Policy-changing pull requests are rejected from automatic run authorization.
9. Exact-head approval-required workflow runs are authorized after two SHA checks.
10. CI, security, coverage, independent review, and merge disposition operate separately.

## Failure handling

| Failure | Result | Required response |
| --- | --- | --- |
| NVIDIA secret missing | Fail before OpenCode | Restore `NVIDIA_NIM_API_KEY`; add no fallback |
| Repository token or Git helper unavailable | Fail visibly | Preserve `persist-credentials: false`; inspect runner tooling |
| Archive unavailable, checksum mismatch, unexpected member/type, or version mismatch | Fail before execution | Treat as supply-chain review; never relax the pin silently |
| Model endpoint unavailable or deprecated | Fail without fallback | Research a current NVIDIA endpoint and submit a reviewed test-first change |
| OpenCode exceeds 45 minutes | TERM then KILL; cleanup trap executes | Reduce slice size and inspect partial branch state |
| Pre-agent head output missing or invalid | Authorization job fails | Never authorize from an unknown baseline |
| Agent changes `.github/**` or `CODEOWNERS` | Automatic authorization refused | Require explicit human workflow-run authorization |
| Head moves during discovery or authorization | Authorization refused | Re-evaluate the new exact head |
| No exact-head run materializes | Workflow fails | Diagnose event and Actions policy; do not merge the head |
| Workflow-run approval rejected | Workflow fails | Verify repository policy and token permissions; add no personal-token workaround |
| Checks or review fail | Pull request remains blocked | Fix the exact head without weakening the gate |
| A prior hourly run remains active | New run waits | Investigate only if the prior run is stuck |

A failed run can leave a reviewable feature branch or pull request, but it cannot claim successful validation, approval, merge, or release.

## Rollback

Disable **Hourly OpenCode maintenance** to stop execution immediately. Permanent rollback must revert the workflow, contract tests, operations document, doctoring evidence, plan/design documents, and `CHANGELOG.md` through a reviewed pull request.

Do not remove exact-head authorization while retaining agent writes through `GITHUB_TOKEN`, and do not move `actions: write` into the OpenCode job. A GitHub App replacement requires separately reviewed evidence for installation permissions, recursive triggers, actor identity, secret lifecycle, exact-head validation, and independent review.

## Verification checklist

Before merge, verify on the exact current head:

- Ubuntu, macOS, and Windows CI succeeded;
- dependency review, SBOM, Semgrep, Trivy, OSV, Scorecard, and required security gates succeeded;
- no current unresolved review thread or requested change remains;
- a non-author approval is anchored to the exact head;
- required workflow-change labels are present;
- only `NVIDIA_NIM_API_KEY` is referenced as a model secret;
- the current NVIDIA endpoint and immutable OpenCode pin remain valid;
- archive member, type, private extraction, overwrite, output-type, and version checks remain intact;
- OpenCode retains no Actions write authority;
- the isolated authorization job is the only holder of `actions: write` and performs no checkout;
- the before/after head output, `.github/**` and `CODEOWNERS` exclusion, exact-head run filter, double head check, and visible absent-run failure remain intact;
- the development workflow contains no review approval, merge, protected-branch push, fallback credential, or review-agent modification.

## References — APA 7th

Anomaly. (2026). *GitHub handler (Version 1.18.13)* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/src/cli/cmd/github.handler.ts

Anomaly. (2026). *OpenCode release v1.18.13* [Software release]. GitHub. https://github.com/anomalyco/opencode/releases/tag/v1.18.13

Anomaly. (2026). *GitHub integration*. OpenCode. https://opencode.ai/docs/github/

Anomaly. (2026). *Providers*. OpenCode. https://opencode.ai/docs/providers/

Free Software Foundation. (2023). *GNU tar 1.35: Security*. https://www.gnu.org/software/tar/manual/html_section/Security.html

Free Software Foundation. (2026). *timeout: Run a command with a time limit*. GNU Coreutils 9.11. https://www.gnu.org/software/coreutils/manual/html_node/timeout-invocation.html

GitHub, Inc. (2026). *GITHUB_TOKEN*. GitHub Docs. https://docs.github.com/en/actions/concepts/security/github_token

GitHub, Inc. (2026). *GitHub CLI manual: gh auth git-credential*. GitHub CLI Manual. https://cli.github.com/manual/gh_auth_git-credential

GitHub, Inc. (2026). *REST API endpoints for workflow runs*. GitHub Docs. https://docs.github.com/en/rest/actions/workflow-runs

GitHub, Inc. (2026). *Security hardening for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions

GitHub, Inc. (2026). *Triggering a workflow*. GitHub Docs. https://docs.github.com/en/actions/using-workflows/triggering-a-workflow

NVIDIA Corporation. (2026). *DeepSeek V4 Pro*. NVIDIA NIM API catalog. https://build.nvidia.com/deepseek-ai/deepseek-v4-pro

NVIDIA Corporation. (2026). *DeepSeek AI / DeepSeek V4 Pro*. NVIDIA NIM API reference. https://docs.api.nvidia.com/nim/reference/deepseek-ai-deepseek-v4-pro
