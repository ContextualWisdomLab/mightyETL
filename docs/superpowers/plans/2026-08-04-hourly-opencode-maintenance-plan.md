# Hourly OpenCode Maintenance Agent Implementation Plan

> **Execution rule:** implement each authority boundary test-first, then verify the final exact head through repository CI, security, and independent review.

**Goal:** Run one bounded NVIDIA NIM-backed development loop every hour while ensuring model execution cannot create, approve, close, or merge pull requests and cannot authorize GitHub Actions runs.

**Architecture:** Three jobs separate model execution, deterministic draft-PR publication, and exact-head workflow-run authorization. Independent OpenCode/Noema review and expected-head merge disposition remain outside all three jobs.

## Global constraints

- Use only `${{ secrets.NVIDIA_NIM_API_KEY }}` as the model secret.
- Do not introduce `COPILOT_GITHUB_TOKEN` or alter an existing review-agent secret.
- Invoke plain `opencode run`, not the GitHub lifecycle handler.
- Give the model job `pull-requests: read`, never write.
- Give exactly one non-checkout publisher `pull-requests: write`.
- Give exactly one non-checkout authorizer `actions: write`.
- Bind every workflow-run authorization to both exact SHA and exact PR number.
- Keep all `.github/**` and `CODEOWNERS` changes outside automatic publication and authorization.
- Preserve immutable OpenCode installation, branch protection, 100% configured production statement/branch coverage, public docstrings, APA 7th doctoring, and `CHANGELOG.md` maintenance.

---

## Task 1 — Test the authority boundaries before production changes

**File:** `etl-service/src/test/java/com/xtrmetl/etl/documentation/HourlyOpenCodeMaintenanceWorkflowTest.java`

- [x] Require hourly serialized execution and bounded TERM/KILL handling.
- [x] Require the immutable OpenCode 1.18.13 archive, SHA-256, regular-file member, private extraction, and exact version.
- [x] Require `NVIDIA_NIM_API_KEY` as the sole model secret and `nvidia/deepseek-ai/deepseek-v4-pro` as the selected model.
- [x] Require plain `opencode run --model "${MODEL}" --auto` and reject `opencode github run`.
- [x] Require `pull-requests: read` on `maintain-repository`.
- [x] Require one non-checkout publisher with the workflow's sole `pull-requests: write` grant.
- [x] Require one non-checkout authorizer with the workflow's sole `actions: write` grant.
- [x] Require one strict existing-PR or `automation/opencode-*` candidate.
- [x] Require draft publication, policy-path exclusion, and a branch ahead of `develop`.
- [x] Require workflow-run association with both PR number and exact SHA.
- [x] Require complete CI, Dependency Review, SBOM, Semgrep, and Security Scan materialization.

The test-only head intentionally made the preceding workflow fail before implementation.

## Task 2 — Implement the model job without PR write authority

**File:** `.github/workflows/hourly-opencode-maintenance.yml`

- [x] Run at `43 * * * *` only from protected default-branch source.
- [x] Keep top-level `contents: read`.
- [x] Set model-job permissions to Actions/checks/PR/security/status read, issues write, and contents write.
- [x] Install OpenCode from the pinned immutable archive.
- [x] Configure the removable repository-local `gh auth git-credential` helper and bot author.
- [x] Pipe the bounded prompt into plain `opencode run`.
- [x] Permit an existing eligible branch update or exactly one strict automation branch.
- [x] Preserve the model step's failure after capturing any reviewable branch evidence.

## Task 3 — Detect exactly one candidate

**File:** `.github/workflows/hourly-opencode-maintenance.yml`

- [x] Snapshot `develop`, current direct PR heads, and prior automation branch heads before model execution.
- [x] Require `develop` to remain unchanged afterward.
- [x] Detect one changed existing PR head or one strict automation branch.
- [x] Exclude an automation branch already represented by the changed PR.
- [x] Fail on multiple candidates.
- [x] Emit compact candidate JSON as a job output.

## Task 4 — Publish deterministically without executing source

**File:** `.github/workflows/hourly-opencode-maintenance.yml`

- [x] Add `publish-agent-pull-request` with `contents: read` and `pull-requests: write` only.
- [x] Do not checkout source or pass the NVIDIA credential.
- [x] Re-read existing PR repository, state, base, branch, and exact SHA.
- [x] For a new branch, require the strict namespace, live exact SHA, positive `ahead_by`, at most 50 files, and no policy path.
- [x] Create one draft PR from a fixed JSON payload.
- [x] Expose PR number, head ref, and exact SHA for the authorizer.
- [x] Contain no review or merge endpoint.

## Task 5 — Authorize only PR-associated exact-head runs

**File:** `.github/workflows/hourly-opencode-maintenance.yml`

- [x] Add `authorize-exact-head-checks` with `actions: write`, `contents: read`, and `pull-requests: read`.
- [x] Do not checkout source or pass the model credential.
- [x] Reject `.github/**` and `CODEOWNERS` changes.
- [x] Re-read the live PR before discovery and on every bounded pass.
- [x] Filter each run by `event=pull_request`, exact SHA, and `pull_requests[].number`.
- [x] Authorize only `action_required` or `waiting` runs.
- [x] Require all five named workflows to materialize.
- [x] Fail with missing names or any head movement.

## Task 6 — Align operations, design, doctoring, and changelog evidence

**Files:**

- `docs/operations/hourly-opencode-maintenance.md`
- `docs/doctoring/github-token-exact-head-check-authorization-evidence.md`
- `docs/superpowers/specs/2026-08-04-hourly-opencode-maintenance-design.md`
- `docs/superpowers/plans/2026-08-04-hourly-opencode-maintenance-plan.md`
- `CHANGELOG.md`

- [x] Document the three-job authority topology.
- [x] Record why plain OpenCode run replaces the GitHub lifecycle handler.
- [x] Record strict candidate and draft publication controls.
- [x] Record exact PR-number plus SHA association.
- [x] Record residual coarse permissions and compensating controls.
- [x] Preserve APA 7th references to OpenCode and GitHub primary sources.
- [ ] Confirm the final root changelog wording against the final exact head before merge.

## Task 7 — Final verification and integration

- [ ] Run focused workflow contract tests on the exact final head.
- [ ] Run the complete Maven reactor with no skipped project test.
- [ ] Confirm Ubuntu, macOS, and Windows CI.
- [ ] Confirm Dependency Review and CycloneDX SBOM.
- [ ] Confirm Semgrep, Trivy, OSV, Scorecard, and all required security evidence.
- [ ] Confirm zero unresolved current review thread.
- [ ] Obtain non-author approval anchored to the exact final SHA.
- [ ] Remove `manual-merge` only immediately before an expected-head squash merge.
- [ ] Merge #121, then retarget and revalidate #122 and every successor in stack order.

## References — APA 7th

Anomaly. (2026). *GitHub handler (Version 1.18.13)* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/src/cli/cmd/github.handler.ts

Anomaly. (2026). *Run command (Version 1.18.13)* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/src/cli/cmd/run.ts

GitHub, Inc. (2026). *GITHUB_TOKEN*. GitHub Docs. https://docs.github.com/en/actions/concepts/security/github_token

GitHub, Inc. (2026). *REST API endpoints for workflow runs*. GitHub Docs. https://docs.github.com/en/rest/actions/workflow-runs
