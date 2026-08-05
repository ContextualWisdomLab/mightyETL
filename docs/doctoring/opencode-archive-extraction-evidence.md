# OpenCode archive extraction evidence

## Scope

This evidence note covers only the installation boundary in `.github/workflows/hourly-opencode-maintenance.yml`. It does not authorize pull-request approval, merge, protected-branch writes, release publication, or changes to the independent review agent.

The scheduled workflow downloads the immutable OpenCode `v1.18.13` Linux x64 release archive. GitHub marks that release immutable, and the workflow pins the Linux x64 asset's SHA-256. Checksum verification binds downloaded bytes to the reviewed asset, but it does not independently constrain the filesystem shape or entry type that an archive extractor would process. This note records the additional fail-closed archive-member, pre-extraction entry-type, extraction-directory, and post-extraction file controls.

## Threat statement

Archive extraction is a filesystem write operation. Unexpected archive members can broaden that write beyond the intended executable. Symbolic links, hard links, directories, device nodes, and other special entries can change the meaning or destination of extracted paths. GNU tar's security guidance therefore treats archive member names, extraction location, ownership, permissions, links, and overwrite behavior as security-relevant controls.

For this immutable OpenCode release, upstream publishing source and release packaging identify a single root executable named `opencode`. mightyETL intentionally does not generalize that shape into a reusable archive installer. Any changed member count, name, or entry type is a supply-chain review event and fails the job.

## Evidence chain

1. GitHub marks OpenCode release `v1.18.13` immutable and identifies asset `501285078` as `opencode-linux-x64.tar.gz`.
2. OpenCode's upstream release process builds the Linux x64 archive and computes release digests.
3. The mightyETL workflow pins release `v1.18.13` and SHA-256 `8d500b20fed2d26e537e221895b1a575476571b4f0089bb29fb13eeb8eb9e937`.
4. The workflow validates the SHA-256 before listing or extracting the archive.
5. The workflow lists the verified archive and requires exactly one member named `opencode`.
6. With `LC_ALL=C`, the workflow obtains GNU tar's verbose metadata for that sole member and requires the first type character to be `-`, denoting a regular-file archive entry. Link, directory, and special-file entries are rejected before extraction.
7. The workflow recreates a private mode-`0700` installation directory and enables overwrite refusal.
8. Extraction does not restore archived ownership or permissions.
9. The extracted `opencode` path must be a regular file and must not be a symbolic link before executable mode is applied.
10. The executable must report exactly version `1.18.13` before its directory is added to `GITHUB_PATH`.

These controls are deliberately cumulative. A checksum mismatch, unexpected member name or count, non-regular archive entry, extraction failure, missing file, non-regular extracted file, symbolic link, or version mismatch terminates installation before OpenCode runs.

## Test-first evidence

`HourlyOpenCodeArchiveValidationTest` was first added before the workflow member check. On the pull-request CI merge ref for test-only commit `751eedb852eca1165a5b936296255fc608494dad`, the Maven reactor reported 284 tests, one failure, and zero errors. The sole failure was `validatesOneExpectedArchiveMemberBeforeExtraction`, demonstrating that the prior checksum-only workflow did not satisfy the member-shape contract.

After exact member validation was added, the test was extended before production code to require a regular-file archive entry. On the pull-request CI merge ref for test-only commit `7b82a40b12c46aed869aeec7b387a161a7b33896`, GitHub Actions run `30964191079` reported 285 tests, one failure, zero errors, and zero skipped project tests on Ubuntu. The sole failure was `validatesRegularFileEntryTypeBeforeExtraction`, demonstrating that name and count validation alone did not reject hard-link or other non-regular archive entries before extraction.

The production workflow then added locale-stable verbose metadata inspection and a regular-entry type check before its existing extraction command. Exact member validation, a fresh private directory, overwrite refusal, disabled ownership and archived-permission restoration, and post-extraction regular non-symbolic-link checks remain defense in depth. The tests are deterministic cross-platform repository contracts; the actual scheduled installer executes only on the declared Ubuntu runner.

This document is design and verification evidence, not a substitute for exact-head CI, security checks, independent review, or branch protection. Any later commit makes earlier exact-head evidence stale.

## Operational response

Treat any archive checksum, member-set, archive-entry type, extracted-file type, or version mismatch as a supply-chain incident. Do not broaden the member allowlist, permit link or special-file entries, remove post-extraction file checks, disable overwrite refusal, or change the checksum merely to restore a green workflow. Compare the exact immutable upstream release record, release-asset metadata, and upstream publishing source before proposing a reviewed pin change.

Rollback consists of disabling the scheduled workflow or reverting the workflow, tests, operations documentation, design, implementation plan, doctoring evidence, and CHANGELOG entry through an independently reviewed pull request. Rollback must not rename or remove `NVIDIA_NIM_API_KEY` when another approved workflow also uses it, and must not alter the review-agent credential scheme.

## References

Anomaly. (2026). *OpenCode release publishing script* [Source code]. GitHub. https://github.com/anomalyco/opencode/blob/v1.18.13/packages/opencode/script/publish.ts

Anomaly. (2026). *OpenCode release v1.18.13* [Software release]. GitHub. https://github.com/anomalyco/opencode/releases/tag/v1.18.13

Free Software Foundation. (2023). *GNU tar 1.35: Security*. https://www.gnu.org/software/tar/manual/html_section/Security.html

GitHub, Inc. (2026). *OpenCode v1.18.13 Linux x64 release asset metadata* [JSON metadata]. GitHub REST API. https://api.github.com/repos/anomalyco/opencode/releases/assets/501285078

GitHub, Inc. (2026). *Security hardening for GitHub Actions*. GitHub Docs. https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions
