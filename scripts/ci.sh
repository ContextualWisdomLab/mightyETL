#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

if ! command -v mvn >/dev/null 2>&1; then
  for candidate in /opt/homebrew/bin /usr/local/bin; do
    if [[ -x "$candidate/mvn" ]]; then
      export PATH="$candidate:$PATH"
      break
    fi
  done
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: mvn (Maven) not found. Install Maven 3.6+ and retry." >&2
  exit 1
fi

echo "==> Running unit tests"
mvn -B test

echo "==> Generating SBOM (CycloneDX aggregate)"
mvn -B -DskipTests org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom \
  -DoutputFormat=all \
  -Dcyclonedx.skipAttach=true

echo "==> Done"
echo "SBOM outputs:"
echo "  - target/bom.json"
echo "  - target/bom.xml"
