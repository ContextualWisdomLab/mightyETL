package com.xtrmetl.etl.documentation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prevents mutable container tags from silently changing the software supply-chain inputs.
 *
 * <p>Docker image tags are intentionally mutable. A build that names only a tag can therefore
 * consume different base-image bytes without any repository change. This contract keeps the tag
 * for human readability while requiring every external Dockerfile build stage to include an
 * immutable SHA-256 digest. References to an earlier local build stage are exempt because they do
 * not resolve through a registry.</p>
 */
class ContainerImagePinningTest {

    private static final Pattern FROM_INSTRUCTION = Pattern.compile(
            "(?im)^FROM\\s+(?:--platform=\\S+\\s+)?(?<image>\\S+)"
                    + "(?:\\s+AS\\s+(?<alias>[A-Za-z0-9._-]+))?\\s*$"
    );
    private static final Pattern SHA256_PIN = Pattern.compile(
            "^[^@\\s]+@sha256:[0-9a-f]{64}$"
    );

    /**
     * Requires every registry-backed Dockerfile build stage to resolve by immutable SHA-256 digest.
     *
     * @throws IOException when the repository Dockerfile cannot be read
     */
    @Test
    void pinsEveryExternalBaseImageBySha256Digest() throws IOException {
        Path dockerfilePath = projectRoot().resolve("Dockerfile");
        assertTrue(Files.isRegularFile(dockerfilePath), "The repository Dockerfile must exist");

        assertExternalImagesArePinned(
                Files.readString(dockerfilePath, StandardCharsets.UTF_8)
        );
    }

    /**
     * Requires Docker-compatible leading indentation to remain inside the immutable-image policy.
     *
     * <p>Docker accepts spaces or tabs before an instruction. A mutable external image must
     * therefore still fail this policy when its {@code FROM} instruction is indented.</p>
     */
    @Test
    void rejectsIndentedMutableExternalBaseImage() {
        String pinnedDigest = "a".repeat(64);
        String dockerfile = "FROM example.invalid/build@sha256:" + pinnedDigest + " AS build\n"
                + "  FROM alpine:latest\n";

        assertThrows(
                AssertionError.class,
                () -> assertExternalImagesArePinned(dockerfile),
                "Indented FROM instructions must not bypass immutable image enforcement"
        );
    }

    /**
     * Applies the immutable-image policy to Dockerfile source text.
     *
     * @param dockerfile Dockerfile text to validate
     */
    private static void assertExternalImagesArePinned(String dockerfile) {
        Matcher matcher = FROM_INSTRUCTION.matcher(dockerfile);
        Set<String> localStageAliases = new HashSet<>();
        int externalImageCount = 0;

        while (matcher.find()) {
            String imageReference = matcher.group("image");
            if (!localStageAliases.contains(imageReference)) {
                externalImageCount++;
                assertTrue(
                        SHA256_PIN.matcher(imageReference).matches(),
                        () -> "Dockerfile base image must use an immutable SHA-256 digest: "
                                + imageReference
                );
            }
            String stageAlias = matcher.group("alias");
            if (stageAlias != null) {
                localStageAliases.add(stageAlias);
            }
        }

        assertTrue(externalImageCount > 0, "The Dockerfile must declare at least one base image");
    }

    /**
     * Finds the repository root from either root-level or module-local Maven execution.
     *
     * @return absolute repository root containing the top-level Dockerfile and Maven project
     * @throws IllegalStateException when no repository root can be found
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