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
| OpenCode release | `v1.18.13` immutable GitHub release |
| Linux x64 archive SHA-256 | `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937` |
| Expected archive shape | exactly one root member named `opencode` |
| OpenCode provider/model | `nvidia/qwen/qwen3-coder-480b-a35b-instruct` |
| OpenCode agent | Repository `default_agent`, with OpenCode 1.18.13 falling back to `build` |
| Checkout action | `actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0` |
| OpenCode process timeout | `TERM` after 45 minutes, then `KILL` after a 30-second grace period |
| GitHub job timeout | 50 minutes |
| Session sharing | disabled |
| Overlapping runs | disabled; an active run is not cancelled |

The workflow downloads the immutable `opencode-linux-x64.tar.gz` release asset and verifies the authoritative SHA-256 published by the upstream release process. Before extraction it lists the archive and requires exactly one member named `opencode`. It then creates a fresh mode-`0700` installation directory, refuses overwrites, does not restore archived ownership or permissions, and verifies that the extracted object is a regular non-symbolic-link file whose reported version is exactly `1.18.13`. It does not execute an npm install command, invoke a mutable OpenCode GitHub Action tag, or use a floating `latest` reference. Checkout credentials are not persisted in the working tree.

Checksum validation and member validation are separate controls. The checksum binds the bytes to the reviewed release asset; the member check constrains the extraction shape if a future pin is changed incorrectly or upstream packaging changes. The private empty extraction directory and post-extraction type checks provide additional containment.

OpenCode 1.18.13's raw `opencode github run` handler does not consume an `AGENT` environment variable. It deliberately omits an explicit agent from the session request, allowing repository `default_agent` configuration or the handler's `build` fallback. The workflow therefore does not set a misleading `AGENT` variable.

## Direct-token Git bootstrap

The workflow sets `USE_GITHUB_TOKEN=true` so OpenCode uses the repository-scoped `GITHUB_TOKEN` directly and does not request an OpenCode App token through OIDC. In OpenCode 1.18.13, that mode also skips OpenCode's internal `configureGit` function. Without an explicit bootstrap, `persist-credentials: false` would leave later infrastructure-managed commits without an author and pushes without an HTTPS credential helper.

Before starting OpenCode, the workflow therefore:

1. fails closed when either `GITHUB_TOKEN` or its GitHub CLI alias `GH_TOKEN` is absent;
2. removes any pre-existing repository-local GitHub credential-helper entry;
3. resets inherited helpers for `https://github.com` with an empty local helper entry;
4. adds the repository-local `!gh auth git-credential` helper, which reads the short-lived token from `GH_TOKEN` at credential-request time;
5. sets the local commit author to `opencode-agent[bot]`;
6. installs an `EXIT` trap that removes the local helper after success, failure, graceful timeout, or forced process termination.

No encoded or plaintext token is written to Git configuration. The local author identity contains no credential. The agent still receives `GITHUB_TOKEN` because OpenCode uses it for GitHub API operations such as pull-request creation. No personal token, OIDC path, fallback model credential, or tracked credential file is introduced.

## Repository permissions

The workflow-level default is only `contents: read`. Write authority is scoped to the sole `maintain-repository` job so a future job cannot inherit repository write access accidentally.

That job receives only these explicit `GITHUB_TOKEN` permissions:

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
3. It downloads the OpenCode 1.18.13 Linux archive and verifies the pinned SHA-256.
4. It requires exactly one archive member named `opencode`, extracts into a fresh private directory without archived ownership or permissions, and verifies a regular non-symbolic-link executable with the exact version.
5. It checks that `NVIDIA_NIM_API_KEY` was supplied through `NVIDIA_API_KEY` and that the repository token aliases are present.
6. It installs the repository-local Git author and GitHub CLI credential helper required by OpenCode's direct-token path.
7. OpenCode inspects all current pull requests before selecting any work.
8. The agent runs tests first, implements one bounded change, updates authoritative documentation and `CHANGELOG.md`, and leaves a feature branch and pull request.
9. The shell `EXIT` trap removes the local Git credential helper.
10. Independent review and repository checks evaluate the exact new head.
11. The separate disposition workflow may merge only after every gate passes.

## Failure handling

