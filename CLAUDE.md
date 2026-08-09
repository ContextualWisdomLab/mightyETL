# CLAUDE

Contributor/agent context for mightyETL.

`AGENTS.md` is the governing repository policy and wins on conflict. In particular:

- explicitly authorized autonomous mightyETL maintenance may commit/publish candidate branches without a fresh human comment for every mutation;
- the writer lease applies only to mightyETL and is branch-local on conflicts;
- separate CWL repository loops are read-only dependencies;
- every failure/blocker requires RCA, materially distinct remediation options, real-world feasibility proof, safe execution where possible, and exact post-action verification;
- queued checks/reviews/external blockers do not justify stopping unrelated safe work;
- branch publication that requires an exact parent uses branch-wide CAS and non-forced ref movement rather than file-level assumptions;
- never bypass protection, synthesize approval, force-push, weaken tests/security, or reuse stale evidence;
- source behavior uses red-green-refactor TDD, 100% configured owned-production statement/branch coverage, and complete public production documentation;
- canonical PRD/TRD/Architecture/ADR/UML/ERD/API/Security/Threat/Test/Operability/Traceability docs must track implementation status;
- database objects use descriptive multi-word snake_case by default; legacy violations require safe migration/rollback evidence;
- PII needed for legitimate operation is controlled through authorization, least privilege, encryption, retention/minimization, and audit rather than blanket masking;
- autonomous GitHub Actions development uses pinned OpenCode + `NVIDIA_NIM_API_KEY`, never GitHub Copilot or `COPILOT_GITHUB_TOKEN`;
- release only from an exact integrated protected head with complete CI/security/coverage/migration/compatibility/SBOM/provenance/review/operational acceptance.

Before acting on repository state, refetch exact current evidence. Before finishing an invocation, perform the mandatory second live sweep defined in `AGENTS.md`.
