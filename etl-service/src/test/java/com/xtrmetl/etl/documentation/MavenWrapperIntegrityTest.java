package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prevents Maven Wrapper bootstrap from executing an unverified Maven distribution.
 *
 * <p>The wrapper downloads Maven before project compilation and tests can run. This repository
 * therefore treats the Maven distribution URL and its reviewed SHA-256 as one atomic build-input
 * contract. A Maven version or URL change must carry a newly reviewed checksum in the same change;
 * deleting the checksum must fail deterministically without network access.</p>
 */
class MavenWrapperIntegrityTest {

    private static final String REVIEWED_DISTRIBUTION_URL =
            "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/"
                    + "apache-maven-3.9.11-bin.zip";
    private static final String REVIEWED_DISTRIBUTION_SHA256 =
            "0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb";
    private static final String PREFLIGHT_COMMAND =
            "run: java .github/scripts/VerifyMavenWrapperIntegrity.java";

    /**
     * Requires the fixed Maven 3.9.11 download to remain bound to its reviewed SHA-256 checksum.
     *
     * @throws IOException when the wrapper properties cannot be read as repository source
     */
    @Test
    void bindsMavenDistributionUrlToReviewedSha256() throws IOException {
        Properties properties = new Properties();
        Path wrapperProperties = projectRoot().resolve(".mvn/wrapper/maven-wrapper.properties");
        assertTrue(Files.isRegularFile(wrapperProperties), "Maven Wrapper properties must exist");
        try (Reader reader = Files.newBufferedReader(wrapperProperties, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        assertEquals("3.3.4", properties.getProperty("wrapperVersion"));
        assertEquals("only-script", properties.getProperty("distributionType"));
        assertEquals(REVIEWED_DISTRIBUTION_URL, properties.getProperty("distributionUrl"));
        String distributionSha256 = properties.getProperty("distributionSha256Sum");
        assertNotNull(distributionSha256,
                "Maven Wrapper must verify the downloaded Maven distribution with SHA-256");
        assertTrue(distributionSha256.matches("[0-9a-f]{64}"));
        assertEquals(REVIEWED_DISTRIBUTION_SHA256, distributionSha256);
    }

    /**
     * Requires a fail-closed integrity preflight immediately before every CI/SBOM wrapper step.
     *
     * @throws IOException when a workflow cannot be read as repository source
     */
    @Test
    void preflightsEveryCiAndSbomWrapperInvocation() throws IOException {
        for (String workflow : List.of(".github/workflows/ci.yml", ".github/workflows/sbom.yml")) {
            List<String> lines = Files.readAllLines(projectRoot().resolve(workflow), StandardCharsets.UTF_8);
            int wrapperInvocations = 0;
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                String line = lines.get(lineIndex).trim();
                if (!line.startsWith("run:") || !containsWrapperInvocation(line)) {
                    continue;
                }
                wrapperInvocations++;
                int wrapperStep = previousStepStart(lines, lineIndex);
                int preflightStep = previousStepStart(lines, wrapperStep - 1);
                assertTrue(preflightStep >= 0,
                        workflow + ": wrapper invocation must have a preceding preflight step");
                String preflightBlock = String.join("\n", lines.subList(preflightStep, wrapperStep));
                assertTrue(preflightBlock.contains(PREFLIGHT_COMMAND),
                        workflow + ": every Maven Wrapper invocation must be immediately preceded by the integrity preflight");
                assertEquals(
                        stepCondition(lines, wrapperStep, lineIndex + 1),
                        stepCondition(lines, preflightStep, wrapperStep),
                        workflow + ": preflight and wrapper step must use the same condition"
                );
            }
            assertTrue(wrapperInvocations > 0,
                    workflow + ": expected at least one Maven Wrapper invocation");
        }
    }

    private static boolean containsWrapperInvocation(String line) {
        return line.contains("./mvnw ") || line.contains(".\\mvnw.cmd ");
    }

    private static int previousStepStart(List<String> lines, int fromIndex) {
        for (int lineIndex = fromIndex; lineIndex >= 0; lineIndex--) {
            if (lines.get(lineIndex).trim().startsWith("- name:")) {
                return lineIndex;
            }
        }
        return -1;
    }

    private static String stepCondition(List<String> lines, int startInclusive, int endExclusive) {
        for (int lineIndex = startInclusive; lineIndex < endExclusive; lineIndex++) {
            String line = lines.get(lineIndex).trim();
            if (line.startsWith("if:")) {
                return line;
            }
        }
        return "";
    }

    /** Finds the repository root from reactor-root or module-local Maven execution. */
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
