package com.xtrmetl.etl.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Guards the production Dockerfile against mutable external base-image references.
 *
 * <p>Human-readable tags communicate the intended Maven/JDK/JRE line, while a full SHA-256 image
 * digest binds the exact immutable multi-platform image index selected for an audited build. A tag
 * without a digest can move upstream without any mightyETL source change and therefore cannot be
 * treated as reproducible build input.</p>
 */
class RepositoryDockerBaseImagePolicyTest {

    private static final Pattern FROM_INSTRUCTION = Pattern.compile(
            "(?im)^\\s*FROM\\s+(?:--platform=\\S+\\s+)?(\\S+)(?:\\s+AS\\s+\\S+)?\\s*$"
    );
    private static final Pattern TAG_AND_SHA256 = Pattern.compile(
            "^[^@\\s]+:[^@\\s]+@sha256:[0-9a-f]{64}$"
    );

    @Test
    void everyExternalDockerBaseImageKeepsReadableTagAndPinsFullSha256Digest() throws IOException {
        Path repositoryRoot = findRepositoryRoot(Path.of("").toAbsolutePath().normalize());
        assertNotNull(repositoryRoot, "repository root containing Dockerfile and pom.xml must be discoverable");

        String dockerfile = Files.readString(repositoryRoot.resolve("Dockerfile"));
        List<String> imageReferences = externalBaseImageReferences(dockerfile);
        assertFalse(imageReferences.isEmpty(), "production Dockerfile must declare at least one external base image");

        for (String imageReference : imageReferences) {
            assertTrue(
                    TAG_AND_SHA256.matcher(imageReference).matches(),
                    () -> "external Docker base image must use readable tag plus full lowercase sha256 digest: "
                            + imageReference
            );
            assertFalse(
                    imageReference.toLowerCase(Locale.ROOT).contains(":latest@"),
                    () -> "external Docker base image must not use latest tag: " + imageReference
            );
        }
    }

    private static List<String> externalBaseImageReferences(String dockerfile) {
        List<String> imageReferences = new ArrayList<>();
        Matcher matcher = FROM_INSTRUCTION.matcher(dockerfile);
        while (matcher.find()) {
            imageReferences.add(matcher.group(1));
        }
        return imageReferences;
    }

    private static Path findRepositoryRoot(Path start) {
        Path candidate = start;
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("Dockerfile"))
                    && Files.isRegularFile(candidate.resolve("pom.xml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return null;
    }
}
