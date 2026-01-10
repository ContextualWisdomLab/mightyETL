# SBOM (CycloneDX)

This repository can generate a CycloneDX SBOM for the full Maven multi-module build.

## Generate locally

```bash
mvn -B -DskipTests org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom \
  -DoutputFormat=all \
  -Dcyclonedx.skipAttach=true
```

Outputs:
- `target/bom.json`
- `target/bom.xml`

## CI

GitHub Actions workflow: `.github/workflows/sbom.yml`
