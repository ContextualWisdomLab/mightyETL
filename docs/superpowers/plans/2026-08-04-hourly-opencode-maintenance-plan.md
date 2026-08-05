# Hourly OpenCode Maintenance Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fail-closed hourly OpenCode development agent that uses `NVIDIA_NIM_API_KEY` without changing the independent review agent or deterministic merge workflow.

**Architecture:** A new scheduled GitHub Actions workflow runs OpenCode from the protected default branch, uses a pinned NVIDIA model and checksum-verified OpenCode release, and may only prepare feature-branch pull requests. Before extraction, the installer accepts only the reviewed single-member regular-file archive shape and uses a fresh private directory plus post-extraction file-type checks. Existing review, CI, security, and hourly merge-disposition automation remain independent and authoritative. Direct `GITHUB_TOKEN` mode retains `persist-credentials: false` and adds a repository-local GitHub CLI credential helper because OpenCode 1.18.13 skips its own Git setup in that mode. A bounded `TERM` timeout escalates to `KILL` after 30 seconds so cleanup completes before the workflow-level timeout.

**Tech Stack:** GitHub Actions, OpenCode 1.18.13 immutable release archive, NVIDIA NIM, GitHub CLI credential helper, GNU Coreutils `sha256sum` and `timeout`, GNU tar, bash, Maven, JUnit 5.

## Global Constraints

- Keep the review-agent key configuration unchanged and preserve `.github/workflows/hourly-pr-disposition.yml` as the independent deterministic merge boundary.
- Use only `${{ secrets.NVIDIA_NIM_API_KEY }}` for the scheduled model credential and expose it to OpenCode as `NVIDIA_API_KEY`.
- Never configure GitHub Copilot, Anthropic, or OpenAI credentials as fallbacks.
- Pin third-party workflow sources and executable content immutably.
- Download OpenCode only from the immutable `v1.18.13` release asset and verify Linux x64 SHA-256 `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937` before extraction.
- Require exactly one archive member named `opencode` and require GNU tar's regular-file entry type before extraction; use a fresh mode-`0700` directory, refuse overwrites, and reject non-regular or symbolic-link output afterward.
- Do not use npm install commands, floating package tags, or mutable OpenCode action references.
- Keep checkout credential persistence disabled; bootstrap only a repository-local GitHub CLI credential helper and remove it through an `EXIT` trap.
- Keep the workflow-level token default read-only and scope required write permissions to the sole maintenance job.
- Bound OpenCode with `TERM` after 45 minutes, force `KILL` after a 30-second grace period, and retain a 50-minute GitHub job timeout.
- Do not claim that raw OpenCode 1.18.13 consumes an `AGENT` environment variable; it uses repository `default_agent` configuration or its `build` fallback.
- The scheduled agent may open or update a pull request, but may never approve, merge, bypass protection, or push to `develop` or `main`.
- Preserve standalone operation and modular CWL service compatibility.

---

### Task 1: Add fail-closed workflow contract tests

