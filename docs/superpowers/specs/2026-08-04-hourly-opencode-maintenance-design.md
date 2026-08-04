# Hourly OpenCode Maintenance Agent Design

## Purpose

mightyETL needs a scheduled development loop that can inspect the current repository state, remediate one bounded item, and open or update a pull request without weakening the independent review and merge gates. The scheduled agent must use OpenCode with the repository secret `NVIDIA_NIM_API_KEY`; it must not use GitHub Copilot and must not change the credentials or configuration of the existing review agent.

## Decision

Add a separate `.github/workflows/hourly-opencode-maintenance.yml` workflow. Preserve `.github/workflows/hourly-pr-disposition.yml` as the deterministic, fail-closed merge-disposition boundary and require that boundary to recognize only a non-author approval anchored to the exact current head.

The maintenance workflow will:

- run at minute 43 of every hour and on manual dispatch;
- use an immutable full-length SHA for `actions/checkout` and disable persisted checkout credentials;
- download the immutable OpenCode `v1.18.13` Linux x64 release archive and verify SHA-256 `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937` before extraction;
- avoid npm install commands, floating package tags, and mutable OpenCode action references;
- map `${{ secrets.NVIDIA_NIM_API_KEY }}` only to OpenCode's documented `NVIDIA_API_KEY` environment variable;
- select `nvidia/qwen/qwen3-coder-480b-a35b-instruct` explicitly;
- keep the workflow-level token default read-only and scope required write permissions to the sole maintenance job;
- use the repository-scoped GitHub token, without OpenCode OIDC exchange, and grant only the write permissions needed to create branches, pull requests, and issues plus read access to checks, statuses, workflow outcomes, and security findings;
- bootstrap a repository-local GitHub CLI credential helper and bot author identity because OpenCode 1.18.13 skips internal Git setup when direct `GITHUB_TOKEN` mode is selected;
- remove the local helper through an `EXIT` trap after success, failure, or process timeout;
- omit the ineffective `AGENT` environment variable because raw OpenCode 1.18.13 uses repository `default_agent` configuration or its `build` fallback;
- disable public session sharing;
- cap each run with a 45-minute `TERM` timeout, 30-second `KILL` escalation, and 50-minute workflow timeout;
- run from the protected default branch and never from pull-request code.

## Supply-chain boundary

An exact npm version is not a content identity and was reported by GitHub Advanced Security's Scorecard integration as an unpinned command dependency. The workflow therefore consumes the upstream immutable release asset directly.

The installation step:

1. downloads only over HTTPS with redirect failure handling and a TLS 1.2 minimum;
2. verifies the archive against the SHA-256 published by the upstream release process and generated Homebrew tap;
3. refuses extraction on any mismatch;
4. extracts without preserving archive ownership or permissions;
5. applies executable mode only to the expected `opencode` file;
6. verifies the binary reports exactly `1.18.13` before adding its directory to `GITHUB_PATH`.

This closes the mutable npm-command finding without adding a package-manager bootstrap, lockfile-generation network step, or floating release reference.

## Direct-token credential lifecycle

OpenCode 1.18.13 reads `USE_GITHUB_TOKEN`, uses `GITHUB_TOKEN` for GitHub API access, and then deliberately bypasses its `configureGit` function. Its scheduled-event path later invokes ordinary `git commit` and `git push` commands to publish the generated branch before creating a pull request. With `persist-credentials: false`, those commands otherwise have neither a commit author nor a credential source.

The workflow resolves this version-specific gap without weakening checkout isolation:

1. checkout still persists no credential;
2. the run step fails closed if `NVIDIA_API_KEY`, `GITHUB_TOKEN`, or `GH_TOKEN` is empty;
3. inherited GitHub credential helpers are reset for the repository-local scope;
4. `!gh auth git-credential` is installed only as a repository-local helper and reads the ephemeral token from `GH_TOKEN` when Git requests credentials;
5. local `user.name` and `user.email` identify infrastructure-created commits as `opencode-agent[bot]`;
6. an `EXIT` trap removes the local helper even when OpenCode fails, exits after `TERM`, or is forcibly ended after the grace period;
7. no encoded token, personal token, OIDC permission, model fallback, tracked credential file, or review-agent secret is introduced.

The agent process already requires the repository token for GitHub API calls, so this bootstrap does not expand token scope. It makes the granted `contents: write` capability operational while keeping credential resolution bounded to the ephemeral runner and repository-local Git configuration.

## Permission inheritance boundary

GitHub applies workflow-level permissions to every job unless a job provides its own permission map. A top-level `contents: write` grant therefore creates avoidable future-job inheritance. The workflow now sets only top-level `contents: read` and gives the sole `maintain-repository` job its explicit read/write map. This preserves current functionality while preventing a future observation or reporting job from silently inheriting write authority.

## Agent authority boundary

