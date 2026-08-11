import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Fail-closed Maven Wrapper bootstrap preflight for GitHub Actions.
 *
 * <p>This source-file-mode utility runs on the JDK configured by the workflow before any
 * {@code mvnw} or {@code mvnw.cmd} invocation. It verifies that the Maven distribution remains
 * bound to the exact reviewed URL and SHA-256 checksum, so deleting or changing the checksum
 * cannot silently bypass integrity verification before Maven starts.</p>
 */
public final class VerifyMavenWrapperIntegrity {

    private static final String REVIEWED_WRAPPER_VERSION = "3.3.4";
    private static final String REVIEWED_DISTRIBUTION_TYPE = "only-script";
    private static final String REVIEWED_DISTRIBUTION_URL =
            "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/"
                    + "apache-maven-3.9.11-bin.zip";
    private static final String REVIEWED_DISTRIBUTION_SHA256 =
            "0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb";

    private VerifyMavenWrapperIntegrity() {
    }

    /**
     * Validates the reviewed Maven Wrapper bootstrap inputs before Maven can execute.
     *
     * @param args ignored command-line arguments
     * @throws IOException when the wrapper properties cannot be read
     */
    public static void main(String[] args) throws IOException {
        Path propertiesPath = Path.of(".mvn", "wrapper", "maven-wrapper.properties");
        if (!Files.isRegularFile(propertiesPath)) {
            throw new IllegalStateException("Missing Maven Wrapper properties: " + propertiesPath);
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(propertiesPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        requireExact(properties, "wrapperVersion", REVIEWED_WRAPPER_VERSION);
        requireExact(properties, "distributionType", REVIEWED_DISTRIBUTION_TYPE);
        requireExact(properties, "distributionUrl", REVIEWED_DISTRIBUTION_URL);
        requireExact(properties, "distributionSha256Sum", REVIEWED_DISTRIBUTION_SHA256);
        System.out.println("Maven Wrapper integrity preflight passed.");
    }

    private static void requireExact(Properties properties, String key, String expected) {
        String actual = properties.getProperty(key);
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "Maven Wrapper integrity preflight rejected " + key
                            + ": expected reviewed value but found " + String.valueOf(actual)
            );
        }
    }
}
