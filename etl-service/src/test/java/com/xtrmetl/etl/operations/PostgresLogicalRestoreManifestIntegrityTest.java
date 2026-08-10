package com.xtrmetl.etl.operations;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defines the independent manifest-integrity boundary for PostgreSQL restore rehearsals.
 */
class PostgresLogicalRestoreManifestIntegrityTest {

    @Test
    void restoreAuthenticatesManifestBeforeTrustingProvenanceFields() throws IOException {
        String script = Files.readString(
                projectRoot().resolve("scripts/ops/postgres-logical-restore-rehearsal.sh"),
                StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        String expectedManifestDigestInput =
                ": \"${EXPECTED_MANIFEST_SHA256:?Set EXPECTED_MANIFEST_SHA256 to the independently recorded backup manifest digest}\"";
        String expectedManifestDigestValidation =
                "if [[ ! \"$EXPECTED_MANIFEST_SHA256\" =~ ^[0-9a-f]{64}$ ]]";
        String actualManifestDigest = "actual_manifest_sha256=$(sha256_file \"$manifest_path\")";
        String manifestDigestMatch =
                "if [[ \"$actual_manifest_sha256\" != \"$EXPECTED_MANIFEST_SHA256\" ]]";
        String firstManifestFieldRead = "manifest_version=$(manifest_value manifest_version)";

        assertTrue(script.contains(expectedManifestDigestInput),
                "restore must require manifest integrity evidence supplied outside the mutable backup bundle");
        assertTrue(script.contains(expectedManifestDigestValidation),
                "restore must reject malformed out-of-band manifest digest evidence");
        assertTrue(script.contains(actualManifestDigest),
                "restore must hash the manifest before trusting any provenance field");
        assertTrue(script.contains(manifestDigestMatch),
                "restore must compare the manifest to independently supplied integrity evidence");
        assertTrue(script.indexOf(manifestDigestMatch) < script.indexOf(firstManifestFieldRead),
                "manifest integrity must be established before source/version/Flyway provenance is parsed");
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
