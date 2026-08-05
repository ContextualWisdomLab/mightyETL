# Hourly OpenCode maintenance

## Purpose

`.github/workflows/hourly-opencode-maintenance.yml` runs a bounded development agent at minute 43 of every hour, in UTC, and on manual dispatch. The agent uses OpenCode 1.18.13 with NVIDIA NIM to inspect the repository, repair one existing development pull request, or prepare one bounded buyer-visible improvement when no development pull request is open.

This workflow is intentionally separate from code review and merge disposition. It does not replace independent review, GitHub branch protection, required checks, GitHub Advanced Security, Dependabot, CodeRabbit, or `.github/workflows/hourly-pr-disposition.yml`.

## Required repository secret

Create or retain exactly this GitHub Actions repository secret:

```text
NVIDIA_NIM_API_KEY
```

The workflow exposes that value only to the OpenCode process as the provider variable documented by OpenCode:

```text
NVIDIA_API_KEY
```

The secret name used by the existing review agent is not changed. The scheduled workflow has no fallback credential for GitHub Copilot, Anthropic, OpenAI, a partner-only NVIDIA endpoint, or another model provider. A missing or empty `NVIDIA_NIM_API_KEY` fails the run before the agent starts.

Never place the key in repository variables, source files, workflow output, issue comments, pull-request descriptions, step summaries, command arguments, or diagnostic logs.

## Pinned execution contract

| Control | Pinned value |
| --- | --- |
| Schedule | `43 * * * *` |
| OpenCode release | immutable GitHub release `v1.18.13` |
| Linux x64 release asset | asset `501285078`, `opencode-linux-x64.tar.gz` |
| Linux x64 archive SHA-256 | `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937` |
| Expected archive shape | exactly one root regular-file entry named `opencode` |
| OpenCode provider/model | `nvidia/deepseek-ai/deepseek-v4-pro` |
| OpenCode agent | repository `default_agent`, with OpenCode 1.18.13 falling back to `build` |
| Checkout action | `actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0` |
| OpenCode process timeout | `TERM` after 45 minutes, then `KILL` after a 30-second grace period |
| GitHub job timeout | 50 minutes |
| Session sharing | disabled |
| Overlapping runs | disabled; an active run is not cancelled |

## Model selection boundary

The workflow uses `deepseek-ai/deepseek-v4-pro` because NVIDIA's current primary model catalog exposes it through a free endpoint and documents coding, agentic tool use, structured output, function calling, software-engineering use cases, and up to one million tokens of context. The previously selected Qwen3 Coder free endpoint is currently marked deprecated; relying on that endpoint would make the scheduled loop operationally brittle even when its workflow syntax and credentials were correct.

The workflow deliberately has no automatic model fallback. A second model invocation after a partially completed agent session could continue from non-deterministic workspace state, create duplicate branches, or produce conflicting pull requests. If the selected endpoint becomes unavailable, the run fails visibly. A replacement requires current NVIDIA primary-source research, a failing contract test, updated doctoring evidence, exact-head checks, and independent review.

Current selection evidence and its limits are recorded in `docs/doctoring/nvidia-opencode-model-selection-evidence.md`. The repository does not claim NVIDIA benchmark results as mightyETL performance and does not claim that raw OpenCode automatically selects a maximum-reasoning variant.

## Supply-chain installation boundary

The workflow downloads the immutable `opencode-linux-x64.tar.gz` release asset and verifies its pinned SHA-256. Before extraction it lists the archive and requires exactly one member named `opencode`. It then reads the sole member's locale-stable GNU tar verbose metadata and requires the regular-file type character `-`, rejecting hard links, symbolic links, directories, device nodes, and other special entries before the filesystem is modified.

The installer creates a fresh mode-`0700` directory, refuses overwrites, does not restore archived ownership or permissions, and verifies that the extracted object is a regular non-symbolic-link file whose reported version is exactly `1.18.13`. It does not execute an npm install command, invoke a mutable OpenCode GitHub Action tag, or use a floating `latest` reference. Checkout credentials are not persisted in the working tree.

Checksum validation, member validation, entry-type validation, and post-extraction validation are separate controls. The checksum binds the bytes to the reviewed immutable release asset. Member-name and type checks constrain extraction when a future pin is changed incorrectly or upstream packaging changes. The private empty extraction directory, overwrite refusal, and post-extraction type checks provide further containment. Detailed evidence is in `docs/doctoring/opencode-archive-extraction-evidence.md`.

OpenCode 1.18.13's raw `opencode github run` handler does not consume an `AGENT` environment variable. It omits an explicit agent from the session request, allowing repository `default_agent` configuration or the handler's `build` fallback. The workflow therefore does not set a misleading `AGENT` variable.

