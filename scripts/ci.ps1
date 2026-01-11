$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$rootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Set-Location $rootDir

function Invoke-Maven {
  param(
    [Parameter(Mandatory = $true)]
    [string[]]$Arguments
  )

  if (Test-Path ".\\mvnw.cmd") {
    & .\\mvnw.cmd @Arguments
    return
  }

  if (Test-Path "./mvnw") {
    & ./mvnw @Arguments
    return
  }

  if (Get-Command mvn -ErrorAction SilentlyContinue) {
    & mvn @Arguments
    return
  }

  throw "Maven not found. Use ./mvnw (recommended) or install Maven."
}

Write-Host "==> Running unit tests"
Invoke-Maven -Arguments @("-B", "test")

Write-Host "==> Generating SBOM (CycloneDX aggregate)"
Invoke-Maven -Arguments @(
  "-B",
  "-DskipTests",
  "org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom",
  "-DoutputFormat=all",
  "-Dcyclonedx.skipAttach=true"
)

Write-Host "==> Done"
Write-Host "SBOM outputs:"
Write-Host "  - target/bom.json"
Write-Host "  - target/bom.xml"
