package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Maven coverage policy against an empty JaCoCo analysis set.
 *
 * <p>JaCoCo agent class filters use JVM class-name patterns, while report and check filters use
 * class-file paths. Reusing the agent's dotted, colon-delimited filter for report generation can
 * analyze zero classes and make a missed-count rule pass vacuously. This contract therefore
 * requires class-file path filters and an independent non-empty selection guard.</p>
 */
class DurableJobCoverageConfigurationTest {

    @Test
    void coverageReportUsesClassFilePathsAndRejectsAnEmptySelection() throws IOException {
        String pom = Files.readString(
                projectRoot().resolve("etl-service/pom.xml"),
                StandardCharsets.UTF_8
        );

        assertTrue(
                pom.contains("<include>com/xtrmetl/etl/job/**</include>"),
                "durable-job report/check filters must use class-file paths"
        );
        assertTrue(
                pom.contains("<include>com/xtrmetl/etl/controller/EtlJobController*</include>"),
                "the public job controller must remain inside the coverage gate"
        );
        assertFalse(
                pom.contains(
                        "com.xtrmetl.etl.job.*:com.xtrmetl.etl.controller.EtlJobController*"
                ),
                "a dotted agent filter must not be reused by the report/check analysis"
        );
        assertTrue(
                pom.contains("<resourcecount property=\"durable.job.coverage.class.count\">"),
                "the build must count selected production classes independently"
        );
        assertTrue(
                pom.contains(
                        "<equals arg1=\"${durable.job.coverage.class.count}\" arg2=\"0\"/>"
                ),
                "the build must fail when no durable-job class is selected"
        );
    }

    /**
     * Finds the multi-module reactor root from root or module-local Maven execution.
     *
     * @return repository root containing both the root POM and the ETL module
     */
    private static Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path fallback = null;
        while (current != null) {
            if (Files.exists(current.resolve("etl-service/pom.xml"))) {
                return current;
            }
            if (Files.exists(current.resolve("pom.xml"))) {
                fallback = current;
            }
            current = current.getParent();
        }
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException("Could not locate the mightyETL reactor root");
    }
}
