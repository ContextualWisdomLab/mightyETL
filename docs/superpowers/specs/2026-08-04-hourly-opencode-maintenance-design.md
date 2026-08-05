# Hourly OpenCode Maintenance Agent Design

## Purpose

mightyETL needs a scheduled development loop that can inspect current repository state, remediate one bounded item, and open or update a pull request without weakening independent review and merge gates. The scheduled agent must use OpenCode with `NVIDIA_NIM_API_KEY`; it must not use GitHub Copilot or change the existing review agent's credentials, provider, workflow, or authority.

## Decision

Add a separate `.github/workflows/hourly-opencode-maintenance.yml` workflow. Preserve `.github/workflows/hourly-pr-disposition.yml` as the deterministic, fail-closed merge boundary and require a non-author approval anchored to the exact current head.

The maintenance workflow will:

- run at minute 43 of every hour and on manual dispatch;
- pin `actions/checkout` by full SHA with persisted credentials disabled;
- install OpenCode 1.18.13 from an immutable release asset verified by SHA-256;
- accept exactly one regular archive member named `opencode` before extraction;
- extract into a fresh mode-`0700` directory with ownership, archived permissions, and overwrites disabled;
- reject non-regular or symbolic-link output and verify the exact executable version;
- map only `${{ secrets.NVIDIA_NIM_API_KEY }}` to `NVIDIA_API_KEY`;
- select `nvidia/deepseek-ai/deepseek-v4-pro`, a current NVIDIA free endpoint documented for coding, agentic tool use, function calling, and long-context software-engineering work;
- configure no automatic model or provider fallback;
- keep workflow-level permissions read-only and scope required write permissions to the sole maintenance job;
- use the repository-scoped GitHub token without OpenCode OIDC exchange;
- bootstrap a repository-local GitHub CLI credential helper and bot author because OpenCode 1.18.13 skips internal Git setup in direct-token mode;
- remove the helper through an `EXIT` trap;
- omit the ineffective `AGENT` environment variable, allowing repository `default_agent` or OpenCode's `build` fallback;
- disable public session sharing;
- cap each run with a 45-minute `TERM` timeout, 30-second `KILL` escalation, and 50-minute job timeout;
- run from protected default-branch source, never pull-request code.

## Model selection boundary

The previous Qwen3 Coder free endpoint is marked deprecated in NVIDIA's current catalog. A deprecated hosted endpoint makes the scheduler unreliable even if its workflow, credentials, and Git behavior are correct. DeepSeek V4 Pro is selected because NVIDIA currently exposes it through a free endpoint and documents coding, agentic AI, tool use, structured output, function calling, software-engineering use cases, and up to one million tokens of context.

The model identifier is explicit and test-guarded. The workflow has no automatic fallback because invoking a second model after a partially completed session could operate on non-deterministic workspace state, create duplicate branches, or generate conflicting pull requests. Endpoint rejection fails visibly and requires a separate test-first model-selection change. The repository does not claim NVIDIA benchmarks as mightyETL performance and does not claim that raw OpenCode selects a maximum-reasoning variant.

## Supply-chain boundary

An exact npm version is not a content identity. The workflow therefore consumes the immutable upstream release asset directly.

The installation step:

1. downloads only over HTTPS with failure handling and TLS 1.2 minimum;
2. verifies SHA-256 `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937`;
3. requires exactly one archive member named `opencode`;
4. under `LC_ALL=C`, requires GNU tar's regular-file type character `-` before extraction;
5. refuses extraction on checksum, member-shape, or entry-type mismatch;
6. recreates a private mode-`0700` directory and refuses overwrites;
7. extracts without restoring archive ownership or permissions;
8. requires a regular non-symbolic-link output file;
9. applies executable mode only to that file;
10. verifies version `1.18.13` before adding it to `GITHUB_PATH`.

Checksum binding, pre-extraction name and type checks, private extraction, overwrite refusal, and post-extraction file checks are cumulative controls. They close the mutable npm-command finding without a package-manager bootstrap, floating release, mutable action reference, or unconstrained extraction.

## Direct-token credential lifecycle

OpenCode 1.18.13 uses `GITHUB_TOKEN` for GitHub API access when `USE_GITHUB_TOKEN=true` and skips its internal `configureGit` function. Its scheduled-event path later uses ordinary `git commit` and `git push`. With `persist-credentials: false`, those commands otherwise lack both author identity and HTTPS credentials.

The workflow therefore:

1. fails closed if `NVIDIA_API_KEY`, `GITHUB_TOKEN`, or `GH_TOKEN` is empty;
2. resets inherited GitHub credential helpers in repository-local configuration;
3. installs `!gh auth git-credential`, which reads the ephemeral token from `GH_TOKEN` only when Git requests credentials;
4. sets local `user.name` and `user.email` to `opencode-agent[bot]`;
5. removes the helper through an `EXIT` trap after success, failure, timeout, or forced termination.

