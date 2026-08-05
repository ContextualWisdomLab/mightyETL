# Hourly OpenCode Maintenance Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fail-closed hourly OpenCode development agent that uses `NVIDIA_NIM_API_KEY`, prepares one bounded development pull request, starts validation for an agent-written exact head, and never changes the independent review agent or deterministic merge authority.

**Architecture:** A protected default-branch GitHub Actions workflow has two jobs. `maintain-repository` installs a checksum-verified immutable OpenCode release and may prepare one feature branch without Actions write permission. `authorize-exact-head-checks` never checks out repository code, receives no model secret, and uses the sole Actions write grant to authorize only approval-required pull-request workflow runs for an unchanged exact head. Existing review, security, branch protection, and merge-disposition automation remain independent and authoritative.

**Tech Stack:** GitHub Actions, OpenCode 1.18.13 immutable release archive, NVIDIA NIM, DeepSeek V4 Pro, GitHub CLI credential helper, GNU Coreutils, GNU tar, Bash, jq, Maven, JUnit 5.

## Global constraints

- Keep the review-agent provider, workflow, credential flow, and secret names unchanged.
- Preserve `.github/workflows/hourly-pr-disposition.yml` as the independent exact-head merge boundary.
- Use only `${{ secrets.NVIDIA_NIM_API_KEY }}` for the model credential and expose it as `NVIDIA_API_KEY` only to the OpenCode step.
- Pin `MODEL: nvidia/deepseek-ai/deepseek-v4-pro`; reject deprecated or non-NVIDIA fallback identifiers.
- Pin executable content and third-party workflow sources immutably.
- Verify OpenCode 1.18.13 Linux x64 SHA-256 `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937` before extraction.
- Require exactly one regular archive entry named `opencode`; use a fresh mode-`0700` directory, refuse overwrites, and reject non-regular or symbolic-link output.
- Keep checkout credential persistence disabled; use only a repository-local GitHub CLI helper removed by an `EXIT` trap.
- Keep workflow-level permissions read-only.
- Keep Actions write authority out of the OpenCode job and in one isolated non-checkout job.
- Refuse automatic workflow-run authorization for `.github/**` and `CODEOWNERS` changes.
- Bind run discovery and authorization to the exact still-current head SHA.
- Bound OpenCode with `TERM` after 45 minutes, `KILL` after a 30-second grace period, and a 50-minute job timeout.
- The agent may create or update one pull request but may never approve, merge, bypass protection, publish, or push to `develop` or `main`.
- Preserve standalone operation and modular CWL service compatibility.

---

### Task 1: Add fail-closed workflow contracts

