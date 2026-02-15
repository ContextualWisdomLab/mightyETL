# Security Policy

## Supported versions

Security fixes are currently developed and released from the `main` branch.

| Version | Supported |
| --- | --- |
| `main` | Yes |
| Other branches | No |

## Reporting a vulnerability

Please do not open public GitHub issues for suspected vulnerabilities.

Use one of the following private channels:

1. GitHub private vulnerability reporting in the repository Security tab.
2. If private reporting is unavailable, contact repository maintainers
   directly and share minimal reproduction details privately.

Include the following when reporting:

- Affected component and version/commit
- Reproduction steps or proof-of-concept
- Impact assessment (confidentiality, integrity, availability)
- Any known mitigations or workarounds

## Triage and response expectations

- Acknowledgement target: within 2 business days
- Initial severity/impact triage: within 5 business days
- Status updates: at least weekly until resolution or accepted risk decision

Response targets are best-effort and may vary based on report quality
and maintainer availability.

## Disclosure expectations

- Coordinate disclosure with maintainers before publishing details.
- Allow reasonable remediation time prior to public disclosure.
- Avoid publishing exploit details while users remain unpatched.

## Dependency and security scanning references

- Dependency update automation is managed with Dependabot only.
- Dependabot configuration: `.github/dependabot.yml`
- CI checks: `.github/workflows/ci.yml`
- Dependency review on PRs: `.github/workflows/dependency-review.yml`
- Code scanning (CodeQL): `.github/workflows/codeql.yml`
- SBOM generation (CycloneDX): `.github/workflows/sbom.yml`
- OpenSSF Scorecard checks: `.github/workflows/scorecard.yml`

When CI runners are unavailable, run local parity checks:

- macOS/Linux: `./scripts/ci.sh`
- Windows (PowerShell): `./scripts/ci.ps1`