No encoded token, personal token, OIDC permission, tracked credential file, alternate model credential, or review-agent secret is introduced.

## Permission inheritance boundary

GitHub applies workflow-level permissions to jobs unless a job provides its own map. The workflow sets only top-level `contents: read` and gives the sole `maintain-repository` job the minimum explicit map needed to inspect checks and security state, create a feature branch, update issues, and create or update a pull request. `contents: write` is necessary for the bounded branch operation; branch protection remains authoritative for `develop` and `main`.

## Agent authority boundary

The prompt is part of the security boundary. The agent may inspect, test, edit, commit, push one feature branch, update one development pull request, or open one pull request. It must not:

- approve or merge a pull request;
- push directly to `develop` or `main`;
- bypass checks, branch protection, security gates, coverage, or independent review;
- alter review-agent workflows, providers, credentials, secret names, `CODEOWNERS`, branch protection, or repository secrets;
- modify `.github/workflows/**` unless an issue explicitly labeled `automation-maintenance` authorizes that exact bounded change;
- expose secret values, request payloads, raw principals, raw idempotency keys, or internal exception details;
- create a second development pull request while another one is open;
- publish a release without a separate release-authorized workflow and every acceptance gate.

The deterministic disposition workflow independently evaluates reviews, current threads, named checks, status contexts, labels, mergeability, and expected head SHA.

## Data flow

1. GitHub starts the workflow from `develop`.
2. Checkout reads protected default-branch source without persisting credentials.
3. The installer verifies the immutable OpenCode archive, member name, regular entry type, extraction boundary, output type, and executable version.
4. The shell validates required secrets and token aliases, installs local Git identity and helper, and registers cleanup.
5. OpenCode calls `nvidia/deepseek-ai/deepseek-v4-pro` with the bounded prompt.
6. The agent inspects all open pull requests first. It repairs one dependency-eligible exact head or, when none exists, selects one bounded buyer-visible gap, preferring issue #120 while open and ready.
7. OpenCode prepares one branch and pull request but does not approve or merge.
8. The shell removes its credential helper.
9. CI, security, independent review, branch protection, and deterministic disposition evaluate the exact head independently.

## Failure behavior

Missing credentials, deprecated or rejected model, NVIDIA outage, download failure, checksum mismatch, archive mismatch, non-regular entry, extracted-file mismatch, version mismatch, Git bootstrap failure, timeout, test failure, or permission denial fails visibly. No provider fallback or partial-success claim is allowed. Concurrency is serialized. At 45 minutes, `TERM` is followed by `KILL` after 30 seconds if necessary; the job-level timeout remains 50 minutes.

## Verification

Repository tests fail unless they prove:

- hourly serialized scheduling and bounded forced termination;
- immutable checkout and OpenCode content pins;
- exact pre-extraction member name and regular-file type;
- private extraction, overwrite refusal, and regular non-symbolic-link output;
- exclusive use of `NVIDIA_NIM_API_KEY` and current `nvidia/deepseek-ai/deepseek-v4-pro` selection;
- rejection of the deprecated Qwen3 Coder identifier and non-NVIDIA credential paths;
- direct-token Git bootstrap and cleanup without stored encoded authorization;
- workflow-level read-only and job-scoped least privilege without OIDC;
- prompt prohibitions against approval, merge, protected-branch writes, review-agent changes, self-modification, and duplicate pull requests.

## References

Anomaly. (2026). *GitHub handler (Version 1.18.13)* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/src/cli/cmd/github.handler.ts

Anomaly. (2026). *OpenCode release publishing script* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/script/publish.ts

Anomaly. (2026). *OpenCode release v1.18.13* [Software release]. GitHub. https://github.com/anomalyco/opencode/releases/tag/v1.18.13

Free Software Foundation. (2023). *GNU tar 1.35: Security*. https://www.gnu.org/software/tar/manual/html_section/Security.html

GitHub, Inc. (2026). *Automatic token authentication*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/automatic-token-authentication

GitHub, Inc. (2026). *GitHub CLI manual: gh auth git-credential*. GitHub CLI Manual. https://cli.github.com/manual/gh_auth_git-credential

GitHub, Inc. (2026). *Workflow syntax for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax

NVIDIA Corporation. (2026). *DeepSeek V4 Pro*. NVIDIA NIM API catalog. https://build.nvidia.com/deepseek-ai/deepseek-v4-pro

NVIDIA Corporation. (2026). *DeepSeek AI / DeepSeek V4 Pro*. NVIDIA NIM API reference. https://docs.api.nvidia.com/nim/reference/deepseek-ai-deepseek-v4-pro

NVIDIA Corporation. (2026). *Qwen3-Coder-480B-A35B-Instruct*. NVIDIA NIM API catalog. https://build.nvidia.com/qwen/qwen3-coder-480b-a35b-instruct
