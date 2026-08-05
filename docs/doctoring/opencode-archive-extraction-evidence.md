# OpenCode archive extraction evidence

## Scope

This evidence note covers only the installation boundary in `.github/workflows/hourly-opencode-maintenance.yml`. It does not authorize pull-request approval, merge, protected-branch writes, release publication, or changes to the independent review agent.

The scheduled workflow downloads the immutable OpenCode `v1.18.13` Linux x64 release archive. A pinned SHA-256 verifies that the downloaded bytes match the reviewed upstream artifact, but checksum verification alone does not constrain the filesystem shape that an archive extractor would process. This note records the additional fail-closed archive-member and extracted-file controls.

## Threat statement

Archive extraction is a filesystem write operation. Unexpected archive members can broaden that write beyond the intended executable, and link or special-file members can change the meaning of the extracted path. GNU tar's security guidance therefore treats archive member names, extraction location, ownership, permissions, links, and overwrite behavior as security-relevant controls.

For this immutable OpenCode release, the reviewed upstream packaging contract installs a single root executable named `opencode`. mightyETL intentionally does not generalize that shape into a reusable archive installer. A changed member set is a supply-chain review event and fails the job.

## Evidence chain

1. OpenCode's upstream release process builds `opencode-linux-x64.tar.gz`, computes its SHA-256, and generates package-manager metadata that installs the root `opencode` executable.
2. The mightyETL workflow pins release `v1.18.13` and SHA-256 `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937`.
3. The workflow validates the SHA-256 before listing or extracting the archive.
4. The workflow lists the verified archive and requires exactly one member named `opencode`.
5. The workflow recreates a private mode-`0700` installation directory and enables overwrite refusal.
6. Extraction does not restore archived ownership or permissions.
7. The extracted `opencode` path must be a regular file and must not be a symbolic link before executable mode is applied.
8. The executable must report exactly version `1.18.13` before its directory is added to `GITHUB_PATH`.

These controls are deliberately cumulative. A checksum mismatch, unexpected member name or count, extraction failure, missing file, non-regular file, symbolic link, or version mismatch terminates the installation before OpenCode runs.

## Test-first evidence

`HourlyOpenCodeArchiveValidationTest` was added before the workflow member check. On the pull-request CI merge ref for test-only commit `751eedb852eca1165a5b936296255fc608494dad`, the Maven reactor reported 284 tests, one failure, and zero errors. The sole failure was `validatesOneExpectedArchiveMemberBeforeExtraction`, demonstrating that the prior checksum-only workflow did not satisfy the new extraction contract.

The production workflow then added exact member validation before its existing extraction command, a fresh private directory, overwrite refusal, and regular non-symbolic-link output checks. The test remains a deterministic cross-platform repository contract; the actual scheduled installer executes only on the declared Ubuntu runner.

This document is design and verification evidence, not a substitute for exact-head CI, security checks, independent review, or branch protection. Any later commit makes earlier exact-head evidence stale.

## Operational response

Treat any archive checksum, member-set, extracted-file type, or version mismatch as a supply-chain incident. Do not broaden the member allowlist, remove the symbolic-link check, disable overwrite refusal, or change the checksum merely to restore a green workflow. Compare the exact immutable upstream release, upstream publishing source, and generated checksum metadata before proposing a reviewed pin change.

Rollback consists of disabling the scheduled workflow or reverting the workflow, tests, operations documentation, design, implementation plan, doctoring evidence, and CHANGELOG entry through an independently reviewed pull request. Rollback must not rename or remove `NVIDIA_NIM_API_KEY` when another approved workflow also uses it, and must not alter the review-agent credential scheme.

## References

Anomaly. (2026). *OpenCode release publishing script* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/script/publish.ts

Anomaly. (2026). *OpenCode release v1.18.13* [Software release]. GitHub. https://github.com/anomalyco/opencode/releases/tag/v1.18.13

Anomaly. (2026). *OpenCode Homebrew formula* [Source code]. GitHub. https://github.com/anomalyco/homebrew-tap/blob/master/opencode.rb

Free Software Foundation. (2023). *GNU tar 1.35: Security*. https://www.gnu.org/software/tar/manual/html_section/Security.html

GitHub, Inc. (2026). *Security hardening for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions
