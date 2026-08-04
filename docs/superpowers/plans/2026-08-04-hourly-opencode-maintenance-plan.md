# Hourly OpenCode Maintenance Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fail-closed hourly OpenCode development agent that uses `NVIDIA_NIM_API_KEY` without changing the independent review agent or deterministic merge workflow.

**Architecture:** A new scheduled GitHub Actions workflow runs OpenCode from the protected default branch, uses a pinned NVIDIA model and exact OpenCode version, and may only prepare feature-branch pull requests. Existing review, CI, security, and hourly merge-disposition automation remain independent and authoritative. Direct `GITHUB_TOKEN` mode retains `persist-credentials: false` and adds a repository-local, short-lived Git authorization header because OpenCode 1.18.13 skips its own Git setup in that mode.

**Tech Stack:** GitHub Actions, OpenCode 1.18.13, NVIDIA NIM, bash, Maven, JUnit 5.

## Global Constraints

- Keep `.github/workflows/hourly-pr-disposition.yml` and review-agent key configuration unchanged.
- Use only `${{ secrets.NVIDIA_NIM_API_KEY }}` for the scheduled model credential and expose it to OpenCode as `NVIDIA_API_KEY`.
- Never configure GitHub Copilot, Anthropic, or OpenAI credentials as fallbacks.
- Pin third-party workflow sources and executable package versions immutably.
- Keep checkout credential persistence disabled; bootstrap only a repository-local direct-token authorization header and remove it through an `EXIT` trap.
- Do not claim that raw OpenCode 1.18.13 consumes an `AGENT` environment variable; it uses repository `default_agent` configuration or its `build` fallback.
- The scheduled agent may open or update a pull request, but may never approve, merge, bypass protection, or push to `develop` or `main`.
- Preserve standalone operation and modular CWL service compatibility.

---

### Task 1: Add the fail-closed workflow contract test

**Files:**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeMaintenanceWorkflowTest.java`

**Interfaces:**
- Consumes: repository root discovery pattern from `HourlyPrDispositionWorkflowTest` and the pinned OpenCode 1.18.13 GitHub-handler behavior.
- Produces: a text-level security and configuration contract for `.github/workflows/hourly-opencode-maintenance.yml`.

- [ ] **Step 1: Write the failing test**

Create a JUnit 5 class that first asserts the workflow file exists, then verifies the schedule, concurrency, timeout, immutable checkout SHA, `persist-credentials: false`, exact `opencode-ai@1.18.13` installation, NVIDIA-only credential mapping, explicit NVIDIA Qwen3 Coder model, private sharing, direct GitHub token mode, least-privilege permissions, no `id-token`, and prompt prohibitions.

Add a focused direct-token contract that requires a local GitHub authorization header, local bot author identity, credential cleanup through an `EXIT` trap, and removal of the ineffective `AGENT: build` environment claim.

- [ ] **Step 2: Run the focused test to verify RED**

Run:

```bash
./mvnw -pl etl-service -Dtest=HourlyOpenCodeMaintenanceWorkflowTest test
```

Expected initially: FAIL at the explicit existence assertion because `.github/workflows/hourly-opencode-maintenance.yml` does not exist.

After discovering the direct-token gap, expected regression RED: FAIL because the workflow has `persist-credentials: false` and `USE_GITHUB_TOKEN=true` without the local authorization header, author identity, and cleanup contract required for OpenCode 1.18.13 infrastructure-managed commits and pushes.

- [ ] **Step 3: Commit the failing contract**

```bash
git add etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeMaintenanceWorkflowTest.java
git commit -m "test(ci): define hourly OpenCode maintenance contract"
git commit -am "test(ci): require direct-token git bootstrap"
```

### Task 2: Implement the pinned NVIDIA OpenCode workflow

**Files:**
- Create: `.github/workflows/hourly-opencode-maintenance.yml`

**Interfaces:**
- Consumes: repository secret `NVIDIA_NIM_API_KEY`, built-in `GITHUB_TOKEN`, OpenCode CLI 1.18.13.
- Produces: one serialized scheduled maintenance run that can prepare a feature-branch pull request but cannot approve or merge.

- [ ] **Step 1: Add the minimal workflow**

Configure `schedule` at `43 * * * *`, `workflow_dispatch`, serialized concurrency, `timeout-minutes: 50`, minimal permissions, a full-SHA checkout with disabled persisted credentials, exact npm installation, exact version verification, and `timeout --signal=TERM 45m opencode github run`.

Set only the OpenCode environment variables that raw OpenCode 1.18.13 consumes:

```yaml
GITHUB_TOKEN: ${{ github.token }}
NVIDIA_API_KEY: ${{ secrets.NVIDIA_NIM_API_KEY }}
MODEL: nvidia/qwen/qwen3-coder-480b-a35b-instruct
SHARE: "false"
USE_GITHUB_TOKEN: "true"
PROMPT: |
  ...
