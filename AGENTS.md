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

## Scheduled maintenance progress contract

External review, approval, check, or read-only dependency latency is not a reason to stop all
productive mightyETL work. Those unavailable gates remain not passing and must never be reused as
merge or release evidence.

A pull request is source-actionable only when its exact current head has a valid repository-local
finding or failing source gate that mightyETL can repair. Queued checks, missing independent
approval, synthetic-merge-only scanner evidence, and a separately leased dependency that has not
yet integrated are external-only blockers rather than source-actionable findings.

When no open pull request is source-actionable, select exactly one non-conflicting bounded
mightyETL slice from the protected `develop` head. Prefer documentation, tests, security,
reliability, packaging, release evidence, or a buyer-visible vertical slice that can be developed
and reviewed independently of the blocked stack. Keep the one-candidate-per-run publication
boundary and all exact-head validation requirements.

Do not deepen an invalid stack or modify a blocked stack branch merely to appear productive. The
independent slice must not depend on, retarget, rewrite, or overlap files changed by the invalid
stack. If no such independent slice exists, perform read-only analysis and return without a
candidate rather than weakening ancestry, tests, or branch protection.

ContextualWisdomLab/.github, naruon, contextual-orchestrator, and every separately leased repository
remain read-only. Inspect their exact integration state, but never mutate, dispatch a write-capable
agent, or post a mutation-trigger comment there. Their dedicated loops own those writes.

## Code-owner review gates — disabled (on hold)

As of 2026-08-04, code-owner review requirements (`require_code_owner_reviews` in branch
protection, `require_code_owner_review` in rulesets) are disabled across the ContextualWisdomLab
org: there is a single maintainer (solo developer), so a code-owner approval gate can never be
satisfied. This is ON HOLD until the org has multiple maintainers — do NOT re-enable these
settings or add CODEOWNERS-based merge gates before then.
