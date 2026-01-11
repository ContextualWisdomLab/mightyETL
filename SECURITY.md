# Security

## Dependency Updates

- Dependabot: `.github/dependabot.yml`
- Renovate: `renovate.json`

## Local Checks

When CI runners are unavailable, run the same checks locally:

- macOS/Linux: `./scripts/ci.sh`
- Windows (PowerShell): `./scripts/ci.ps1`

## SBOM

- SBOM workflow definition: `.github/workflows/sbom.yml`
- Local generation: `./mvnw -B -DskipTests org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DoutputFormat=all -Dcyclonedx.skipAttach=true`
