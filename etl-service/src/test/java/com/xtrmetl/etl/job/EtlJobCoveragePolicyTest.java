package com.xtrmetl.etl.job;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the durable-job production slice bound to an executable 100% coverage policy.
 *
 * <p>The policy is intentionally scoped to the production classes introduced by the durable-job
 * intake slice. It requires current Java-compatible JaCoCo instrumentation, a non-empty selected
 * production bundle, and zero missed instructions, lines, methods, or branches while the ordinary
 * {@code mvn test} lifecycle runs.</p>
 */
class EtlJobCoveragePolicyTest {

    /**
     * Requires the ETL module build to fail when the durable-job production slice is empty or any
     * selected production path is untested.
     *
     * @throws IOException when the module build descriptor cannot be read
     */
    @Test
    void etlModuleEnforcesCompleteInstructionAndBranchCoverageForTheDurableJobSlice()
            throws IOException {
        String modulePom = read("etl-service/pom.xml");

        assertTrue(modulePom.contains("<artifactId>jacoco-maven-plugin</artifactId>"));
        assertTrue(modulePom.contains("<version>0.8.15</version>"));
        assertTrue(modulePom.contains("<phase>initialize</phase>"));
        assertTrue(modulePom.contains("<goal>prepare-agent</goal>"));
        assertTrue(modulePom.contains("<phase>test</phase>"));
        assertTrue(modulePom.contains("<goal>report</goal>"));
        assertTrue(modulePom.contains("<goal>check</goal>"));
        assertTrue(modulePom.contains("<include>com/xtrmetl/etl/job/*.class</include>"));
        assertTrue(modulePom.contains(
                "<include>com/xtrmetl/etl/controller/EtlJobController*.class</include>"
        ));
        assertTrue(modulePom.contains("<element>BUNDLE</element>"));
        assertTrue(modulePom.contains("<counter>CLASS</counter>"));
        assertTrue(modulePom.contains("<value>TOTALCOUNT</value>"));
        assertTrue(modulePom.contains("<minimum>1</minimum>"));
        assertTrue(modulePom.contains("<counter>INSTRUCTION</counter>"));
        assertTrue(modulePom.contains("<counter>LINE</counter>"));
        assertTrue(modulePom.contains("<counter>METHOD</counter>"));
        assertTrue(modulePom.contains("<counter>BRANCH</counter>"));
        assertTrue(modulePom.contains("<value>MISSEDCOUNT</value>"));
        assertTrue(modulePom.contains("<maximum>0</maximum>"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        Path lastPomParent = null;
        while (current != null) {
            if (Files.exists(current.resolve(".git"))) {
                return current;
            }
            if (Files.exists(current.resolve("pom.xml"))) {
                lastPomParent = current;
            }
            current = current.getParent();
        }
        if (lastPomParent != null) {
            return lastPomParent;
        }
        throw new IllegalStateException("Could not find project root");
    }
}
