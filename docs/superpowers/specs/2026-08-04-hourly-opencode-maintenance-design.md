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
- disable public session sharing;
- cap each run with workflow and process timeouts;
- run from the protected default branch and never from pull-request code.

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
4. `opencode github run` receives the bounded maintenance prompt, NVIDIA model selection, `GITHUB_TOKEN`, and `NVIDIA_API_KEY` alias.
5. The agent inspects every open pull request first. If one exists, it works only on the dependency-eligible current head. If none exists, it selects one bounded buyer-visible gap, preferring the durable worker lifecycle tracked in issue #120.
6. The agent leaves work in a feature branch and pull request. It does not approve or merge.
7. Existing CI, security, review, and the deterministic disposition workflow evaluate the exact head independently.

## Failure behavior

- A missing `NVIDIA_NIM_API_KEY`, unavailable NVIDIA endpoint, OpenCode installation mismatch, timeout, test failure, or GitHub permission denial fails the scheduled job visibly.
- No fallback provider or Copilot credential is configured.
- Concurrency is serialized with `cancel-in-progress: false`, preventing overlapping maintenance runs from racing.
- A timed-out process receives `TERM` before the workflow hard timeout.
- The workflow does not publish a release or merge partial work.

## Verification

A repository test will parse the workflow as text and fail unless it proves all of the following:

- hourly off-peak schedule, serialized concurrency, and bounded timeout;
- full-SHA checkout pinning and disabled credential persistence;
- exact OpenCode version pin and no `@latest` use;
- exclusive use of `NVIDIA_NIM_API_KEY` through `NVIDIA_API_KEY` with the explicit NVIDIA model;
- no Copilot, Anthropic, or OpenAI credential path;
- private session setting and direct `GITHUB_TOKEN` mode;
- least-privilege permissions without `id-token`;
- prompt prohibitions against approval, merge, protected-branch pushes, review-agent key changes, workflow self-modification, and duplicate pull requests.

## References

Anomaly. (2026). *GitHub integration*. OpenCode. https://opencode.ai/docs/github/

Anomaly. (2026). *Providers*. OpenCode. https://opencode.ai/docs/providers/

GitHub, Inc. (2026). *Automatic token authentication*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/automatic-token-authentication

GitHub, Inc. (2026). *Events that trigger workflows*. GitHub Docs. https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows

NVIDIA Corporation. (2026). *LLM APIs*. NVIDIA API documentation. https://docs.api.nvidia.com/nim/reference/llm-apis