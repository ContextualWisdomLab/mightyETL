# Non-vacuous JaCoCo Coverage Evidence

**Status:** `active_pr` #164  
**Protected baseline assessed:** `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**Assessment date:** 2026-08-09

## Incident evidence

PR #155 CI `31314123991`, macOS job `93246494460`, ran the full Maven reactor and emitted:

```text
--- jacoco:0.8.15:report (report-durable-job-coverage) @ etl-service ---
Analyzed bundle 'etl-service' with 0 classes

--- jacoco:0.8.15:check (check-durable-job-coverage) @ etl-service ---
All coverage checks have been met.
```

The job had already compiled production/test code and ran the real test suites successfully. The coverage defect is therefore not an empty project or missing test execution: the report/check class selection was empty while the configured zero-missed limits still passed.

That result invalidates the previous use of this JaCoCo execution as evidence of 100% durable-job production coverage. It does not invalidate the test results themselves.

## Root cause

Protected `etl-service/pom.xml` configured dotted patterns at plugin scope:

```text
com.xtrmetl.etl.job.*
com.xtrmetl.etl.controller.EtlJobController*
```

JaCoCo `prepare-agent` defines `includes` as class names. `CoverageTransformer` converts those configured names to VM notation before matching loaded classes.

JaCoCo Maven `report` and `check`, however, define their `includes` as **class files**. `ReportSupport` constructs a Maven `FileFilter` and enumerates matching files below the compiled classes directory before analysis. The plugin-level configuration therefore reused one syntax across execution-time class names and report/check class-file paths.

The second root cause is control design: every existing coverage limit constrained `MISSEDCOUNT` to zero, but none required the selected bundle to contain a class. An empty bundle can therefore satisfy the limits vacuously.

## Selected repair

The active repair separates the goals:

1. `prepare-agent` receives no restrictive include filter; JaCoCo documents this filter as unnecessary except for technical/performance corner cases.
2. `report-durable-job-coverage` selects compiled files using:
   - `com/xtrmetl/etl/job/*.class`
   - `com/xtrmetl/etl/controller/EtlJobController*.class`
3. `check-durable-job-coverage` uses the same class-file paths.
4. The check applies limits to the selected `BUNDLE` and first requires `CLASS TOTALCOUNT >= 1`.
5. Exact zero missed INSTRUCTION, LINE, METHOD and BRANCH counters remain unchanged in strictness.

If this exposes real uncovered production code, the correct repair is additional realistic tests or a reviewed product-code removal/refactor—not reintroducing an empty filter or lowering the thresholds.

## TDD

Fail-first commit `ba174ac98128da254358a5f8dccdfcf8eee93496` adds `JaCoCoCoverageConfigurationTest` before changing the POM. The test requires:

- plugin-level includes absent;
- separate report/check class-file patterns;
- a non-empty BUNDLE guard;
- actual compiled `EtlJobService.class` and `EtlJobController.class` at test runtime.

Protected POM fails the configuration contract while the named production target classes exist, so the RED reaches the intended quality-gate boundary rather than a missing fixture.

The first GREEN candidate is `b65c31adcb636640a1a9a44c5b80eec2a487215c`, which changes only JaCoCo goal configuration after the fail-first test.

## Acceptance evidence

The final exact head is not accepted until a fresh hosted run proves all of the following:

- JaCoCo report logs a nonzero analyzed-class count for `etl-service`;
- the generated report includes the intended durable-job classes;
- JaCoCo check satisfies `CLASS TOTALCOUNT >= 1`;
- selected owned production code has zero missed instruction, line, method and branch counters;
- full Maven reactor and supported hosted OS tests succeed;
- any real coverage deficits revealed by the repaired selector are fixed test-first;
- Dependency Review, SBOM, SAST/security and review gates pass;
- current protected synthetic-merge execution is not mislabeled literal-source proof.

## Rollback

Reinstating the old plugin-level dotted include patterns is not an acceptable rollback because it restores a proven vacuous coverage gate. If the new selector exposes too much or the target definition is wrong, revise the explicit class-file target under review while retaining the non-empty class-count invariant.

## References — APA 7th

JaCoCo. (2026). *Java agent*. https://www.jacoco.org/jacoco/trunk/doc/agent.html

JaCoCo. (2026). *jacoco:prepare-agent*. https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html

JaCoCo. (2026). *jacoco:report*. https://www.jacoco.org/jacoco/trunk/doc/report-mojo.html

JaCoCo. (2026). *jacoco:check*. https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html

JaCoCo. (2026). *CoverageTransformer.java*. https://www.jacoco.org/jacoco/trunk/coverage/org.jacoco.agent.rt/org.jacoco.agent.rt.internal/CoverageTransformer.java.html

JaCoCo. (2026). *ReportSupport.java*. https://www.jacoco.org/jacoco/trunk/coverage/jacoco-maven-plugin/org.jacoco.maven/ReportSupport.java.html

JaCoCo. (2026). *FileFilter.java*. https://www.jacoco.org/jacoco/trunk/coverage/jacoco-maven-plugin/org.jacoco.maven/FileFilter.java.html