**Files:**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeMaintenanceWorkflowTest.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeArchiveValidationTest.java`

- [x] **Step 1: Write the initial missing-workflow test**

Require workflow existence, hourly schedule, serialized concurrency, bounded timeout, immutable checkout, no persisted credentials, NVIDIA-only credentials, private sharing, and prompt prohibitions.

- [x] **Step 2: Verify initial RED**

```bash
./mvnw -pl etl-service -Dtest=HourlyOpenCodeMaintenanceWorkflowTest test
```

Observed: the existence assertion failed before the workflow was created.

- [x] **Step 3: Add immutable-installation and direct-token contracts**

Require exact release URL and checksum, no npm install, graceful and forced timeout, removable GitHub CLI helper, bot author, cleanup trap, and no encoded authorization header.

- [x] **Step 4: Add archive-member and entry-type contracts**

Require one member named `opencode`, locale-stable GNU tar metadata, regular-file type `-`, validation before extraction, private directory, overwrite refusal, and post-extraction file checks.

- [x] **Step 5: Add current-model availability contract**

Require `nvidia/deepseek-ai/deepseek-v4-pro` and reject the deprecated Qwen3 Coder endpoint.

- [x] **Step 6: Add exact-head revalidation contracts**

Require a pre-agent head snapshot, isolated Actions-write job, no checkout or NVIDIA secret in that job, `.github/**` and `CODEOWNERS` exclusion, exact-run discovery, double SHA validation, absent-run failure, and no review or merge operation.

- [x] **Step 7: Verify RED cycles**

The workflow-existence, archive-member, archive-type, model-selection, exact-head authorization, policy-path exclusion, and Actions-write isolation contracts were committed before their corresponding production behavior.

### Task 2: Implement the bounded NVIDIA OpenCode job

**Files:**
- Create: `.github/workflows/hourly-opencode-maintenance.yml`

- [x] **Step 1: Add protected scheduling and authority boundary**

Configure `43 * * * *`, omit manual dispatch, serialize concurrency, set a 50-minute job timeout, checkout protected default-branch source, and prohibit approval, merge, protected-branch push, review-agent modification, secret disclosure, duplicate PR creation, and release publication.

- [x] **Step 2: Add immutable OpenCode installation**

Download the immutable `v1.18.13` Linux x64 archive over HTTPS, validate its SHA-256, require one regular entry named `opencode`, extract privately without restoring archive ownership or permissions, refuse overwrite, reject non-regular or symbolic-link output, and verify exact version.

- [x] **Step 3: Add direct-token Git bootstrap**

Fail closed on missing token aliases, reset inherited helpers locally, install `!gh auth git-credential`, set `opencode-agent[bot]` local identity, and remove the helper through an `EXIT` trap.

- [x] **Step 4: Select the current NVIDIA coding endpoint**

Set `NVIDIA_API_KEY`, `MODEL`, `SHARE=false`, and `USE_GITHUB_TOKEN=true` without provider or model fallback.

- [x] **Step 5: Remove Actions write from the OpenCode job**

Give `maintain-repository` Actions read plus only the branch, issue, PR, check, security-read, and status permissions required for bounded maintenance.

### Task 3: Implement isolated exact-head run authorization

**Files:**
- Modify: `.github/workflows/hourly-opencode-maintenance.yml`
- Modify: `etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeMaintenanceWorkflowTest.java`

- [x] **Step 1: Export the pre-agent head map**

Snapshot same-repository pull requests targeting `develop` before OpenCode and expose compact JSON as a maintenance-job output.

- [x] **Step 2: Add the isolated authorization job**

Run after maintenance success or failure unless cancelled. Give it only `actions: write`, `contents: read`, and `pull-requests: read`. Do not checkout repository code or pass the NVIDIA credential.

- [x] **Step 3: Add policy-path and TOCTOU gates**

Reject `.github/**` and `CODEOWNERS` changes, verify the exact head before discovery and immediately before authorization, and pass the expected SHA to jq as data.

- [x] **Step 4: Authorize only exact approval-required runs**

Discover `pull_request` runs by exact `head_sha`, fail when no run appears, and call the approval endpoint only for `action_required` or `waiting` runs. Do not approve or merge the pull request.

- [ ] **Step 5: Verify focused GREEN on the final exact head**

```bash
./mvnw -pl etl-service \
  -Dtest='HourlyOpenCodeMaintenanceWorkflowTest,HourlyOpenCodeArchiveValidationTest' test
```

Expected: zero failures, errors, and skipped project tests.

### Task 4: Complete evidence and release notes

**Files:**
- Create: `docs/operations/hourly-opencode-maintenance.md`
- Create: `docs/doctoring/opencode-archive-extraction-evidence.md`
- Create: `docs/doctoring/nvidia-opencode-model-selection-evidence.md`
- Create: `docs/doctoring/github-token-exact-head-check-authorization-evidence.md`
- Modify: `docs/superpowers/specs/2026-08-04-hourly-opencode-maintenance-design.md`
- Modify: `docs/superpowers/plans/2026-08-04-hourly-opencode-maintenance-plan.md`
- Modify: `CHANGELOG.md`

- [x] **Step 1: Document archive and credential boundaries**

Record immutable release, checksum, exact member and type, extraction controls, direct-token helper lifecycle, timeout escalation, authority restrictions, failure behavior, and rollback.

- [x] **Step 2: Document model selection**

Record deprecated-endpoint rejection, current NVIDIA endpoint evidence, no-fallback semantics, RED evidence, and APA 7 references.

- [x] **Step 3: Document exact-head run authorization**

Record GitHub-token recursive-trigger behavior, split-job authority, before/after SHA evidence, policy-path exclusion, Actions-write isolation, TOCTOU validation, failure behavior, rollback, and APA 7 references.

- [x] **Step 4: Align `CHANGELOG.md`**

Record the NVIDIA model, immutable installation, isolated exact-head workflow-run authorization, and all doctoring evidence files under `Unreleased`.

- [ ] **Step 5: Run full reactor verification**

```bash
./mvnw -B test
```

Expected: all modules succeed; no project test is skipped.

### Task 5: Verify and integrate the protected workflow-change pull request

**Files:**
- No additional source files.

- [ ] **Step 1: Verify exact branch head and diff**

```bash
git status --short
git rev-parse HEAD
git diff develop...HEAD --check
./mvnw -B test
```

Expected: clean tree, no whitespace errors, successful build.

- [x] **Step 2: Open and label the pull request**

Use title `ci: schedule NVIDIA OpenCode maintenance agent`; apply `automerge-workflow`, and retain `manual-merge` until exact-head checks and non-author approval exist.

- [ ] **Step 3: Reinspect all feedback on the final exact head**

Inspect human, CodeRabbit, GitHub Advanced Security, Dependabot, and automated feedback. Resolve only findings addressed by the current head.

- [ ] **Step 4: Verify every final exact-head gate**

Require successful Ubuntu, macOS, Windows, Dependency Review, SBOM, Semgrep, Trivy, OSV, Scorecard, combined status, mergeability, and zero unresolved current threads. Pending, queued, cancelled, neutral-required, skipped-required, stale, or absent evidence is not passing.

- [ ] **Step 5: Require independent exact-head approval and merge**

Do not self-approve. After a non-author approval whose commit ID equals the current head and every exact-head gate succeeds, remove `manual-merge` and squash-merge using the expected head SHA. Otherwise retain the hold and identify the exact external gate.
