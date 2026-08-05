# Hourly OpenCode Maintenance Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fail-closed hourly OpenCode development agent that uses `NVIDIA_NIM_API_KEY` without changing the independent review agent or deterministic merge workflow.

**Architecture:** A scheduled GitHub Actions workflow runs OpenCode from protected default-branch source, installs a checksum-verified immutable OpenCode release inside a constrained extraction boundary, and may prepare only one feature-branch pull request. It calls the current free NVIDIA `deepseek-ai/deepseek-v4-pro` endpoint without automatic fallback. Existing review, CI, security, branch protection, and merge-disposition automation remain independent and authoritative. Direct `GITHUB_TOKEN` mode retains `persist-credentials: false` and uses a removable repository-local GitHub CLI helper because OpenCode 1.18.13 skips its own Git setup in that mode.

**Tech Stack:** GitHub Actions, OpenCode 1.18.13 immutable release archive, NVIDIA NIM, DeepSeek V4 Pro, GitHub CLI credential helper, GNU Coreutils, GNU tar, Bash, Maven, JUnit 5.

## Global Constraints

- Keep the review-agent provider, workflow, credential flow, and secret names unchanged.
- Preserve `.github/workflows/hourly-pr-disposition.yml` as the independent exact-head merge boundary.
- Use only `${{ secrets.NVIDIA_NIM_API_KEY }}` for the scheduled model credential and expose it as `NVIDIA_API_KEY`.
- Pin `MODEL: nvidia/deepseek-ai/deepseek-v4-pro`; reject the deprecated Qwen3 Coder free-endpoint identifier.
- Do not configure GitHub Copilot, Anthropic, OpenAI, partner-only NVIDIA, or automatic model fallbacks.
- Pin executable content and third-party workflow sources immutably.
- Verify OpenCode 1.18.13 Linux x64 SHA-256 `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937` before extraction.
- Require exactly one regular archive entry named `opencode`; use a fresh mode-`0700` directory, refuse overwrites, and reject non-regular or symbolic-link output.
- Keep checkout credential persistence disabled; use only a repository-local GitHub CLI helper removed by an `EXIT` trap.
- Keep workflow-level permissions read-only and scope necessary writes to the sole maintenance job.
- Bound OpenCode with `TERM` after 45 minutes, `KILL` after a 30-second grace period, and a 50-minute job timeout.
- Do not claim raw OpenCode 1.18.13 consumes `AGENT`; use repository `default_agent` or its `build` fallback.
- The agent may create or update one pull request but may never approve, merge, bypass protection, or push to `develop` or `main`.
- Preserve standalone operation and modular CWL service compatibility.

---

### Task 1: Add fail-closed workflow contracts

**Files:**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeMaintenanceWorkflowTest.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeArchiveValidationTest.java`

**Interfaces:**
- Consumes: repository-root discovery, OpenCode 1.18.13 source and release metadata, NVIDIA model catalog, GNU tar guidance.
- Produces: cross-platform text contracts for schedule, credentials, model, archive, permissions, and authority.

- [x] **Step 1: Write the initial missing-workflow test**

Require the workflow to exist and encode schedule, concurrency, timeout, pinned checkout, no persisted credentials, immutable OpenCode installation, NVIDIA-only credentials, private sharing, direct GitHub token mode, least privilege, and prompt prohibitions.

- [x] **Step 2: Verify initial RED**

Run:

```bash
./mvnw -pl etl-service -Dtest=HourlyOpenCodeMaintenanceWorkflowTest test
```

Observed: the existence assertion failed before the workflow was created.

- [x] **Step 3: Add immutable-installation and direct-token contracts**

Require exact release URL and checksum, no npm install, job-scoped writes, graceful and forced timeout, repository-local GitHub CLI helper, bot author, cleanup trap, and no encoded authorization header or ineffective `AGENT` claim.

- [x] **Step 4: Add archive-member and entry-type contracts**

Require exactly one member named `opencode`, locale-stable GNU tar metadata, regular-file type `-`, stable failure messages, and validation before extraction.

- [x] **Step 5: Verify archive RED cycles**

First test-only commit `751eedb852eca1165a5b936296255fc608494dad` produced 284 tests, one failure, zero errors; the sole failure was missing exact-member validation.

Second test-only commit `7b82a40b12c46aed869aeec7b387a161a7b33896` produced 285 tests, one failure, zero errors, zero skipped project tests; the sole failure was missing regular-entry validation.

- [x] **Step 6: Add current-model availability contract**

Require:

```java
assertTrue(workflow.contains("MODEL: nvidia/deepseek-ai/deepseek-v4-pro"));
assertFalse(workflow.contains("qwen/qwen3-coder-480b-a35b-instruct"));
```

- [x] **Step 7: Verify model-selection RED**

Test-only commit `42eb7d7ac8bc3912e3a50f98b427b712f78b2b9b` produced 286 tests, one failure, zero errors, zero skipped project tests in Ubuntu run `30964719079`; the sole failure was `usesCurrentFreeAgenticCodingModel` because the workflow still selected the deprecated Qwen3 Coder free endpoint.

### Task 2: Implement the bounded NVIDIA OpenCode workflow

**Files:**
- Create: `.github/workflows/hourly-opencode-maintenance.yml`

**Interfaces:**
- Consumes: `NVIDIA_NIM_API_KEY`, repository-scoped `GITHUB_TOKEN`, immutable OpenCode release asset.
- Produces: one serialized scheduled development session that can prepare but not approve or merge one pull request.

- [x] **Step 1: Add scheduling and authority boundary**

Configure `43 * * * *`, manual dispatch, serialized concurrency, 50-minute job timeout, protected source checkout, prompt constraints, and explicit prohibition of approval, merge, protected-branch push, review-agent modification, secret disclosure, duplicate PR creation, and release publication.

- [x] **Step 2: Add immutable OpenCode installation**

Download the immutable `v1.18.13` Linux x64 archive over HTTPS, validate its SHA-256, require one regular entry named `opencode`, extract into a private mode-`0700` directory without restoring archive ownership or permissions and with overwrites disabled, reject non-regular or symbolic-link output, and verify version `1.18.13`.

- [x] **Step 3: Add direct-token Git bootstrap**

Fail closed on missing token aliases, reset inherited helpers locally, install `!gh auth git-credential`, set `opencode-agent[bot]` local identity, and remove the helper through an `EXIT` trap.

- [x] **Step 4: Select a current free NVIDIA coding endpoint**

Set:

```yaml
NVIDIA_API_KEY: ${{ secrets.NVIDIA_NIM_API_KEY }}
MODEL: nvidia/deepseek-ai/deepseek-v4-pro
SHARE: "false"
USE_GITHUB_TOKEN: "true"
```

Do not add a provider or model fallback. Endpoint rejection must fail visibly.

- [ ] **Step 5: Verify focused GREEN on the integrated exact head**

Run:

```bash
./mvnw -pl etl-service \
  -Dtest='HourlyOpenCodeMaintenanceWorkflowTest,HourlyOpenCodeArchiveValidationTest' test