| Failure | Expected result | Operator action |
| --- | --- | --- |
| Secret missing or empty | Job fails before OpenCode starts | Restore `NVIDIA_NIM_API_KEY`; never add a fallback key |
| Repository token missing | Job fails before Git bootstrap | Restore normal GitHub Actions token availability; do not add a personal token |
| GitHub CLI or Git bootstrap fails | Job fails before OpenCode starts or push fails visibly | Retain `persist-credentials: false`; verify the runner-provided `gh` executable and local helper entries |
| Release archive unavailable | Installation step fails before extraction | Verify the immutable upstream release exists; do not substitute a floating version |
| Archive checksum mismatches | Installation fails closed before extraction | Treat as a supply-chain incident; compare the upstream immutable release and generated tap checksum before changing any pin |
| Archive has an unexpected member set | Installation fails closed before extraction | Treat the packaging change as a supply-chain review event; inspect the exact immutable asset before updating the member contract |
| Extracted object is absent, non-regular, or a symbolic link | Installation fails before execution | Treat as a supply-chain incident; do not relax the file-type checks |
| OpenCode version mismatches | Installation step fails | Investigate the verified archive contents; do not bypass the version assertion |
| NVIDIA API unavailable or model rejected | OpenCode step fails | Check NVIDIA service health and model availability; retain the current PR state |
| Process exceeds 45 minutes | `timeout` sends `TERM`, escalates to `KILL` after 30 seconds if necessary, the credential-cleanup trap runs, and the step fails | Inspect the incomplete feature branch or PR; reduce slice size if needed |
| GitHub job exceeds 50 minutes | GitHub cancels the job | Investigate runner or process shutdown behavior and verify the ephemeral runner was destroyed |
| Tests or security checks fail | Pull request remains unmergeable | Fix the current exact head; never weaken the gate |
| Token permission denied | Operation fails visibly | Add no permission until the exact denied operation is justified and documented |
| Another hourly run starts while one is active | New run waits because concurrency is serialized | No action unless the prior run is stuck |

The workflow must not claim success for partial work. A failed run may leave a feature branch or pull request for inspection, but it cannot approve or merge it.

## Rollback and disablement

This change has no database migration and does not change runtime ETL services. To stop scheduled agent execution immediately, disable the **Hourly OpenCode maintenance** workflow in GitHub Actions. To remove it permanently, revert the workflow, its contract tests, this runbook, the design and plan documents, the doctoring evidence, and the corresponding `CHANGELOG.md` entry through a reviewed pull request.

Do not delete or rename `NVIDIA_NIM_API_KEY` when it is also used by other approved workflows. Disabling this workflow does not require changing the review agent or its credential scheme.

## Verification checklist

Before merging a workflow change, verify the exact current head has:

- successful Windows, Ubuntu, and macOS CI;
- successful dependency review, SBOM, Semgrep, Trivy, OSV, and Scorecard gates required by repository policy;
- no unresolved current review thread;
- independent approval;
- the `automerge-workflow` label required by the deterministic disposition workflow;
- no new secret reference other than `NVIDIA_NIM_API_KEY`;
- a full-SHA checkout pin and checksum-pinned immutable OpenCode release asset;
- exactly one expected archive member validated before extraction;
- a fresh mode-`0700` extraction directory, overwrite refusal, and regular non-symbolic-link executable validation;
- no npm install command, floating package tag, or mutable OpenCode action reference;
- workflow-level read-only permission plus explicit job-scoped write permissions;
- `persist-credentials: false` plus the local GitHub CLI credential helper and `EXIT` cleanup;
- bounded `TERM` timeout with deterministic `KILL` escalation;
- no ineffective `AGENT` environment claim for raw OpenCode 1.18.13;
- no review-agent credential or workflow change.

## References

Anomaly. (2026). *GitHub handler (Version 1.18.13)* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/src/cli/cmd/github.handler.ts

Anomaly. (2026). *OpenCode release v1.18.13* [Software release]. GitHub. https://github.com/anomalyco/opencode/releases/tag/v1.18.13

Anomaly. (2026). *OpenCode Homebrew formula* [Source code]. GitHub. https://github.com/anomalyco/homebrew-tap/blob/master/opencode.rb

Anomaly. (2026). *GitHub integration*. OpenCode. https://opencode.ai/docs/github/

Anomaly. (2026). *Providers*. OpenCode. https://opencode.ai/docs/providers/

Free Software Foundation. (2023). *GNU tar 1.35: Security*. https://www.gnu.org/software/tar/manual/html_section/Security.html

Free Software Foundation. (2026). *timeout: Run a command with a time limit*. GNU Coreutils 9.11. https://www.gnu.org/software/coreutils/manual/html_node/timeout-invocation.html

GitHub, Inc. (2026). *Automatic token authentication*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/automatic-token-authentication

GitHub, Inc. (2026). *GitHub CLI manual: gh auth git-credential*. GitHub CLI Manual. https://cli.github.com/manual/gh_auth_git-credential

GitHub, Inc. (2026). *Workflow syntax for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax

GitHub, Inc. (2026). *Security hardening for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions

NVIDIA Corporation. (2026). *LLM APIs*. NVIDIA API documentation. https://docs.api.nvidia.com/nim/reference/llm-apis
