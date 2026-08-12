import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/**
 * Fail-closed preflight for the Maven Wrapper distribution trust binding.
 *
 * <p>This source-file program executes with the JDK before Maven Wrapper bootstrap. It verifies
 * that the repository still binds the reviewed Maven distribution URL to its reviewed SHA-256
 * checksum. Maven Wrapper then verifies the downloaded archive against the same checksum.</p>
 */
public final class VerifyMavenWrapperIntegrity {
    private static final Path WRAPPER_PROPERTIES =
            Path.of(".mvn", "wrapper", "maven-wrapper.properties");
    private static final String EXPECTED_WRAPPER_VERSION = "3.3.4";
    private static final String EXPECTED_DISTRIBUTION_TYPE = "only-script";
    private static final String EXPECTED_DISTRIBUTION_URL =
            "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/"
                    + "apache-maven-3.9.11-bin.zip";
    private static final String EXPECTED_DISTRIBUTION_SHA256 =
            "0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb";

    private VerifyMavenWrapperIntegrity() {
        // Utility class.
    }

    /**
     * Verifies the reviewed Maven Wrapper distribution binding and exits non-zero on drift.
     *
     * @param args ignored command-line arguments
     * @throws IOException when the wrapper properties cannot be read
     */
    public static void main(String[] args) throws IOException {
        if (!Files.isRegularFile(WRAPPER_PROPERTIES)) {
            throw new IllegalStateException("Maven Wrapper properties file is missing");
        }

        List<String> sourceLines = Files.readAllLines(WRAPPER_PROPERTIES, StandardCharsets.UTF_8);
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(WRAPPER_PROPERTIES, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        requireUniqueProperty(sourceLines, "wrapperVersion");
        requireUniqueProperty(sourceLines, "distributionType");
        requireUniqueProperty(sourceLines, "distributionUrl");
        requireUniqueProperty(sourceLines, "distributionSha256Sum");

        requireExact(properties, "wrapperVersion", EXPECTED_WRAPPER_VERSION);
        requireExact(properties, "distributionType", EXPECTED_DISTRIBUTION_TYPE);
        requireExact(properties, "distributionUrl", EXPECTED_DISTRIBUTION_URL);
        requireExact(properties, "distributionSha256Sum", EXPECTED_DISTRIBUTION_SHA256);

        System.out.println("Maven Wrapper integrity preflight passed.");
    }

    private static void requireUniqueProperty(List<String> sourceLines, String key) {
        long matches = sourceLines.stream()
                .map(String::trim)
                .filter(line -> line.startsWith(key + "="))
                .count();
        if (matches != 1) {
            throw new IllegalStateException(
                    "Expected exactly one canonical " + key + " property, found " + matches
            );
        }
    }

    private static void requireExact(Properties properties, String key, String expectedValue) {
        String actualValue = properties.getProperty(key);
        if (!expectedValue.equals(actualValue)) {
            throw new IllegalStateException(
                    "Maven Wrapper integrity drift for " + key + ": expected reviewed value"
            );
        }
    }
}