**Files:**
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeMaintenanceWorkflowTest.java`
- Create: `etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeArchiveValidationTest.java`

**Interfaces:**
- Consumes: repository root discovery pattern from `HourlyPrDispositionWorkflowTest`, OpenCode 1.18.13 release metadata, the pinned GitHub-handler behavior, upstream release packaging, and GNU tar extraction guidance.
- Produces: text-level security, credential, archive, and configuration contracts for `.github/workflows/hourly-opencode-maintenance.yml`.

- [x] **Step 1: Write the initial failing workflow test**

Create a JUnit 5 class that first asserts the workflow file exists, then verifies the schedule, concurrency, workflow timeout, graceful process timeout with deterministic forced termination, immutable checkout SHA, `persist-credentials: false`, immutable release URL, exact SHA-256 verification, exact OpenCode version verification, NVIDIA-only credential mapping, explicit NVIDIA Qwen3 Coder model, private sharing, direct GitHub token mode, job-scoped least-privilege permissions, no `id-token`, and prompt prohibitions.

Add a focused direct-token contract that requires a repository-local GitHub CLI credential helper, local bot author identity, helper cleanup through an `EXIT` trap, no persisted encoded authorization header, and removal of the ineffective `AGENT: build` environment claim.

- [x] **Step 2: Run the initial focused test to verify RED**

Run:

```bash
./mvnw -pl etl-service -Dtest=HourlyOpenCodeMaintenanceWorkflowTest test
```

Observed initial RED: the explicit existence assertion failed because `.github/workflows/hourly-opencode-maintenance.yml` did not exist.

Observed subsequent RED states covered the mutable npm command, workflow-scoped write permission, missing direct-token Git bootstrap, and prompt wording boundary before each corresponding production change.

- [x] **Step 3: Write archive regression tests**

Create `HourlyOpenCodeArchiveValidationTest` with complete beginner-readable Javadoc. Require the workflow to list the verified archive, accept exactly one member named `opencode`, obtain locale-stable GNU tar verbose metadata, require the regular-file type character `-`, emit stable failure messages, and perform all checks before extraction.

- [x] **Step 4: Run archive tests to verify RED**

Run:

```bash
./mvnw -pl etl-service -Dtest=HourlyOpenCodeArchiveValidationTest test
```

Observed first RED on the pull-request CI merge ref for test-only commit `751eedb852eca1165a5b936296255fc608494dad`: 284 tests, one failure, zero errors. The sole failure was `validatesOneExpectedArchiveMemberBeforeExtraction`, proving that the checksum-only installer did not satisfy the member-shape boundary.

Observed second RED on the pull-request CI merge ref for test-only commit `7b82a40b12c46aed869aeec7b387a161a7b33896`: 285 tests, one failure, zero errors, zero skipped project tests. The sole failure was `validatesRegularFileEntryTypeBeforeExtraction`, proving that name and count validation did not reject hard-link or other non-regular archive entries before extraction.

- [x] **Step 5: Commit the failing contracts**

The workflow contracts were committed before their production fixes, including the dedicated member-shape and regular-entry regression contracts.

### Task 2: Implement the checksum-pinned NVIDIA OpenCode workflow

**Files:**
- Create: `.github/workflows/hourly-opencode-maintenance.yml`

**Interfaces:**
- Consumes: repository secret `NVIDIA_NIM_API_KEY`, built-in `GITHUB_TOKEN`, immutable OpenCode v1.18.13 Linux x64 release asset.
- Produces: one serialized scheduled maintenance run that can prepare a feature-branch pull request but cannot approve or merge.

- [x] **Step 1: Add the minimal workflow**

Configure `schedule` at `43 * * * *`, `workflow_dispatch`, serialized concurrency, `timeout-minutes: 50`, workflow-level `contents: read`, job-scoped maintenance permissions, a full-SHA checkout with disabled persisted credentials, immutable release download, exact SHA-256 verification, constrained extraction, exact version verification, and `timeout --signal=TERM --kill-after=30s 45m opencode github run`.

Set only the model and GitHub environment variables required by the direct-token path:

```yaml
GITHUB_TOKEN: ${{ github.token }}
GH_TOKEN: ${{ github.token }}
NVIDIA_API_KEY: ${{ secrets.NVIDIA_NIM_API_KEY }}
MODEL: nvidia/qwen/qwen3-coder-480b-a35b-instruct
SHARE: "false"
USE_GITHUB_TOKEN: "true"
PROMPT: |
  ...
```

The prompt must encode every authority boundary from the design and instruct the agent to inspect all exact current PR heads before selecting work.

- [x] **Step 2: Install OpenCode from immutable and constrained content**

Before extraction:

1. require `curl`, `install`, `sha256sum`, `tar`, and the `ripgrep` dependency supplied by the runner;
2. download `opencode-linux-x64.tar.gz` from release `v1.18.13` over HTTPS;
3. validate SHA-256 `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937` with `sha256sum --check --strict`;
4. list the archive and require exactly one member named `opencode`;
5. under `LC_ALL=C`, obtain GNU tar verbose metadata and require its first type character to be `-` for a regular-file entry;
6. recreate the extraction directory with mode `0700`;
7. extract with ownership and archived permission restoration disabled and overwrite refusal enabled;
8. require the resulting `opencode` path to be a regular file and not a symbolic link;
9. apply executable mode only to the expected binary;
10. verify the exact reported version before adding the temporary directory to `GITHUB_PATH`.

Do not fall back to npm, a floating release, a mutable action reference, a broader archive-member allowlist, or link/special-file entry support after any failure.

- [x] **Step 3: Bootstrap direct-token Git access without persisted checkout credentials**

Before starting OpenCode:

1. fail closed if `GITHUB_TOKEN` or `GH_TOKEN` is empty;
2. define the repository-local key `credential.https://github.com.helper`;
3. install an `EXIT` trap that removes that key;
4. reset inherited helper resolution locally with an empty helper entry;
5. add `!gh auth git-credential`, which obtains the ephemeral token from `GH_TOKEN` only when Git asks for credentials;
6. configure repository-local `user.name` and `user.email` for `opencode-agent[bot]`.

