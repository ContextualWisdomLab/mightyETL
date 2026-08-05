# Hourly OpenCode Maintenance Agent Design

## Purpose

mightyETL needs a scheduled development loop that can inspect current repository state, remediate one bounded item, and open or update a pull request without weakening independent review, exact-head validation, or merge gates. The agent must use OpenCode with `NVIDIA_NIM_API_KEY`; it must not use GitHub Copilot or change the existing review agent's credentials, provider, workflow, or authority.

## Decision

Add `.github/workflows/hourly-opencode-maintenance.yml` as a two-job workflow:

```text
maintain-repository
  ├─ protected default-branch checkout
  ├─ pinned OpenCode + NVIDIA NIM
  ├─ branch / issue / pull-request preparation
  └─ actions: read

             exact pre-agent head map
                       ↓

authorize-exact-head-checks
  ├─ no checkout or repository-code execution
  ├─ no model credential
  ├─ actions: write
  └─ exact-head workflow-run authorization only
```

Preserve `.github/workflows/hourly-pr-disposition.yml` as the deterministic fail-closed merge boundary and require a non-author approval anchored to the exact current head.

The workflow:

- runs only from the protected default branch at minute 43 of every hour;
- omits manual dispatch so a feature branch or tag cannot become scheduler source;
- pins `actions/checkout` by full SHA with persisted credentials disabled;
- installs OpenCode 1.18.13 from an immutable release asset verified by SHA-256;
- accepts exactly one regular archive member named `opencode` before extraction;
- extracts into a fresh mode-`0700` directory without restoring archive ownership or permissions and with overwrites disabled;
- rejects non-regular or symbolic-link output and verifies the exact executable version;
- maps only `${{ secrets.NVIDIA_NIM_API_KEY }}` to `NVIDIA_API_KEY`;
- selects `nvidia/deepseek-ai/deepseek-v4-pro` with no automatic model or provider fallback;
- uses the repository-scoped GitHub token without OpenCode OIDC exchange;
- bootstraps a repository-local GitHub CLI credential helper and bot author because OpenCode 1.18.13 skips internal Git setup in direct-token mode;
- removes the helper through an `EXIT` trap;
- omits the ineffective `AGENT` environment variable, allowing repository `default_agent` or OpenCode's `build` fallback;
- disables public session sharing;
- caps OpenCode with a 45-minute `TERM` timeout, 30-second `KILL` escalation, and 50-minute job timeout;
- exports a compact pre-agent pull-request head map to the isolated authorization job;
- authorizes only approval-required `pull_request` workflow runs for a still-current exact head;
- refuses automatic authorization for `.github/**` and `CODEOWNERS` changes;
- never approves or merges a pull request.

## Model selection boundary

The previous Qwen3 Coder free endpoint is marked deprecated in NVIDIA's current catalog. DeepSeek V4 Pro is selected because NVIDIA currently exposes it through a free endpoint and documents coding, agentic AI, tool use, structured output, function calling, software-engineering use cases, and long context.

The model identifier is explicit and test guarded. No fallback runs after a partial agent session because another model could operate on non-deterministic workspace state, create duplicate branches, or generate conflicting pull requests. Endpoint rejection fails visibly and requires a separate test-first model-selection change. The repository does not claim NVIDIA benchmarks as mightyETL performance.

## Supply-chain boundary

An exact npm version is not a content identity. The workflow consumes the immutable upstream release asset directly.

The installer:

1. downloads only over HTTPS with failure handling and TLS 1.2 minimum;
2. verifies SHA-256 `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937`;
3. requires exactly one archive member named `opencode`;
4. under `LC_ALL=C`, requires GNU tar's regular-file type character `-` before extraction;
5. recreates a private mode-`0700` directory and refuses overwrites;
6. extracts without restoring archive ownership or permissions;
7. requires a regular non-symbolic-link output file;
8. applies executable mode only to that file;
9. verifies version `1.18.13` before adding it to `GITHUB_PATH`.

Checksum binding, pre-extraction name and type checks, private extraction, overwrite refusal, and post-extraction checks are cumulative controls. They avoid mutable package-manager bootstrapping and unconstrained archive extraction.

## Direct-token credential lifecycle

OpenCode 1.18.13 uses `GITHUB_TOKEN` for GitHub API access when `USE_GITHUB_TOKEN=true` and skips its internal Git configuration. With `persist-credentials: false`, ordinary `git commit` and `git push` need explicit local author and HTTPS helper configuration.

The maintenance job therefore:

1. fails closed if `NVIDIA_API_KEY`, `GITHUB_TOKEN`, or `GH_TOKEN` is empty;
2. resets inherited GitHub credential helpers in repository-local configuration;
3. installs `!gh auth git-credential`, which reads the ephemeral token from `GH_TOKEN` only when Git requests credentials;
4. sets local author identity to `opencode-agent[bot]`;
5. removes the helper through an `EXIT` trap after success, failure, timeout, or forced termination.

No encoded token, personal token, OIDC permission, tracked credential file, alternate model credential, or review-agent secret is introduced.

## Permission and code-execution boundary

The workflow-level permission is only `contents: read`.

The `maintain-repository` job receives Actions read plus the minimum check, branch, issue, pull-request, security-read, and status-read permissions required for bounded development. It executes OpenCode and repository tests but has no Actions write authority.

The `authorize-exact-head-checks` job receives Actions write plus read-only contents and pull-request metadata. It never checks out or executes repository code and never receives `NVIDIA_API_KEY`. Its only write operation is authorizing an approval-required workflow run after exact-head verification.