```

Expected: PASS with zero failures, errors, and skipped project tests.

### Task 3: Complete evidence and release notes

**Files:**
- Create: `docs/operations/hourly-opencode-maintenance.md`
- Create: `docs/doctoring/opencode-archive-extraction-evidence.md`
- Create: `docs/doctoring/nvidia-opencode-model-selection-evidence.md`
- Modify: `docs/superpowers/specs/2026-08-04-hourly-opencode-maintenance-design.md`
- Modify: `docs/superpowers/plans/2026-08-04-hourly-opencode-maintenance-plan.md`
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: implemented workflow, TDD evidence, OpenCode source, NVIDIA current catalog/API, GitHub and GNU primary documentation.
- Produces: beginner-readable operations, rollback, model-selection, supply-chain, credential, permission, timeout, and governance evidence.

- [x] **Step 1: Document archive and credential boundaries**

Document the immutable release, checksum, exact regular archive member, extraction boundary, direct-token helper lifecycle, permissions, timeout escalation, authority restrictions, failure handling, and rollback.

- [x] **Step 2: Document model selection**

Record the deprecated Qwen3 Coder free endpoint, current DeepSeek V4 Pro free endpoint, coding and tool-use suitability, one-million-token context evidence, absence of automatic fallback, RED evidence, and APA 7 references.

- [ ] **Step 3: Align `CHANGELOG.md` with the exact integrated behavior**

Replace the Qwen3 Coder model reference with `nvidia/deepseek-ai/deepseek-v4-pro`, record the deprecated-endpoint replacement, and list both doctoring evidence files.

- [ ] **Step 4: Run full reactor verification**

```bash
./mvnw -B test
```

Expected: all modules succeed; no project test is skipped.

### Task 4: Verify and integrate the protected workflow-change pull request

**Files:**
- No additional source files.

**Interfaces:**
- Consumes: final exact branch head and all CI, security, and review evidence.
- Produces: guarded squash merge to `develop` only after every policy gate succeeds.

- [ ] **Step 1: Verify exact branch head and diff**

```bash
git status --short
git rev-parse HEAD
git diff develop...HEAD --check
./mvnw -B test
```

Expected: clean tree, no whitespace errors, successful build.

- [x] **Step 2: Open and label the pull request**

Use title `ci: schedule NVIDIA OpenCode maintenance agent`; apply `automerge-workflow` because workflow files change, and retain `manual-merge` until an exact-head non-author approval exists.

- [ ] **Step 3: Reinspect all feedback on the final exact head**

Inspect human, CodeRabbit, GitHub Advanced Security, Dependabot, and automated feedback. Resolve only addressed current threads and distinguish stale or superseded findings.

- [ ] **Step 4: Verify every final exact-head gate**

Require successful Ubuntu, macOS, Windows, Dependency Review, SBOM, Semgrep, Trivy, OSV, Scorecard, combined status, mergeability, and no unresolved current thread. Treat pending, queued, cancelled, neutral-required, skipped-required, stale, or absent evidence as not passing.

- [ ] **Step 5: Require independent exact-head approval and merge**

Do not self-approve. After a non-author approval whose commit ID equals the current head and every exact-head gate succeeds, remove `manual-merge` and squash-merge using the expected head SHA. Otherwise keep the hold and report only the external approval or policy blocker after all autonomous remediation is exhausted.