Do not persist checkout credentials, encode the token into Git configuration, add a personal token, enable OIDC, or store credential material in tracked files.

- [ ] **Step 4: Run focused tests to verify GREEN**

```bash
./mvnw -pl etl-service \
  -Dtest='HourlyOpenCodeMaintenanceWorkflowTest,HourlyOpenCodeArchiveValidationTest' test
```

Expected: PASS.

- [ ] **Step 5: Run workflow and documentation contract tests**

```bash
./mvnw -pl etl-service \
  -Dtest='HourlyOpenCodeMaintenanceWorkflowTest,HourlyOpenCodeArchiveValidationTest,HourlyPrDispositionWorkflowTest,DocumentationValidationTest' test
```

Expected: PASS with no skipped project tests.

### Task 3: Document operations, evidence, and release notes

**Files:**
- Create: `docs/operations/hourly-opencode-maintenance.md`
- Create: `docs/doctoring/opencode-archive-extraction-evidence.md`
- Modify: `CHANGELOG.md`
- Modify: `docs/superpowers/specs/2026-08-04-hourly-opencode-maintenance-design.md`
- Modify: `docs/superpowers/plans/2026-08-04-hourly-opencode-maintenance-plan.md`

**Interfaces:**
- Consumes: behavior and constraints from the workflow, OpenCode 1.18.13 primary source, immutable release metadata, release-asset metadata, GNU tar security guidance, GitHub workflow permission semantics, and GitHub CLI credential behavior.
- Produces: beginner-readable activation, failure, rollback, credential-lifecycle, permission-inheritance, checksum, archive-member, archive-entry, timeout-escalation, security, and evidence documentation.

- [x] **Step 1: Add and update operator documentation**

Document the secret name, provider alias, exact model and version, release asset and checksum, exact archive name and regular-file type, private extraction directory, file-type checks, schedule, job-scoped permissions, branch/PR lifecycle, direct-token GitHub CLI helper, credential cleanup, bounded `TERM`/`KILL` behavior, repository/default agent behavior, separation from review and merge agents, failure modes, rollback procedure, and APA 7 references to OpenCode, NVIDIA, GitHub, GNU tar, and GNU Coreutils primary documentation.

- [x] **Step 2: Add doctoring evidence and update `CHANGELOG.md`**

Record the threat, evidence chain, exact fail-closed controls, both RED cycles, rollback implications, and APA 7 references in `docs/doctoring/opencode-archive-extraction-evidence.md`. Update the Unreleased entry to state that the installer validates exactly one expected regular-file archive member before extraction and rejects symbolic-link or non-regular output.

- [ ] **Step 3: Run the full reactor tests**

```bash
./mvnw -B test
```

Expected: all modules build successfully; no project test is skipped.

### Task 4: Verify and integrate the protected workflow-change pull request

**Files:**
- No additional source files.

**Interfaces:**
- Consumes: completed branch and all exact-head test results.
- Produces: a ready-for-review pull request targeting `develop` and a guarded merge only after every gate passes.

- [ ] **Step 1: Verify exact branch head and diff**

```bash
git status --short
git rev-parse HEAD
git diff develop...HEAD --check
./mvnw -B test
```

Expected: clean tree, no whitespace errors, successful build.

- [x] **Step 2: Open the pull request**

Open a pull request titled `ci: schedule NVIDIA OpenCode maintenance agent`, explain credential and supply-chain isolation, and apply `automerge-workflow` because the deterministic disposition workflow requires explicit approval for workflow changes.

- [ ] **Step 3: Request independent review and verify exact-head checks**

Request CodeRabbit and any configured independent reviewer. Treat queued, pending, skipped-required, stale-head, or cancelled checks as not passing. Resolve only addressed current threads.

- [ ] **Step 4: Merge only after every repository gate passes**

Do not self-approve. Merge only when branch protection, independent approval, security gates, repository policy, exact-head checks, and workflow-change policy are all satisfied.
