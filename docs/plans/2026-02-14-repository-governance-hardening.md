# Repository Governance Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to
> implement this plan task-by-task.

**Goal:** Bring repository governance and security maintenance to a
production-ready baseline (docs, CI, security checks, templates, and
dependency automation).

**Architecture:** Keep existing Maven multi-module structure intact and
add governance assets around it: contributor docs, issue/PR templates,
and GitHub workflows for CI, dependency/supply-chain checks, and code
scanning gates.

**Tech Stack:** GitHub Actions, Maven, Java 25, Markdown docs,
Dependabot, OpenSSF Scorecard, CodeQL, Dependency Review.

---

## Task 1: Baseline governance docs

**Files:**

- Create: `AGENTS.md`
- Create: `CONTRIBUTING.md`
- Modify: `SECURITY.md`
- Modify: `ARCHITECTURE.md` (optional, only if metadata/path drift is found)

**Steps:**

1. Add `AGENTS.md` with repository-specific agent guardrails and
   operational defaults.
2. Add `CONTRIBUTING.md` with branching, local test, lint, and PR
   expectations.
3. Expand `SECURITY.md` into a real security policy with reporting SLA
   and response process.
4. Verify `ARCHITECTURE.md` key paths/services are current and update
   metadata only if drift is found.

## Task 2: GitHub project hygiene templates

**Files:**

- Create: `.github/PULL_REQUEST_TEMPLATE.md`
- Create: `.github/ISSUE_TEMPLATE/bug_report.md`
- Create: `.github/ISSUE_TEMPLATE/feature_request.md`
- Create: `.github/ISSUE_TEMPLATE/config.yml`

**Steps:**

1. Add PR template with risk, test evidence, and security checklist
   sections.
2. Add issue templates for bug/feature with reproducibility and
   acceptance criteria.
3. Add issue template config to guide users to discussions/docs when
   relevant.

## Task 3: Dependabot and Actions hardening

**Files:**

- Modify: `.github/dependabot.yml`
- Modify: `.github/workflows/ci.yml`
- Modify: `.github/workflows/sbom.yml`
- Create: `.github/workflows/codeql.yml`
- Create: `.github/workflows/dependency-review.yml`
- Create: `.github/workflows/scorecard.yml`

**Steps:**

1. Expand Dependabot schedule/labels/groups and include GitHub Actions
   updates.
2. Fix CI trigger logic so PR and push checks actually run by default.
3. Keep SBOM build on default triggers; preserve local fallback
   strategy.
4. Add CodeQL scanning workflow for Java/Kotlin.
5. Add dependency review workflow for PR dependency diffs.
6. Add OpenSSF Scorecard workflow for supply-chain posture.

## Task 4: Local verification evidence

**Files:**

- Modify: `README.md` (if needed to link new governance docs)

**Steps:**

1. Run Maven test/build commands.
2. Run Markdown lint on changed Markdown files.
3. Validate GitHub workflow YAML with local checks where possible.
4. Record any permission-limited GitHub API checks as `BLOCKED` with
   exact commands.
