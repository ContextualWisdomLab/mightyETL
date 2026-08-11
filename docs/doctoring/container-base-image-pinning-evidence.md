# Container base-image pinning evidence

## Purpose

mightyETL container builds must not silently consume different registry bytes when a mutable image tag moves. Docker supports `FROM image:tag@sha256:<digest>` so a human-readable release tag can remain visible while the build resolves to one immutable content digest. This control reduces software-supply-chain drift and makes base-image changes reviewable as repository changes.

This evidence is a build-input integrity control. It does **not** claim that a digest makes an image trustworthy, vulnerability-free, or permanently suitable. Vulnerability management and planned digest rotation remain separate duties.

## Governing contract

Every registry-backed `FROM` instruction in the repository `Dockerfile` must include a lowercase SHA-256 digest. References to an earlier local build-stage alias are exempt because they do not resolve through an external registry.

The current reviewed references are:

- build stage: `maven:3.9.13-eclipse-temurin-25@sha256:ade3c87ed2874c8b773ccb9b238cd66db8a7c56c77d99a1c825bf929f3afcb96`
- runtime stage: `eclipse-temurin:25-jre@sha256:f19dbf0dc677ae28efed04b8b99d3123d6aaf2e6b3c9d35c09274dd8b5d53a4f`

The digest values were surfaced by the OpenSSF Scorecard remediation output associated with mightyETL's Security Scan. That historical scan executed against GitHub's synthetic pull-request merge revision, so it is used here only as remediation provenance for the digest-resolved references and is **not** accepted as literal-head security-gate evidence.

## Red-green TDD evidence

### RED

Commit `b6efb1fc05c0161e82278c675ae7a631878b9302` added `ContainerImagePinningTest` without changing the mutable Dockerfile tags. Exact-head CI run `31238858595` checked out that literal SHA. The macOS job `93056263926` ran the Maven test suite and failed specifically with:

`Dockerfile base image must use an immutable SHA-256 digest: maven:3.9.13-eclipse-temurin-25`

The failure is intentionally retained in history as evidence that the contract detects the pre-existing mutable input.

### GREEN implementation

Commit `f8cdcf77b2a940a474d1b8080e3e9b6bfeacca4b` changed only the two external Dockerfile base references to the reviewed digest-qualified form. Exact-head CI run `31238960659` checked out that literal SHA; its macOS Maven test step completed successfully, including `ContainerImagePinningTest`. Full integrated exact-head gate acceptance is evaluated separately after all documentation commits so older-head evidence cannot be reused.

## Enforcement design

`ContainerImagePinningTest` parses every `FROM` instruction, tracks local stage aliases, and fails when any registry-backed image reference lacks exactly one `@sha256:` value followed by 64 lowercase hexadecimal characters. It also fails if the Dockerfile unexpectedly contains no external base image. The test is intentionally repository-level because the risk is configuration drift rather than Java runtime behavior.

Keeping the tag alongside the digest is deliberate. Docker documents that the digest provides an immutable identifier while the tag preserves release intent and readability. A tag update, digest update, or both therefore becomes an explicit reviewed diff rather than an implicit registry-side change.

## Update procedure

When a Maven or Eclipse Temurin base image must be updated:

1. Resolve the intended official image tag to its current registry digest using a trusted registry-aware Docker/BuildKit inspection path.
2. Review the upstream image release and security rationale; do not copy an untrusted third-party digest.
3. Update the readable tag and SHA-256 digest together when the release changes, or update only the digest when intentionally adopting a rebuilt image under the same reviewed tag.
4. Run the full test suite and container build verification on the exact candidate head.
5. Require exact-head Dependency Review, SBOM, SAST, security scanning, provenance/release checks, and independent review according to repository policy before integration.

## Rollback

If a newly pinned base image causes a regression, revert to the previously reviewed **digest-qualified** reference and rerun exact-head validation. Never remove the digest merely to make a build move again, because that would restore unreviewed registry mutability.

## Standards and primary references

Docker, Inc. (2026). *Dockerfile reference*. Docker Docs. https://docs.docker.com/reference/dockerfile/

Docker, Inc. (2026). *Building best practices*. Docker Docs. https://docs.docker.com/build/building/best-practices/

Docker, Inc. (2026). *docker image pull*. Docker Docs. https://docs.docker.com/reference/cli/docker/image/pull/

National Institute of Standards and Technology. (2022). *Secure Software Development Framework (SSDF) version 1.1: Recommendations for mitigating the risk of software vulnerabilities (NIST SP 800-218).* https://doi.org/10.6028/NIST.SP.800-218
