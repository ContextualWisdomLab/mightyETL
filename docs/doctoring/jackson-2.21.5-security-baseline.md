# Jackson 2.21.5 Security Baseline Evidence

**Status:** `active_pr` #160  
**Protected baseline assessed:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Assessment date:** 2026-08-09

## Trigger

PR #158 Security Scan run `31312834418`, Trivy filesystem job `93243221587`, completed the Trivy scan and then failed the repository's findings gate on three inherited `com.fasterxml.jackson.core:jackson-databind` findings:

- `CVE-2026-54515`;
- `CVE-2026-59889`;
- `GHSA-mhm7-754m-9p8w`.

The scanner checked out synthetic merge `6f0669cc1c3b36f14296ab839154991c298a7eec`, which merged #158 head `3b501f823fa0d3d93a5a95c714efdd79242a8a58` into protected `develop@622e5e6c3d534f230c390f10e3832efadfc01825`. #158 itself changes MySQL CDC source discovery and does not modify Jackson or Maven dependency management. Re-running #158 against the unchanged base therefore cannot change this failure.

Synthetic-merge scanning is useful evidence that the combined tree contains the vulnerable dependency, but it does not satisfy mightyETL's separate literal-source security-evidence requirement.

## Dependency root cause

Protected root `pom.xml` imports `org.springframework.boot:spring-boot-dependencies:3.5.16` and does not inherit `spring-boot-starter-parent`. Spring Boot 3.5.16 manages the Jackson 2.21 line at 2.21.4. The project has no explicit Jackson override on protected develop, so the vulnerable Databind patch is inherited through dependency management rather than declared by #158.

FasterXML designates Jackson 2.21 as an LTS line and released 2.21.5 on 2026-07-06. Upstream advisory/release material identifies 2.21.5 as the patched 2.21.x line for the Databind issues that triggered this scan.

## Remediation options

### Execute now — import Jackson BOM 2.21.5 before Spring Boot dependency management

Selected. mightyETL uses the Spring Boot BOM without the Boot parent POM. Spring Boot's Maven documentation explicitly states that property-only version overrides do not apply in this mode; an overriding dependency or BOM must appear before the `spring-boot-dependencies` import.

The root POM therefore imports:

```xml
<dependency>
    <groupId>com.fasterxml.jackson</groupId>
    <artifactId>jackson-bom</artifactId>
    <version>${jackson-bom.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

with `jackson-bom.version=2.21.5` before the Spring Boot BOM. This keeps Jackson modules on one upstream 2.21 security patch line rather than pinning only `jackson-databind` and risking patch-line skew.

### Reject — set only `<jackson-bom.version>` in project properties

Rejected for this repository topology. That is the convenient override path when the project inherits Spring Boot's parent; Spring Boot documents that projects importing the BOM without the parent need dependency-management entries before the Boot import.

### Reject — suppress or ignore the Trivy findings

Rejected. The fixed LTS patch is available and the findings arise from an actual inherited package. Suppression would leave the root cause unchanged and weaken a security gate.

### Reject — patch only PR #158

Rejected. The dependency belongs to shared `develop`; a PR-local patch would duplicate the override across branches and leave the protected base and other open PRs vulnerable.

### Defer — wait for a later Spring Boot release

Not selected. Spring Boot 3.5.16 is the current project baseline and Jackson 2.21.5 is already an upstream patch in the same minor/LTS line. Waiting preserves a known shared security failure without a compatibility benefit proven by evidence.

## TDD and acceptance

Fail-first commit `b60d7a6ef6b8a7fd8faa4cf3598bc25768a1847b` adds `JacksonSecurityBaselineTest` before dependency management changes. The test binds the root Maven security contract to:

- exact `jackson-bom.version` 2.21.5;
- explicit Jackson BOM import;
- Jackson override ordering before `spring-boot-dependencies`.

The protected POM fails those assertions by inspection because no Jackson BOM/property exists there. The GREEN candidate adds only the supported BOM override before documentation/evidence updates.

Final acceptance requires fresh exact-head/base evidence rather than source inspection alone:

- full Maven reactor compatibility;
- Dependency Review;
- generated SBOM resolving Jackson Databind outside the reported vulnerable range;
- Trivy no longer reporting the three inherited Databind findings;
- SAST/security gates;
- current review and qualifying independent approval where required;
- literal-source security evidence once the repository/central control plane provides it.

A synthetic merge becoming clean after this patch proves the merged dependency graph changed, but it must not be misreported as literal-source scanner proof.

## Compatibility and rollback

The remediation stays inside Jackson 2.21 LTS and changes only the patch baseline. Spring Boot cautions that overriding curated dependencies can cause compatibility problems, so the full multi-module reactor remains mandatory acceptance evidence.

Rollback is the single Jackson BOM override removal, but rollback knowingly restores the inherited vulnerable Databind baseline while Spring Boot 3.5.16 remains unchanged. Therefore rollback is not a security-safe steady state; if compatibility fails, the next remediation must evaluate a supported alternate Jackson/Spring Boot patch path rather than silently accepting the advisories.

## References — APA 7th

FasterXML. (2026, July 7). *Jackson release 2.21*. GitHub. https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21

FasterXML. (2026). *Case-insensitive deserialization bypasses per-property @JsonIgnoreProperties in jackson-databind* (GHSA-5jmj-h7xm-6q6v; CVE-2026-54515). GitHub Security Advisory. https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-5jmj-h7xm-6q6v

FasterXML. (2026, July 8). *Jackson release 2.22.1*. GitHub. https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.22.1

Spring Boot. (2026). *Using the plugin: Using Spring Boot without the parent POM*. Spring Documentation. https://docs.spring.io/spring-boot/maven-plugin/using.html

Spring Boot. (2026). *Version properties: Jackson BOM*. Spring Documentation. https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/properties.html
