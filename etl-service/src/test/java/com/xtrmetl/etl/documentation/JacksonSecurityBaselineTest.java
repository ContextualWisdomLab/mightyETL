package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shared Maven dependency-management boundary against Jackson Databind versions that
 * remain inside the currently known vulnerable 2.21.x range.
 */
class JacksonSecurityBaselineTest {

    private static final Path PROJECT_ROOT = projectRoot();

    @Test
    void jacksonSecurityBomPrecedesImportedSpringBootDependencyManagement() throws IOException {
        String pom = Files.readString(PROJECT_ROOT.resolve("pom.xml"), StandardCharsets.UTF_8);

        assertTrue(
                pom.contains("<jackson-bom.version>2.21.5</jackson-bom.version>"),
                "Root dependency management must pin Jackson 2.21.5, the current patched 2.21 LTS baseline"
        );

        String jacksonBom = "<artifactId>jackson-bom</artifactId>";
        String springBootBom = "<artifactId>spring-boot-dependencies</artifactId>";
        int jacksonIndex = pom.indexOf(jacksonBom);
        int springBootIndex = pom.indexOf(springBootBom);

        assertTrue(jacksonIndex >= 0, "Root dependencyManagement must import the Jackson BOM explicitly");
        assertTrue(springBootIndex >= 0, "Root dependencyManagement must continue importing Spring Boot dependencies");
        assertTrue(
                jacksonIndex < springBootIndex,
                "Without the Spring Boot parent POM, the explicit Jackson override BOM must precede spring-boot-dependencies"
        );
    }

    /** Finds the repository root from root- or module-scoped Maven execution. */
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
