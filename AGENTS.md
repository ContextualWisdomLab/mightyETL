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
- Config Server is independently runnable and is not a default production dependency. The default Git profile must fail closed without an operator-supplied `CONFIG_REPO_URI` before JGit runs; `native` plus `prod`/`production` requires `xtrmetl.config.allow-native=true`. See `docs/doctoring/config-server-repository-authority.md`.
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
