# Hourly OpenCode maintenance

## Purpose

`.github/workflows/hourly-opencode-maintenance.yml` runs a bounded development agent at minute 43 of every hour, in UTC, and on manual dispatch. The agent uses OpenCode 1.18.13 with NVIDIA NIM to inspect the repository, repair one existing development pull request, or prepare one bounded buyer-visible improvement when no development pull request is open.

This workflow is intentionally separate from both code review and merge disposition. It does not replace independent review, GitHub branch protection, required checks, GitHub Advanced Security, Dependabot, CodeRabbit, or `.github/workflows/hourly-pr-disposition.yml`.

## Required repository secret

Create or retain exactly this GitHub Actions repository secret:

```text
NVIDIA_NIM_API_KEY
```

The workflow exposes that value only to the OpenCode process as the provider variable documented by OpenCode:

```text
NVIDIA_API_KEY
```

The secret name used by the existing review agent is not changed. The scheduled workflow has no fallback credential for GitHub Copilot, Anthropic, OpenAI, or another model provider. A missing or empty `NVIDIA_NIM_API_KEY` fails the run before the agent starts.

Never place the key in repository variables, source files, workflow output, issue comments, pull-request descriptions, step summaries, command arguments, or diagnostic logs.

## Pinned execution contract

| Control | Pinned value |
| --- | --- |
| Schedule | `43 * * * *` |
| OpenCode package | `opencode-ai@1.18.13` |
| OpenCode provider/model | `nvidia/qwen/qwen3-coder-480b-a35b-instruct` |
| OpenCode agent | Repository `default_agent`, with OpenCode 1.18.13 falling back to `build` |
| Checkout action | `actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0` |
| OpenCode process timeout | 45 minutes, terminated with `TERM` |
| GitHub job timeout | 50 minutes |
| Session sharing | disabled |
| Overlapping runs | disabled; an active run is not cancelled |

The workflow installs the exact npm package version and verifies `opencode --version` before use. It does not invoke the mutable OpenCode GitHub Action tag or a floating `latest` package version. Checkout credentials are not persisted in the working tree.

OpenCode 1.18.13's raw `opencode github run` handler does not consume an `AGENT` environment variable. It deliberately omits an explicit agent from the session request, allowing repository `default_agent` configuration or the handler's `build` fallback. The workflow therefore does not set a misleading `AGENT` variable.

## Direct-token Git bootstrap

The workflow sets `USE_GITHUB_TOKEN=true` so OpenCode uses the repository-scoped `GITHUB_TOKEN` directly and does not request an OpenCode App token through OIDC. In OpenCode 1.18.13, that mode also skips OpenCode's internal `configureGit` function. Without an explicit bootstrap, `persist-credentials: false` would leave later infrastructure-managed commits without an author and pushes without an HTTPS authorization header.

Before starting OpenCode, the workflow therefore:

1. fails closed when `GITHUB_TOKEN` is absent;
2. removes any pre-existing repository-local GitHub authorization header;
3. derives a Basic authorization header in memory from `x-access-token:${GITHUB_TOKEN}` without printing it;
4. stores that header only in the checked-out repository's local Git configuration;
5. sets the local commit author to `opencode-agent[bot]`;
6. installs an `EXIT` trap that removes the authorization header after success, failure, or timeout.

The local author identity contains no credential. The authorization header is short-lived on the ephemeral runner and is not committed. The agent still receives `GITHUB_TOKEN` because OpenCode uses it for GitHub API operations such as pull-request creation. No OIDC or fallback model credential is introduced.

## Repository permissions

The job receives only these explicit `GITHUB_TOKEN` permissions:

- `actions: read`
- `checks: read`
- `contents: write`
- `issues: write`
- `pull-requests: write`
- `security-events: read`
- `statuses: read`

There is no `id-token` permission and no permission to write Actions or security events. `contents: write` is needed to prepare a feature branch; repository branch protection remains authoritative for protected branches.

## Authority boundaries

The scheduled development agent starts by inspecting every open pull request and its exact current head. It may update one dependency-eligible development pull request or, when none exists, create one feature branch and pull request for one bounded vertical slice.

The agent must not:

- approve or merge a pull request;
- push directly to `develop` or `main`;
- treat queued, pending, skipped-required, cancelled, stale-head, absent, or failed checks as passing;
- bypass independent review, branch protection, repository policy, security gates, or coverage requirements;
- change the existing review agent, its provider, its workflow, its credential flow, or any review-agent secret name;
- modify `.github/workflows/**` or `CODEOWNERS` unless an open issue with the `automation-maintenance` label authorizes that exact change;
- inspect or disclose secret values;
- create a second development pull request while another development pull request is open;
- publish a release unless a separate release-authorized workflow and all release acceptance gates permit it.

