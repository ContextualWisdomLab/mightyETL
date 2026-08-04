# Hourly OpenCode Maintenance Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fail-closed hourly OpenCode development agent that uses `NVIDIA_NIM_API_KEY` without changing the independent review agent or deterministic merge workflow.

**Architecture:** A new scheduled GitHub Actions workflow runs OpenCode from the protected default branch, uses a pinned NVIDIA model and exact OpenCode version, and may only prepare feature-branch pull requests. Existing review, CI, security, and hourly merge-disposition automation remain independent and authoritative.

**Tech Stack:** GitHub Actions, OpenCode 1.18.13, NVIDIA NIM, bash, Maven, JUnit 5.

## Global Constraints

- Keep `.github/workflows/hourly-pr-disposition.yml` and review-agent key configuration unchanged.
- Use only `${{ secrets.NVIDIA_NIM_API_KEY }}` for the scheduled model credential and expose it to OpenCode as `NVIDIA_API_KEY`.
- Never configure GitHub Copilot, Anthropic, or OpenAI credentials as fallbacks.
- Pin third-party workflow sources and executable package versions immutably.
- The scheduled agent may open or update a pull request, but may never approve, merge, bypass protection, or push to `develop` or `main`.
- Preserve standalone operation and modular CWL service compatibility.

---

### Task 1: Add the fail-closed workflow contract test

**Files:**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeMaintenanceWorkflowTest.java`

**Interfaces:**
- Consumes: repository root discovery pattern from `HourlyPrDispositionWorkflowTest`.
- Produces: a text-level security and configuration contract for `.github/workflows/hourly-opencode-maintenance.yml`.

- [ ] **Step 1: Write the failing test**

Create a JUnit 5 class that first asserts the workflow file exists, then verifies the schedule, concurrency, timeout, immutable checkout SHA, `persist-credentials: false`, exact `opencode-ai@1.18.13` installation, NVIDIA-only credential mapping, explicit NVIDIA Qwen3 Coder model, private sharing, direct GitHub token mode, least-privilege permissions, no `id-token`, and prompt prohibitions.

- [ ] **Step 2: Run the focused test to verify RED**

Run:

```bash
./mvnw -pl etl-service -Dtest=HourlyOpenCodeMaintenanceWorkflowTest test
```

Expected: FAIL at the explicit existence assertion because `.github/workflows/hourly-opencode-maintenance.yml` does not exist.

- [ ] **Step 3: Commit the failing contract**

```bash
git add etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeMaintenanceWorkflowTest.java
git commit -m "test(ci): define hourly OpenCode maintenance contract"
```

### Task 2: Implement the pinned NVIDIA OpenCode workflow

**Files:**
- Create: `.github/workflows/hourly-opencode-maintenance.yml`

**Interfaces:**
- Consumes: repository secret `NVIDIA_NIM_API_KEY`, built-in `GITHUB_TOKEN`, OpenCode CLI 1.18.13.
- Produces: one serialized scheduled maintenance run that can prepare a feature-branch pull request but cannot approve or merge.

- [ ] **Step 1: Add the minimal workflow**

Configure `schedule` at `43 * * * *`, `workflow_dispatch`, serialized concurrency, `timeout-minutes: 50`, minimal permissions, a full-SHA checkout with disabled persisted credentials, exact npm installation, exact version verification, and `timeout --signal=TERM 45m opencode github run`.

Set:

```yaml
GITHUB_TOKEN: ${{ github.token }}
NVIDIA_API_KEY: ${{ secrets.NVIDIA_NIM_API_KEY }}
MODEL: nvidia/qwen/qwen3-coder-480b-a35b-instruct
AGENT: build
SHARE: "false"
USE_GITHUB_TOKEN: "true"
```

The prompt must encode every authority boundary from the design and must instruct the agent to inspect all exact current PR heads before selecting work.

- [ ] **Step 2: Run the focused test to verify GREEN**

```bash
./mvnw -pl etl-service -Dtest=HourlyOpenCodeMaintenanceWorkflowTest test
```

Expected: PASS.

- [ ] **Step 3: Run workflow and documentation contract tests**

```bash
./mvnw -pl etl-service -Dtest='HourlyOpenCodeMaintenanceWorkflowTest,HourlyPrDispositionWorkflowTest,DocumentationValidationTest' test
```

Expected: PASS with no skipped tests.

- [ ] **Step 4: Commit the workflow**

```bash
git add .github/workflows/hourly-opencode-maintenance.yml
git commit -m "ci: schedule NVIDIA OpenCode maintenance agent"
```

### Task 3: Document operations and release notes

**Files:**
- Create: `docs/operations/hourly-opencode-maintenance.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: behavior and constraints from the workflow.
- Produces: beginner-readable activation, failure, rollback, security, and evidence documentation.

- [ ] **Step 1: Add the operator document**

Document the secret name, provider alias, exact model and version, schedule, permissions, branch/PR lifecycle, separation from review and merge agents, failure modes, rollback procedure, and APA 7 references to OpenCode, NVIDIA, and GitHub primary documentation.

- [ ] **Step 2: Update `CHANGELOG.md`**

Add an Unreleased entry describing the separate pinned OpenCode/NVIDIA maintenance workflow and its review-agent/merge-agent isolation.

- [ ] **Step 3: Run the full reactor tests**

```bash
./mvnw -B test
```

Expected: all modules build successfully; no project test is skipped.

- [ ] **Step 4: Commit documentation**

```bash
git add docs/operations/hourly-opencode-maintenance.md CHANGELOG.md
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