The prompt is part of the security boundary. The OpenCode agent may inspect, test, edit, commit, push a feature branch, update one existing pull request, or open one pull request. It must not:

- approve or merge a pull request;
- push directly to `develop` or `main`;
- bypass checks, branch protection, security gates, or independent review;
- alter review-agent workflows, review-agent secret names, `CODEOWNERS`, branch protection, or repository secrets;
- modify its own workflow or other `.github/workflows/**` files unless an open issue explicitly carries the `automation-maintenance` label;
- expose secret values, payloads, raw principals, raw idempotency keys, or internal exception details;
- create a second development pull request while another development pull request is open.

The deterministic hourly disposition workflow remains responsible for exact-head merge eligibility. Branch protection remains authoritative even if the maintenance agent proposes a change.

## Data and control flow

1. GitHub starts the scheduled workflow from `develop`.
2. Checkout reads the default-branch source with credentials persistence disabled.
3. The installer downloads the immutable OpenCode archive, verifies the pinned SHA-256, extracts it safely, and verifies the exact version.
4. The shell validates required tokens, installs the repository-local bot author and GitHub CLI credential helper, and registers helper cleanup.
5. `opencode github run` receives the bounded maintenance prompt, NVIDIA model selection, `GITHUB_TOKEN`, `GH_TOKEN`, and `NVIDIA_API_KEY` alias.
6. The agent inspects every open pull request first. If one exists, it works only on the dependency-eligible current head. If none exists, it selects one bounded buyer-visible gap, preferring the durable worker lifecycle tracked in issue #120.
7. OpenCode infrastructure commits and pushes the generated feature branch and opens or updates one pull request. It does not approve or merge.
8. The shell removes the repository-local credential helper.
9. Existing CI, security, independent review, and the deterministic disposition workflow evaluate the exact head independently.

## Failure behavior

- A missing `NVIDIA_NIM_API_KEY`, missing repository token alias, unavailable NVIDIA endpoint, release download failure, checksum mismatch, version mismatch, Git bootstrap failure, timeout, test failure, or GitHub permission denial fails the scheduled job visibly.
- The archive is never extracted after a checksum mismatch.
- No fallback provider or Copilot credential is configured.
- Concurrency is serialized with `cancel-in-progress: false`, preventing overlapping maintenance runs from racing.
- At 45 minutes, the process receives `TERM`; if it remains alive after 30 seconds, GNU `timeout` sends `KILL` so the shell can complete its credential-cleanup trap before the 50-minute workflow limit.
- The workflow does not publish a release or merge partial work.

## Verification

A repository test parses the workflow as text and fails unless it proves all of the following:

- hourly off-peak schedule, serialized concurrency, bounded graceful timeout, and forced termination;
- full-SHA checkout pinning and disabled credential persistence;
- immutable OpenCode release URL, exact SHA-256 verification, exact version verification, and no npm install command;
- exclusive use of `NVIDIA_NIM_API_KEY` through `NVIDIA_API_KEY` with the explicit NVIDIA model;
- no Copilot, Anthropic, or OpenAI credential path;
- private session setting and direct GitHub token mode;
- repository-local GitHub CLI credential helper, bot author identity, `EXIT` cleanup, no persisted encoded authorization header, and no ineffective `AGENT: build` claim;
- read-only workflow default plus job-scoped least-privilege permissions without `id-token`;
- prompt prohibitions against approval, merge, protected-branch pushes, review-agent key changes, workflow self-modification, and duplicate pull requests.

## References

Anomaly. (2026). *GitHub handler (Version 1.18.13)* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/src/cli/cmd/github.handler.ts

Anomaly. (2026). *OpenCode release v1.18.13* [Software release]. GitHub. https://github.com/anomalyco/opencode/releases/tag/v1.18.13

Anomaly. (2026). *OpenCode Homebrew formula* [Source code]. GitHub. https://github.com/anomalyco/homebrew-tap/blob/master/opencode.rb

Anomaly. (2026). *GitHub integration*. OpenCode. https://opencode.ai/docs/github/

Anomaly. (2026). *Providers*. OpenCode. https://opencode.ai/docs/providers/

Free Software Foundation. (2026). *timeout: Run a command with a time limit*. GNU Coreutils 9.11. https://www.gnu.org/software/coreutils/manual/html_node/timeout-invocation.html

GitHub, Inc. (2026). *Automatic token authentication*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/automatic-token-authentication

GitHub, Inc. (2026). *GitHub CLI manual: gh auth git-credential*. GitHub CLI Manual. https://cli.github.com/manual/gh_auth_git-credential

GitHub, Inc. (2026). *Workflow syntax for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax

GitHub, Inc. (2026). *Security hardening for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions

NVIDIA Corporation. (2026). *LLM APIs*. NVIDIA API documentation. https://docs.api.nvidia.com/nim/reference/llm-apis
