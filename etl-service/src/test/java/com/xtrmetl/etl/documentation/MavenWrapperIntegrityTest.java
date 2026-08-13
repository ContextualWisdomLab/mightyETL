package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * Requires the fixed Maven 3.9.11 download to remain bound to its reviewed SHA-256 checksum.
     *
     * @throws IOException when the wrapper properties cannot be read as repository source
     */
    @Test
    void bindsMavenDistributionUrlToReviewedSha256() throws IOException {
        Properties properties = new Properties();
        Path wrapperProperties = projectRoot().resolve(
                ".mvn/wrapper/maven-wrapper.properties"
        );
        assertTrue(Files.isRegularFile(wrapperProperties), "Maven Wrapper properties must exist");

        try (Reader reader = Files.newBufferedReader(wrapperProperties, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        assertEquals("3.3.4", properties.getProperty("wrapperVersion"));
        assertEquals("only-script", properties.getProperty("distributionType"));
        assertEquals(
                REVIEWED_DISTRIBUTION_URL,
                properties.getProperty("distributionUrl"),
                "Changing the Maven distribution requires review of a matching checksum"
        );

        String distributionSha256 = properties.getProperty("distributionSha256Sum");
        assertNotNull(
                distributionSha256,
                "Maven Wrapper must verify the downloaded Maven distribution with SHA-256"
        );
        assertTrue(
                distributionSha256.matches("[0-9a-f]{64}"),
                "distributionSha256Sum must be 64 lowercase hexadecimal characters"
        );
        assertEquals(
                REVIEWED_DISTRIBUTION_SHA256,
                distributionSha256,
                "The checksum must match the reviewed Maven 3.9.11 distribution"
        );
    }

    /**
     * Finds the repository root from reactor-root or module-local Maven execution.
     *
     * @return absolute repository root containing the wrapper configuration
     */
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