This separation prevents OpenCode, generated code, and checked-out repository scripts from using Actions write permission while still closing the `GITHUB_TOKEN` recursive-trigger gap.

## Exact-head authorization algorithm

1. Before OpenCode, snapshot all same-repository pull requests targeting `develop` as `{pull_request_number: head_sha}`.
2. Export that compact object as the maintenance job output.
3. Run the authorization job after maintenance success or failure unless the workflow was cancelled.
4. Require the output to exist and parse as a JSON object.
5. Enumerate the same pull-request set after OpenCode.
6. Select only a new pull request or a changed head.
7. Refuse automatic authorization when any `.github/**` or `CODEOWNERS` path changed.
8. Verify the still-current pull-request head equals the expected SHA.
9. Discover only `pull_request` workflow runs for that SHA.
10. Fail if no exact-head run materializes.
11. Verify the current head again immediately before authorization.
12. Authorize only `action_required` or `waiting` runs.

Expected SHA values are passed to `jq` as data rather than interpolated into jq source. Authorization begins validation but conveys no review, merge, or success decision.

## Agent authority boundary

The prompt is part of the security boundary. The agent may inspect, test, edit, commit, push one feature branch, update one dependency-eligible development pull request, or open one pull request. It must not:

- approve or merge a pull request;
- push directly to `develop` or `main`;
- bypass checks, branch protection, security gates, coverage, or independent review;
- alter review-agent workflows, providers, credentials, secret names, `CODEOWNERS`, branch protection, or repository secrets;
- modify workflow policy unless a specifically labeled issue authorizes the bounded change;
- expose secret values or sensitive ETL payload information;
- create a second development pull request while another is dependency-eligible;
- publish a release without separate release authorization and every acceptance gate.

The deterministic disposition workflow independently evaluates reviews, current threads, named checks, status contexts, labels, mergeability, and expected head SHA.

## Data flow

1. GitHub starts the schedule from the protected default branch.
2. The maintenance job checks out source without persisting credentials.
3. The installer validates and installs OpenCode.
4. The job snapshots direct `develop` pull-request heads and exports the map.
5. The shell validates model and repository credentials, installs local Git identity and helper, and registers cleanup.
6. OpenCode calls `nvidia/deepseek-ai/deepseek-v4-pro`, inspects the queue, and performs one bounded test-first slice.
7. OpenCode leaves one branch and pull request but does not approve or merge.
8. The helper cleanup trap runs.
9. The isolated authorization job compares before and after heads and rejects policy-changing pull requests.
10. It authorizes only approval-required runs for an unchanged exact head.
11. CI, security, independent review, branch protection, and deterministic disposition evaluate that exact head separately.

## Failure behavior

Missing credentials, deprecated or rejected model, NVIDIA outage, download failure, checksum mismatch, archive mismatch, non-regular entry, extracted-file mismatch, version mismatch, Git bootstrap failure, timeout, test failure, missing pre-agent output, policy-file change, head movement, absent workflow run, authorization rejection, or permission denial fails visibly. No provider fallback or partial-success claim is allowed. Concurrency is serialized.

## Verification

Repository tests fail unless they prove:

- hourly serialized scheduling and bounded forced termination;
- immutable checkout and OpenCode content pins;
- exact pre-extraction member name and regular-file type;
- private extraction, overwrite refusal, and regular non-symbolic-link output;
- exclusive use of `NVIDIA_NIM_API_KEY` and the selected NVIDIA model;
- direct-token Git bootstrap and cleanup without stored authorization data;
- workflow-level read-only permissions;
- Actions write absent from the OpenCode job;
- exactly one isolated non-checkout authorization job with Actions write;
- before/after exact-head evidence, `.github/**` and `CODEOWNERS` exclusion, exact-run filtering, and double SHA validation;
- prompt prohibitions against approval, merge, protected-branch writes, review-agent changes, self-modification, and duplicate pull requests.

## References — APA 7th

Anomaly. (2026). *GitHub handler (Version 1.18.13)* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/src/cli/cmd/github.handler.ts

Anomaly. (2026). *OpenCode release v1.18.13* [Software release]. GitHub. https://github.com/anomalyco/opencode/releases/tag/v1.18.13

Free Software Foundation. (2023). *GNU tar 1.35: Security*. https://www.gnu.org/software/tar/manual/html_section/Security.html

GitHub, Inc. (2026). *GITHUB_TOKEN*. GitHub Docs. https://docs.github.com/en/actions/concepts/security/github_token

GitHub, Inc. (2026). *GitHub CLI manual: gh auth git-credential*. GitHub CLI Manual. https://cli.github.com/manual/gh_auth_git-credential

GitHub, Inc. (2026). *REST API endpoints for workflow runs*. GitHub Docs. https://docs.github.com/en/rest/actions/workflow-runs

GitHub, Inc. (2026). *Triggering a workflow*. GitHub Docs. https://docs.github.com/en/actions/using-workflows/triggering-a-workflow

GitHub, Inc. (2026). *Workflow syntax for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax

NVIDIA Corporation. (2026). *DeepSeek V4 Pro*. NVIDIA NIM API catalog. https://build.nvidia.com/deepseek-ai/deepseek-v4-pro

NVIDIA Corporation. (2026). *DeepSeek AI / DeepSeek V4 Pro*. NVIDIA NIM API reference. https://docs.api.nvidia.com/nim/reference/deepseek-ai-deepseek-v4-pro
