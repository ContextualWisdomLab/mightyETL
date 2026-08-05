package com.xtrmetl.etl.documentation;

import com.fasterxml.jackson.databind.cfg.PackageVersion;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the patched Jackson bill of materials is both declared with effective Maven
 * precedence and actually resolved on the test runtime classpath.
 *
 * <p>A property in a project that imports Spring Boot's dependency BOM does not override the
 * imported BOM's internal property interpolation. The patched Jackson BOM therefore has to be
 * imported explicitly before Spring Boot's BOM. The runtime assertion catches configuration that
 * looks correct in source but still resolves a vulnerable Jackson Databind version.</p>
 */
class JacksonBomResolutionTest {

    private static final Pattern JACKSON_VERSION_PROPERTY = Pattern.compile(
            "<jackson-bom\\.version>([^<]+)</jackson-bom\\.version>"
    );

    /**
     * Requires explicit Jackson BOM precedence and exact runtime resolution to the configured
     * patched component line.
     *
     * @throws Exception when the root Maven model cannot be read as UTF-8 text
     */
    @Test
    void importsAndResolvesTheConfiguredPatchedJacksonBom() throws Exception {
        String rootPom = Files.readString(
                projectRoot().resolve("pom.xml"),
                StandardCharsets.UTF_8
        );
        Matcher versionMatcher = JACKSON_VERSION_PROPERTY.matcher(rootPom);
        assertTrue(versionMatcher.find(), "The root POM must declare jackson-bom.version");
        String configuredVersion = versionMatcher.group(1).trim();

        int jacksonBom = rootPom.indexOf("<artifactId>jackson-bom</artifactId>");
        int springBootBom = rootPom.indexOf(
                "<artifactId>spring-boot-dependencies</artifactId>"
        );
        assertTrue(jacksonBom >= 0, "The Jackson BOM must be imported explicitly");
        assertTrue(
                jacksonBom < springBootBom,
                "The Jackson BOM import must precede Spring Boot dependency management"
        );
        assertEquals(
                configuredVersion,
                PackageVersion.VERSION.toString(),
                "The resolved jackson-databind version must match jackson-bom.version"
        );
    }

    /**
     * Finds the repository root from either root or module-local Maven execution.
     *
     * @return absolute repository root containing the root Maven project
     * @throws IllegalStateException when no repository or Maven root can be found
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
