# Hourly OpenCode Maintenance Agent Design

## Purpose

mightyETL needs a scheduled development loop that can inspect the current repository state, remediate one bounded item, and open or update a pull request without weakening the independent review and merge gates. The scheduled agent must use OpenCode with the repository secret `NVIDIA_NIM_API_KEY`; it must not use GitHub Copilot and must not change the credentials or configuration of the existing review agent.

## Decision

Add a separate `.github/workflows/hourly-opencode-maintenance.yml` workflow. Keep `.github/workflows/hourly-pr-disposition.yml` unchanged as the deterministic, fail-closed merge-disposition boundary.

The maintenance workflow will:

- run at minute 43 of every hour and on manual dispatch;
- use an immutable full-length SHA for `actions/checkout` and disable persisted checkout credentials;
- install the exact OpenCode package version `opencode-ai@1.18.13` rather than invoking a mutable `@latest` action;
- map `${{ secrets.NVIDIA_NIM_API_KEY }}` only to OpenCode's documented `NVIDIA_API_KEY` environment variable;
- select `nvidia/qwen/qwen3-coder-480b-a35b-instruct` explicitly;
- use the repository-scoped `GITHUB_TOKEN`, without OpenCode OIDC exchange, and grant only the write permissions needed to create branches, pull requests, and issues plus read access to checks and statuses;
- bootstrap a short-lived repository-local Git authorization header and bot author identity because OpenCode 1.18.13 skips internal Git setup when direct `GITHUB_TOKEN` mode is selected;
- remove the local authorization header through an `EXIT` trap after success, failure, or process timeout;
- omit the ineffective `AGENT` environment variable because raw OpenCode 1.18.13 uses repository `default_agent` configuration or its `build` fallback;
- disable public session sharing;
- cap each run with a 45-minute `TERM` timeout, 30-second `KILL` escalation, and 50-minute workflow timeout;
- run from the protected default branch and never from pull-request code.

## Direct-token credential lifecycle

OpenCode 1.18.13 reads `USE_GITHUB_TOKEN`, uses `GITHUB_TOKEN` for GitHub API access, and then deliberately bypasses its `configureGit` function. Its scheduled-event path later invokes ordinary `git commit` and `git push` commands to publish the generated branch before creating a pull request. With `persist-credentials: false`, those commands otherwise have neither a commit author nor HTTPS push authorization.

The workflow resolves this version-specific gap without weakening checkout isolation:

1. checkout still persists no credential;
2. the run step fails closed if either `NVIDIA_API_KEY` or `GITHUB_TOKEN` is empty;
3. a Basic GitHub authorization header is derived in memory from the repository token and stored only in the checkout's local Git configuration;
4. local `user.name` and `user.email` identify infrastructure-created commits as `opencode-agent[bot]`;
5. an `EXIT` trap removes the authorization header even when OpenCode fails, exits after `TERM`, or is forcibly ended after the grace period;
6. no personal token, OIDC permission, model fallback, tracked credential file, or review-agent secret is introduced.

The agent process already requires `GITHUB_TOKEN` for GitHub API calls, so this bootstrap does not expand token scope. It makes the granted `contents: write` capability operational while keeping the credential bounded to the ephemeral runner and repository-local Git configuration.

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
3. npm installs the pinned OpenCode CLI and verifies its exact version.
4. The shell validates both required tokens, installs the repository-local Git author and short-lived authorization header, and registers credential cleanup.
5. `opencode github run` receives the bounded maintenance prompt, NVIDIA model selection, `GITHUB_TOKEN`, and `NVIDIA_API_KEY` alias.
6. The agent inspects every open pull request first. If one exists, it works only on the dependency-eligible current head. If none exists, it selects one bounded buyer-visible gap, preferring the durable worker lifecycle tracked in issue #120.
7. OpenCode infrastructure commits and pushes the generated feature branch and opens or updates one pull request. It does not approve or merge.
8. The shell removes the repository-local authorization header.
9. Existing CI, security, review, and the deterministic disposition workflow evaluate the exact head independently.

## Failure behavior

- A missing `NVIDIA_NIM_API_KEY`, missing `GITHUB_TOKEN`, unavailable NVIDIA endpoint, OpenCode installation mismatch, Git bootstrap failure, timeout, test failure, or GitHub permission denial fails the scheduled job visibly.
- No fallback provider or Copilot credential is configured.
- Concurrency is serialized with `cancel-in-progress: false`, preventing overlapping maintenance runs from racing.
- At 45 minutes, the process receives `TERM`; if it remains alive after 30 seconds, `timeout` sends `KILL` so the shell can complete its credential-cleanup trap before the 50-minute workflow limit.
- The workflow does not publish a release or merge partial work.

## Verification

A repository test will parse the workflow as text and fail unless it proves all of the following:

- hourly off-peak schedule, serialized concurrency, bounded graceful timeout, and forced termination;
- full-SHA checkout pinning and disabled credential persistence;
- exact OpenCode version pin and no `@latest` use;
- exclusive use of `NVIDIA_NIM_API_KEY` through `NVIDIA_API_KEY` with the explicit NVIDIA model;
- no Copilot, Anthropic, or OpenAI credential path;
- private session setting and direct `GITHUB_TOKEN` mode;
- local direct-token Git authorization, bot author identity, `EXIT` cleanup, and no ineffective `AGENT: build` claim;
- least-privilege permissions without `id-token`;
- prompt prohibitions against approval, merge, protected-branch pushes, review-agent key changes, workflow self-modification, and duplicate pull requests.

## References

Anomaly. (2026). *GitHub handler (Version 1.18.13)* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/src/cli/cmd/github.handler.ts

Anomaly. (2026). *GitHub integration*. OpenCode. https://opencode.ai/docs/github/

Anomaly. (2026). *Providers*. OpenCode. https://opencode.ai/docs/providers/

GitHub, Inc. (2026). *Automatic token authentication*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/automatic-token-authentication

GitHub, Inc. (2026). *Events that trigger workflows*. GitHub Docs. https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows

GitHub, Inc. (2026). *Security hardening for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions

NVIDIA Corporation. (2026). *LLM APIs*. NVIDIA API documentation. https://docs.api.nvidia.com/nim/reference/llm-apis