## Direct-token Git bootstrap

The workflow sets `USE_GITHUB_TOKEN=true` so OpenCode uses the repository-scoped `GITHUB_TOKEN` directly and does not request an OpenCode App token through OIDC. In OpenCode 1.18.13, that mode also skips OpenCode's internal Git configuration. Without an explicit bootstrap, `persist-credentials: false` would leave later infrastructure-managed commits without an author and pushes without an HTTPS credential helper.

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

There is no `id-token` permission and no permission to write Actions or security events. `contents: write` is required to prepare a feature branch; repository branch protection remains authoritative for protected branches.

## Authority boundaries

The scheduled development agent starts by inspecting every open pull request and its exact current head. It may update one dependency-eligible development pull request or, when none exists, create one feature branch and pull request for one bounded vertical slice.

The agent must not:

- approve or merge a pull request;
- push directly to `develop` or `main`;
- treat queued, pending, skipped-required, cancelled, stale-head, absent, or failed checks as passing;
- bypass independent review, branch protection, repository policy, security gates, or coverage requirements;
- change the existing review agent, its provider, workflow, credential flow, or any review-agent secret name;
- modify `.github/workflows/**` or `CODEOWNERS` unless an open issue with the `automation-maintenance` label authorizes that exact change;
- inspect or disclose secret values;
- create a second development pull request while another development pull request is open;
- publish a release unless a separate release-authorized workflow and all release acceptance gates permit it.

`.github/workflows/hourly-pr-disposition.yml` remains the deterministic exact-head merge boundary. It independently evaluates review state, unresolved threads, named checks, status contexts, labels, mergeability, and expected head SHA.

## Normal run sequence

1. GitHub starts the workflow from the default branch.
2. The workflow checks out a shallow copy with persisted credentials disabled.
3. It downloads the OpenCode 1.18.13 Linux x64 archive and verifies the pinned SHA-256.
4. It requires exactly one archive member named `opencode` and confirms through locale-stable verbose metadata that the entry is a regular file before extraction.
5. It extracts into a fresh private directory without archived ownership or permissions, refuses overwrites, and verifies a regular non-symbolic-link executable with the exact version.
6. It checks that `NVIDIA_NIM_API_KEY` was supplied through `NVIDIA_API_KEY` and that the repository token aliases are present.
7. It installs the repository-local Git author and GitHub CLI credential helper required by OpenCode's direct-token path.
8. OpenCode calls the explicit `nvidia/deepseek-ai/deepseek-v4-pro` endpoint and inspects all current pull requests before selecting work.
9. The agent runs tests first, implements one bounded change, updates authoritative documentation and `CHANGELOG.md`, and leaves one feature branch and pull request.
10. The shell `EXIT` trap removes the local Git credential helper.
11. Independent review and repository checks evaluate the exact new head.
12. The separate disposition workflow may merge only after every gate passes.

## Failure handling

| Failure | Expected result | Operator action |
| --- | --- | --- |
| Secret missing or empty | Job fails before OpenCode starts | Restore `NVIDIA_NIM_API_KEY`; never add a fallback key |
| Repository token missing | Job fails before Git bootstrap | Restore normal GitHub Actions token availability; do not add a personal token |
| GitHub CLI or Git bootstrap fails | Job fails before OpenCode starts or push fails visibly | Retain `persist-credentials: false`; verify the runner-provided `gh` executable and local helper entries |
| Release archive unavailable | Installation step fails before extraction | Verify the immutable upstream release exists; do not substitute a floating version |
| Archive checksum mismatches | Installation fails closed before extraction | Treat as a supply-chain incident; compare the immutable release record, asset metadata, and upstream publishing source before changing any pin |
| Archive has an unexpected member name or count | Installation fails closed before extraction | Treat the packaging change as a supply-chain review event; inspect the exact immutable asset before updating the member contract |
| Archive member is not a regular-file entry | Installation fails closed before extraction | Treat link, directory, or special-entry packaging as a supply-chain incident; do not relax the type gate |
| Extracted object is absent, non-regular, or a symbolic link | Installation fails before execution | Treat as a supply-chain incident; do not relax post-extraction checks |
| OpenCode version mismatches | Installation step fails | Investigate the verified archive contents; do not bypass the version assertion |
| NVIDIA endpoint is unavailable, deprecated, or rejects the model | OpenCode step fails without fallback | Confirm current NVIDIA catalog status; prepare a test-first reviewed model-selection change or disable the scheduler |
| Process exceeds 45 minutes | `timeout` sends `TERM`, escalates to `KILL` after 30 seconds if necessary, the credential-cleanup trap runs, and the step fails | Inspect incomplete branch or PR state; reduce slice size if needed |
| GitHub job exceeds 50 minutes | GitHub cancels the job | Investigate shutdown behavior and verify the ephemeral runner was destroyed |
| Tests or security checks fail | Pull request remains unmergeable | Fix the exact current head; never weaken the gate |
| Token permission denied | Operation fails visibly | Add no permission until the exact denied operation is justified and documented |
| Another hourly run starts while one is active | New run waits because concurrency is serialized | No action unless the prior run is stuck |