```

The prompt must encode every authority boundary from the design and must instruct the agent to inspect all exact current PR heads before selecting work.

- [ ] **Step 2: Bootstrap direct-token Git access without persisted checkout credentials**

Before starting OpenCode:

1. fail closed if `GITHUB_TOKEN` is empty;
2. define the repository-local key `http.https://github.com/.extraheader`;
3. install an `EXIT` trap that removes that header;
4. create a Basic authorization value from `x-access-token:${GITHUB_TOKEN}` without printing it;
5. set the header and immediately unset the temporary shell variable;
6. configure repository-local `user.name` and `user.email` for `opencode-agent[bot]`.

Do not persist checkout credentials, add a personal token, enable OIDC, or store the authorization value in tracked files.

- [ ] **Step 3: Run the focused test to verify GREEN**

```bash
./mvnw -pl etl-service -Dtest=HourlyOpenCodeMaintenanceWorkflowTest test
```

Expected: PASS.

- [ ] **Step 4: Run workflow and documentation contract tests**

```bash
./mvnw -pl etl-service -Dtest='HourlyOpenCodeMaintenanceWorkflowTest,HourlyPrDispositionWorkflowTest,DocumentationValidationTest' test
```

Expected: PASS with no skipped tests.

- [ ] **Step 5: Commit the workflow**

```bash
git add .github/workflows/hourly-opencode-maintenance.yml
git commit -m "ci: schedule NVIDIA OpenCode maintenance agent"
git commit -am "fix(ci): bootstrap OpenCode direct-token git access"
```

### Task 3: Document operations and release notes

**Files:**
- Create: `docs/operations/hourly-opencode-maintenance.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/superpowers/specs/2026-08-04-hourly-opencode-maintenance-design.md`

**Interfaces:**
- Consumes: behavior and constraints from the workflow and OpenCode 1.18.13 primary source.
- Produces: beginner-readable activation, failure, rollback, credential-lifecycle, security, and evidence documentation.

- [ ] **Step 1: Add the operator document**

Document the secret name, provider alias, exact model and version, schedule, permissions, branch/PR lifecycle, direct-token Git bootstrap, credential cleanup, repository/default agent behavior, separation from review and merge agents, failure modes, rollback procedure, and APA 7 references to OpenCode, NVIDIA, and GitHub primary documentation.

- [ ] **Step 2: Update the design and `CHANGELOG.md`**

Record why direct-token mode requires an explicit local Git bootstrap while checkout credential persistence remains disabled. Add an Unreleased entry describing the separate pinned OpenCode/NVIDIA maintenance workflow and its review-agent/merge-agent isolation.

- [ ] **Step 3: Run the full reactor tests**

```bash
./mvnw -B test
```

Expected: all modules build successfully; no project test is skipped.

- [ ] **Step 4: Commit documentation**

```bash
git add docs/operations/hourly-opencode-maintenance.md \
  docs/superpowers/specs/2026-08-04-hourly-opencode-maintenance-design.md \
  docs/superpowers/plans/2026-08-04-hourly-opencode-maintenance-plan.md \
  CHANGELOG.md
git commit -m "docs(ci): document OpenCode maintenance operations"
```

### Task 4: Verify and open the protected workflow-change pull request

**Files:**
- No additional source files.

**Interfaces:**
- Consumes: completed branch and all exact-head test results.
- Produces: a ready-for-review pull request targeting `develop`.

- [ ] **Step 1: Verify exact branch head and diff**

```bash
git status --short
git rev-parse HEAD
git diff develop...HEAD --check
./mvnw -B test
```

Expected: clean tree, no whitespace errors, successful build.

- [ ] **Step 2: Open the pull request**

Open a pull request titled `ci: schedule NVIDIA OpenCode maintenance agent`, explain the credential isolation and authority boundary, and apply `automerge-workflow` because the deterministic disposition workflow requires explicit approval for workflow changes.

- [ ] **Step 3: Request independent review and verify exact-head checks**

Request CodeRabbit and any configured independent reviewer. Treat queued, pending, skipped-required, stale-head, or cancelled checks as not passing. Resolve only addressed current threads.

- [ ] **Step 4: Merge only after every repository gate passes**

Do not self-approve. Merge only when branch protection, independent approval, security gates, repository policy, exact-head checks, and workflow-change policy are all satisfied.
