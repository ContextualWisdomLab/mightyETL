# Docker base-image digest pinning

Status: active PR; this document does not describe protected `develop` truth until the owning change is integrated.

## Purpose

mightyETL keeps an explicit Maven or Eclipse Temurin tag in each external production `Dockerfile` `FROM` instruction, but a **mutable tag** is an update channel rather than an immutable build identity. An upstream registry can move a tag to different bytes without any mightyETL commit. For audited builds, the readable tag therefore remains for semantic review while a SHA-256 digest binds the selected image content.

The production rule is:

```text
image:explicit-tag@sha256:64-lowercase-hex-characters
```

`latest` and tag-only external base images are rejected by a repository test.

## Image identity boundary

Docker Official Images publish multi-architecture tags. mightyETL binds the declared tag to its **multi-platform index digest**, not to one platform-specific layer or manifest digest. The index is the reviewed registry object from which Docker selects the matching platform manifest. Build and release provenance still need to record the actual build platform and final artifact digest; this source control does not make a cross-platform image byte-identical by itself.

Dated implementation evidence for this PR, revalidated before the production edit:

- `maven:3.9.13-eclipse-temurin-25` — `sha256:ade3c87e3cdfbe04932afa16b31814cbf60b0122d21d78a76530684a1eeb7cc2`;
- `eclipse-temurin:25-jre` — `sha256:681c543d6f36c50f45e9b5226930a46203dcfa351d3670e9d0bdf0dabae53539`.

These values are dated external-input evidence, not timeless architecture. A later reviewed image update is expected to change them.

## Reviewed update procedure

When a base image must change:

1. Resolve the exact intended tag from the official registry again; never copy a stale digest from an old PR, issue, cache, or log.
2. Verify that the digest is the current multi-platform index digest for that exact tag.
3. Review the semantic version change, upstream security posture, compatibility, supported platforms, and relevant licensing/NOTICE implications.
4. Update the human-readable tag and digest together when the intended version changes. If a mutable tag has moved without a desired semantic-version change, the digest change is still an explicit reviewed supply-chain change rather than an invisible refresh.
5. Run the Dockerfile policy test plus the full applicable CI, dependency, SBOM, SAST, security, packaging, and future release-provenance gates on the resulting exact source.
6. Preserve the resulting source SHA, image identity, build platform, final artifact digest, SBOM, and attestation together when #165 implements release provenance.

Digest pinning intentionally stops an upstream tag movement from silently applying a security fix. Base-image vulnerability remediation therefore requires a reviewed digest/tag update; digest pinning is not a substitute for dependency or image vulnerability monitoring.

## Failure and rollback

If a pinned image fails compatibility or operational acceptance, **rollback** means selecting a previously known image identity only after checking that the older digest does not knowingly reintroduce a remediated vulnerability or unsupported runtime. When rollback would restore a known security defect, use a forward update to another supported digest instead. Never remove the digest merely to regain automatic tag movement.

A registry lookup failure during an update is fail-closed: retain the currently reviewed digest and defer the update rather than guessing an image identity. A digest mismatch between documentation, Dockerfile, registry evidence, or produced provenance is an RCA trigger.

## Security and acquisition limits

This control narrows one supply-chain input. It does **not** by itself prove:

- byte-for-byte reproducibility of the final JAR or container image;
- the final image or JAR digest, SBOM, or provenance attestation;
- completeness of Maven vulnerability resolution;
- licensing or NOTICE rights;
- literal-source GitHub Actions execution;
- non-vacuous repository-wide production coverage;
- runtime deployment acceptance or release readiness.

Issue #165 owns the broader reproducible release/provenance boundary. Build-context secret exclusion is separately owned by #213/#214, and legacy runtime bootstrap/artifact cleanup is separately owned by #168/#169. These controls complement one another and must not be collapsed into one green check.

## References — APA 7

Docker. (2026). *Building best practices*. https://docs.docker.com/build/building/best-practices/

Docker. (2026). *Dockerfile reference*. https://docs.docker.com/reference/dockerfile/

Docker. (2026). *Image digests*. https://docs.docker.com/dhi/core-concepts/digests/