The workflow must not claim success for partial work. A failed run may leave a feature branch or pull request for inspection, but it cannot approve or merge it.

## Rollback and disablement

This change has no database migration and does not change runtime ETL services. To stop scheduled agent execution immediately, disable the **Hourly OpenCode maintenance** workflow in GitHub Actions. To remove it permanently, revert the workflow, its contract tests, this runbook, the design and plan documents, both doctoring evidence notes, and the corresponding `CHANGELOG.md` entries through a reviewed pull request.

Do not delete or rename `NVIDIA_NIM_API_KEY` when it is also used by other approved workflows. Disabling this workflow does not require changing the review agent or its credential scheme.

## Verification checklist

Before merging a workflow change, verify the exact current head has:

- successful Windows, Ubuntu, and macOS CI;
- successful dependency review, SBOM, Semgrep, Trivy, OSV, and Scorecard gates required by repository policy;
- no unresolved current review thread;
- independent approval anchored to the exact current head;
- the `automerge-workflow` label required by the deterministic disposition workflow;
- no new secret reference other than `NVIDIA_NIM_API_KEY`;
- a current non-deprecated NVIDIA free endpoint suited to coding and tool use;
- no automatic provider or model fallback after a partially completed session;
- a full-SHA checkout pin and checksum-pinned immutable OpenCode release asset;
- exactly one expected archive member and a pre-extraction regular-file entry-type check under `LC_ALL=C`;
- a fresh mode-`0700` extraction directory, overwrite refusal, and regular non-symbolic-link executable validation;
- no npm install command, floating package tag, or mutable OpenCode action reference;
- workflow-level read-only permission plus explicit job-scoped write permissions;
- `persist-credentials: false` plus the local GitHub CLI credential helper and `EXIT` cleanup;
- bounded `TERM` timeout with deterministic `KILL` escalation;
- no ineffective `AGENT` environment claim for raw OpenCode 1.18.13;
- no review-agent credential or workflow change.

## References

Anomaly. (2026). *GitHub handler (Version 1.18.13)* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/src/cli/cmd/github.handler.ts

Anomaly. (2026). *OpenCode release publishing script* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/script/publish.ts

Anomaly. (2026). *OpenCode release v1.18.13* [Software release]. GitHub. https://github.com/anomalyco/opencode/releases/tag/v1.18.13

Anomaly. (2026). *GitHub integration*. OpenCode. https://opencode.ai/docs/github/

Anomaly. (2026). *Providers*. OpenCode. https://opencode.ai/docs/providers/

Free Software Foundation. (2023). *GNU tar 1.35: Security*. https://www.gnu.org/software/tar/manual/html_section/Security.html

Free Software Foundation. (2026). *timeout: Run a command with a time limit*. GNU Coreutils 9.11. https://www.gnu.org/software/coreutils/manual/html_node/timeout-invocation.html

GitHub, Inc. (2026). *Automatic token authentication*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/automatic-token-authentication

GitHub, Inc. (2026). *GitHub CLI manual: gh auth git-credential*. GitHub CLI Manual. https://cli.github.com/manual/gh_auth_git-credential

GitHub, Inc. (2026). *OpenCode v1.18.13 Linux x64 release asset metadata* [JSON metadata]. GitHub REST API. https://api.github.com/repos/anomalyco/opencode/releases/assets/501285078

GitHub, Inc. (2026). *Workflow syntax for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax

GitHub, Inc. (2026). *Security hardening for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions

NVIDIA Corporation. (2026). *DeepSeek V4 Pro*. NVIDIA NIM API catalog. https://build.nvidia.com/deepseek-ai/deepseek-v4-pro

NVIDIA Corporation. (2026). *DeepSeek AI / DeepSeek V4 Pro*. NVIDIA NIM API reference. https://docs.api.nvidia.com/nim/reference/deepseek-ai-deepseek-v4-pro

NVIDIA Corporation. (2026). *Qwen3-Coder-480B-A35B-Instruct*. NVIDIA NIM API catalog. https://build.nvidia.com/qwen/qwen3-coder-480b-a35b-instruct
