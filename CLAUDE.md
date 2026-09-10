# CLAUDE

Contributor and agent guidance for this repository.

- Source of truth: follow `AGENTS.md` for all workflow and safety rules.
- Keep changes minimal, production-safe, and scoped to the requested files.
- Verify claims with command evidence before changing behavior.
- Never commit secrets, credentials, `.env` files, or private keys.
- Do not commit or push unless a human explicitly asks.
- For workflow edits, run local YAML parsing and `actionlint` on edited files.
- Config Server default Git profile requires `CONFIG_REPO_URI` and must fail closed on blank authority. See `docs/doctoring/config-server-repository-authority.md`.

If any guidance here conflicts with `AGENTS.md`, `AGENTS.md` wins.
