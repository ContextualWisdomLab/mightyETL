# AGENTS

This repository allows automated agents to help with documentation,
workflows, and service-level maintenance.

## Scope and defaults

- Treat AI review comments as hypotheses; verify claims with code or
  command evidence before changing behavior.
- Keep diffs minimal and production-ready; avoid broad refactors unless
  the task explicitly requires them.
- Preserve existing module names, service boundaries, and file layout
  unless a change is required for correctness.
- Never commit secrets, credentials, `.env` files, or generated private keys.
- Do not commit or push unless a human explicitly asks for it.

## Repository map

- Root Maven aggregator: `pom.xml`
- Services: `etl-service/`, `cdc-service/`, `zuul-gateway/`, `eureka-server/`, `config-server/`
- Shared code: `META-INF/`, common build config in root `pom.xml`
- Operations/docs: `docker/`, `docs/`, `.github/`, `scripts/`

## Safe change workflow

1. Read related docs and existing config before editing.
2. Make the smallest viable set of file changes.
3. Run relevant checks locally when possible.
4. Report what changed, what was verified, and what could not be verified.

## Expected verification

- Java/Maven changes: `./mvnw -B test`
- Workflow changes: parse all edited `.yml` files locally (for example
  with Ruby `YAML.safe_load_file`).
- Documentation-only changes: validate links/paths touched in edited docs.

## Change boundaries

- Prefer updates to existing workflows/docs over adding new systems.
- Keep automation explicit and auditable (clear triggers, least-privilege permissions).
- When unsure, prefer conservative defaults that reduce security and release risk.

## Code-owner review gates — disabled (on hold)

As of 2026-08-04, code-owner review requirements (`require_code_owner_reviews` in branch
protection, `require_code_owner_review` in rulesets) are disabled across the ContextualWisdomLab
org: there is a single maintainer (solo developer), so a code-owner approval gate can never be
satisfied. This is ON HOLD until the org has multiple maintainers — do NOT re-enable these
settings or add CODEOWNERS-based merge gates before then.

## Know-how (summaries; detail in owner runbook)

- Java 25 is required (class file v69). The default `java` on dev machines may be
  Temurin-21, which fails Surefire with `class file version 69.0`. Export
  `JAVA_HOME` to the mise Temurin-25 install before `./mvnw` and re-check
  `java -version`. Detail: `docs/product-technical-gap-baseline.md` §8.
- When `gh run rerun <run-id> --failed` returns
  `404 .../actions/workflows/<id>`, rerun via
  `gh api -X POST repos/{owner}/{repo}/actions/runs/{run_id}/rerun-failed-jobs`.
  Detail: `docs/product-technical-gap-baseline.md` §8.
- `noema-review` HTTP 429, `opencode-review` missing exact-head verdict, CodeQL
  `pending`, and `strix` infra timeouts are orchestration/transient when core CI
  (test ubuntu/macos/windows, Analyze, dependency-review, sbom, Semgrep, Trivy,
  Scorecard) is green. Rerun failed jobs; do not change product code for them.
  Detail: `docs/product-technical-gap-baseline.md` §8.
- Keep fix branches minimal: docs/baseline updates go on separate `docs/*`
  branches from `origin/develop`, never on top of `repair/*` branches, so the
  hourly disposition expected-head SHA stays valid.
