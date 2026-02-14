# Contributing to xtrmETL

Thanks for contributing to xtrmETL. This guide describes the expected
local setup, branching, verification, and pull request flow.

## Development setup

Prerequisites:

- Java 25
- Git
- Docker (optional, for local integration stack)

Initial setup:

```bash
./mvnw -B -DskipTests dependency:go-offline
./mvnw -B test
```

Optional local stack:

```bash
docker compose up --build
```

## Branching and pull request flow

1. Create a branch from `main`.
2. Use a descriptive branch name such as `feat/<topic>`, `fix/<topic>`, or `chore/<topic>`.
3. Keep each pull request focused on one change set.
4. Rebase or merge `main` before requesting final review.

## Local checks before opening a PR

Run the checks relevant to your change:

- Full tests: `./mvnw -B test`
- CI parity script (Unix): `./scripts/ci.sh`
- CI parity script (Windows): `./scripts/ci.ps1`
- Workflow YAML sanity (example):

```bash
ruby -e 'require "yaml"; Dir[".github/workflows/*.yml"].each do |f| \
  YAML.safe_load(File.read(f), permitted_classes: [], aliases: false); \
end; puts "workflow YAML OK"'
```

## PR expectations

Each PR should include:

- A clear problem statement and scope
- Test evidence (command output or screenshots when applicable)
- Risk notes (runtime impact, migration concerns, rollback notes)
- Security impact notes for auth, data handling, dependency, or workflow changes

Use `.github/PULL_REQUEST_TEMPLATE.md` when opening your PR.

## Security and responsible changes

- Do not commit secrets, credentials, `.env` files, or private keys.
- Treat automated review comments as hypotheses and verify with evidence.
- For vulnerability reports, follow `SECURITY.md` and avoid public
  disclosure before triage.