`.github/workflows/hourly-pr-disposition.yml` remains the deterministic exact-head merge boundary. It evaluates review state, unresolved threads, named checks, status contexts, labels, mergeability, and expected head SHA independently from the development agent.

## Normal run sequence

1. GitHub starts the workflow from the default branch.
2. The workflow checks out a shallow copy with persisted credentials disabled.
3. It installs and verifies OpenCode 1.18.13.
4. It checks that `NVIDIA_NIM_API_KEY` was supplied through `NVIDIA_API_KEY` and that `GITHUB_TOKEN` is present.
5. It installs the repository-local Git author and short-lived authorization header required by OpenCode's direct-token path.
6. OpenCode inspects all current pull requests before selecting any work.
7. The agent runs tests first, implements one bounded change, updates authoritative documentation and `CHANGELOG.md`, and leaves a feature branch and pull request.
8. The shell `EXIT` trap removes the local Git authorization header.
9. Independent review and repository checks evaluate the exact new head.
10. The separate disposition workflow may merge only after every gate passes.

## Failure handling

| Failure | Expected result | Operator action |
| --- | --- | --- |
| Secret missing or empty | Job fails before OpenCode starts | Restore `NVIDIA_NIM_API_KEY`; never add a fallback key |
| Repository token missing | Job fails before Git bootstrap | Restore normal GitHub Actions token availability; do not add a personal token |
| Git bootstrap fails | Job fails before OpenCode starts | Inspect local Git configuration commands and retain `persist-credentials: false` |
| Exact OpenCode version unavailable or mismatched | Installation step fails | Investigate npm availability and supply-chain status before changing the pin |
| NVIDIA API unavailable or model rejected | OpenCode step fails | Check NVIDIA service health and model availability; retain the current PR state |
| Process exceeds 45 minutes | `timeout` sends `TERM`, the credential-cleanup trap runs, and the step fails | Inspect the incomplete feature branch or PR; reduce slice size if needed |
| GitHub job exceeds 50 minutes | GitHub cancels the job | Investigate runner or process shutdown behavior and verify the ephemeral runner was destroyed |
| Tests or security checks fail | Pull request remains unmergeable | Fix the current exact head; never weaken the gate |
| Token permission denied | Operation fails visibly | Add no permission until the exact denied operation is justified and documented |
| Another hourly run starts while one is active | New run waits because concurrency is serialized | No action unless the prior run is stuck |

The workflow must not claim success for partial work. A failed run may leave a feature branch or pull request for inspection, but it cannot approve or merge it.

## Rollback and disablement

This change has no database migration and does not change runtime ETL services. To stop scheduled agent execution immediately, disable the **Hourly OpenCode maintenance** workflow in GitHub Actions. To remove it permanently, revert the workflow, its contract test, this runbook, the design and plan documents, and the corresponding `CHANGELOG.md` entry through a reviewed pull request.

Do not delete or rename `NVIDIA_NIM_API_KEY` when it is also used by other approved workflows. Disabling this workflow does not require changing the review agent or its credential scheme.

## Verification checklist

Before merging a workflow change, verify the exact current head has:

- successful Windows, Ubuntu, and macOS CI;
- successful dependency review, SBOM, Semgrep, Trivy, OSV, and Scorecard gates required by repository policy;
- no unresolved current review thread;
- independent approval;
- the `automerge-workflow` label required by the deterministic disposition workflow;
- no new secret reference other than `NVIDIA_NIM_API_KEY`;
- no mutable OpenCode package or action reference;
- `persist-credentials: false` plus explicit local direct-token Git bootstrap and `EXIT` cleanup;
- no ineffective `AGENT` environment claim for raw OpenCode 1.18.13;
- no review-agent credential or workflow change.

## References

Anomaly. (2026). *GitHub handler (Version 1.18.13)* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/src/cli/cmd/github.handler.ts

Anomaly. (2026). *GitHub integration*. OpenCode. https://opencode.ai/docs/github/

Anomaly. (2026). *Providers*. OpenCode. https://opencode.ai/docs/providers/

GitHub, Inc. (2026). *Automatic token authentication*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/automatic-token-authentication

GitHub, Inc. (2026). *Events that trigger workflows*. GitHub Docs. https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows

GitHub, Inc. (2026). *Security hardening for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions

NVIDIA Corporation. (2026). *LLM APIs*. NVIDIA API documentation. https://docs.api.nvidia.com/nim/reference/llm-apis
